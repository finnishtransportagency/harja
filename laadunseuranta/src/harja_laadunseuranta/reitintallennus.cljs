(ns harja-laadunseuranta.reitintallennus
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.ratom :as ratom]
            [harja-laadunseuranta.indexeddb :as idb]
            [harja-laadunseuranta.asetukset :as asetukset]
            [harja-laadunseuranta.projektiot :as projektiot]
            [harja-laadunseuranta.utils :as utils :refer [erota-mittaukset erota-havainnot]]
            [cljs.core.async :as async :refer [<! chan put! close!]]
            [cljs.core.match]
            [cljs-time.coerce :as tc]
            [cljs-time.local :as lt]
            [harja-laadunseuranta.virhekasittely :as virhekasittely])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [run!]]
                   [harja-laadunseuranta.macros :refer [with-delay-loop after-delay]]
                   [harja-laadunseuranta.indexeddb-macros :refer [with-transaction
                                                                  with-transaction-to-store
                                                                  with-get-object
                                                                  with-all-items
                                                                  with-n-items
                                                                  with-objectstore
                                                                  with-cursor
                                                                  with-count]]))

(def db-spec {:version 2
              :on-error #(virhekasittely/ilmoita-virhe (str "Tietokantavirhe " (pr-str %)))
              :objectstores [{:name asetukset/+tapahtumastore+
                              :key-path :id
                              :auto-increment true}
                             {:name asetukset/+tarkastusajostore+
                              :key-path :tarkastusajo
                              :auto-increment false}]})

(defn muodosta-kertakirjausviesti [{:keys [aikaleima havainnot mittaukset sijainti kuvaus kuva pikavalinta
                                           laadunalitus?]} tarkastusajo]
  {:sijainti (select-keys sijainti [:lat :lon])
   :aikaleima (tc/to-long aikaleima)
   :tarkastusajo tarkastusajo
   :mittaukset mittaukset
   :havainnot (if pikavalinta (conj havainnot pikavalinta) havainnot)
   :kuvaus kuvaus
   :laadunalitus (true? laadunalitus?)
   :kuva kuva})

(defn- poista-lahetetyt-tapahtumat!
  "Poistaa kaikki lähetetyt tapahtumaid:t IndexedDB:stä"
  [db lahetetyt-idt]
  (let [c (chan)]
    (with-transaction-to-store db asetukset/+tapahtumastore+ :readwrite store
      (doseq [tapahtumaid lahetetyt-idt]
        (idb/delete-object store tapahtumaid))

      :on-complete (close! c))
    c))

(defn kaynnista-reitinlahetys
  "Lukee lähetettävät tapahtumat IndexedDB:stä ja poistaa onnistuneesti lähetetyt"
  [pollausvali db tapahtumien-lahetin]
  (let [again (chan)]
    (go-loop [cont true]
      (when cont
        (with-transaction-to-store db asetukset/+tapahtumastore+ :readonly store
          (with-n-items store 10 tapahtumat
            (go
              (let [lahetetyt (<! (tapahtumien-lahetin tapahtumat))]
                (if-not (empty? lahetetyt)
                  (do
                    (<! (poista-lahetetyt-tapahtumat! db lahetetyt))
                    (after-delay pollausvali (put! again true)))
                  (after-delay pollausvali (put! again true)))))))
        (recur (<! again))))
    again))

(defn- lisaa-piirrettava-reittipiste
  "Lisää reittipiste jos sama piste ei ole jo lisätty piirrettävien loppuun"
  [reittipisteet sijainti]
  (if (nil? sijainti)
    reittipisteet
    (if (not= sijainti (last reittipisteet))
      (conj reittipisteet sijainti)
      reittipisteet)))

(defn paivita-lahettamattomien-maara [db pollausvali lahettamattomat-atom]
  (with-delay-loop pollausvali
    (with-transaction-to-store db asetukset/+tapahtumastore+ :readonly store
      (with-count store lahettamattomia
        (reset! lahettamattomat-atom lahettamattomia)))))

(defn palauta-tarkastusajo [db action]
  (with-transaction-to-store db asetukset/+tarkastusajostore+ :readonly store
    (with-cursor store kursori ajo
      (action ajo)
      (idb/cursor-continue kursori))))

(defn persistoi-tarkastusajo [db tarkastusajo-id tarkastustyyppi]
  (with-transaction-to-store db asetukset/+tarkastusajostore+ :readwrite store
    (with-get-object store tarkastusajo-id ajo
      (when (nil? ajo)
        (idb/put-object store {:tarkastusajo tarkastusajo-id
                               :tarkastustyyppi tarkastustyyppi
                               :reittipisteet []})))))

(defn tyhjenna-reittipisteet [db]
  (with-transaction-to-store db asetukset/+tapahtumastore+ :readwrite store
    (.clear store)))

(defn tallenna-tarkastusajon-geometria [db tarkastusajo-id reittipisteet tarkastuspisteet]
  (with-transaction-to-store db asetukset/+tarkastusajostore+ :readwrite store
    (with-get-object store tarkastusajo-id ajo
      (when ajo
        (idb/put-object store (assoc (js->clj ajo)
                                     ;; tallenna vain 500 viimeistä reittipistettä
                                     :reittipisteet (clj->js (vec (take-last asetukset/+persistoitavien-max-maara+ reittipisteet)))
                                     :tarkastuspisteet (clj->js (vec (take-last asetukset/+persistoitavien-max-maara+ tarkastuspisteet)))))))))

(defn poista-tarkastusajo [db tarkastusajo-id]
  (with-transaction-to-store db asetukset/+tarkastusajostore+ :readwrite store
    (idb/delete-object store tarkastusajo-id)))

(defn kirjaa-kertakirjaus [db kirjaus tarkastusajo]
  (with-transaction-to-store db asetukset/+tapahtumastore+ :readwrite store
    (idb/add-object store (muodosta-kertakirjausviesti kirjaus tarkastusajo))))

(defn- kaynnista-tarkastusajon-lokaali-tallennus [db tarkastusajo-atom tarkastustyyppi-atom]
  (let [ajo-id (cljs.core/atom nil)]
    (run!
     (if (and @ajo-id (not @tarkastusajo-atom))
       (do (poista-tarkastusajo db @ajo-id)
           (reset! ajo-id nil))
       (when (and (not @ajo-id) @tarkastusajo-atom @tarkastustyyppi-atom)
         (persistoi-tarkastusajo db @tarkastusajo-atom @tarkastustyyppi-atom)
         (reset! ajo-id @tarkastusajo-atom))))))

(defn kaynnista-reitintallennus [sijainnin-tallennus-mahdollinen-atom
                                 sijainti-atomi
                                 db
                                 segmentti-atomi
                                 reittipisteet-atomi
                                 tallennus-kaynnissa-atomi
                                 havainnot-atom
                                 tarkastustyyppi-atom
                                 tarkastusajo-atom
                                 tarkastuspisteet-atom]
  (.log js/console "Reitintallennus käynnistetty")
  (kaynnista-tarkastusajon-lokaali-tallennus db tarkastusajo-atom tarkastustyyppi-atom)
  
  (run!
   (when (and @sijainnin-tallennus-mahdollinen-atom @tarkastusajo-atom)
     (tallenna-tarkastusajon-geometria db @tarkastusajo-atom @reittipisteet-atomi @tarkastuspisteet-atom)))

  (run!
   (when (and @tallennus-kaynnissa-atomi
              @segmentti-atomi)
    (swap! reittipisteet-atomi #(lisaa-piirrettava-reittipiste % @segmentti-atomi))))

  (run!
   (when (and @sijainnin-tallennus-mahdollinen-atom
              @tallennus-kaynnissa-atomi
              (:nykyinen @sijainti-atomi))
     (kirjaa-kertakirjaus db
                          {:havainnot (erota-havainnot @havainnot-atom)
                           :mittaukset (erota-mittaukset @havainnot-atom)
                           :aikaleima (lt/local-now)
                           :sijainti (:nykyinen @sijainti-atomi)}
                          @tarkastusajo-atom))))

(defn tietokannan-alustus []
  (idb/create-indexed-db "harja2" db-spec))
