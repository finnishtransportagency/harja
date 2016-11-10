(ns harja-laadunseuranta.tiedot.reitintallennus
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.ratom :as ratom]
            [harja-laadunseuranta.tiedot.indexeddb :as idb]
            [harja-laadunseuranta.tiedot.asetukset.asetukset :as asetukset]
            [harja-laadunseuranta.tiedot.projektiot :as projektiot]
            [cljs.core.async :as async :refer [<! chan put! close!]]
            [cljs.core.match]
            [cljs-time.coerce :as tc]
            [cljs-time.local :as lt]
            [harja-laadunseuranta.tiedot.sovellus :as s])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [run!]]
                   [harja-laadunseuranta.macros :refer [with-delay-loop after-delay]]
                   [harja-laadunseuranta.tiedot.indexeddb-macros :refer [with-transaction
                                                                  with-transaction-to-store
                                                                  with-get-object
                                                                  with-all-items
                                                                  with-n-items
                                                                  with-objectstore
                                                                  with-cursor
                                                                  with-count]]))

(def db-spec {:version 2
              :on-error #(js/console.log (str "Tietokantavirhe " (pr-str %)))
              :objectstores [{:name asetukset/+reittimerkinta-store+
                              :key-path :id
                              :auto-increment true}
                             {:name asetukset/+tarkastusajo-store+
                              :key-path :tarkastusajo
                              :auto-increment false}]})

(defn- poista-lahetetyt-reittimerkinnat!
  "Poistaa kaikki lähetetyt tapahtumaid:t IndexedDB:stä"
  [db lahetetyt-idt]
  (let [c (chan)]
    (with-transaction-to-store db asetukset/+reittimerkinta-store+ :readwrite store
                               (doseq [tapahtumaid lahetetyt-idt]
        (idb/delete-object store tapahtumaid))

                               :on-complete (close! c))
    c))

(defn kaynnista-reitinlahetys
  "Lukee lähetettävät reittimerkinnät IndexedDB:stä ja poistaa onnistuneesti lähetetyt"
  [pollausvali db merkintojen-lahetin]
  (let [again (chan)]
    (go-loop [cont true]
      (when cont
        (with-transaction-to-store db asetukset/+reittimerkinta-store+ :readonly store
                                   (with-n-items store 10 reittimerkinnat
            (go
              (let [lahetetyt (<! (merkintojen-lahetin reittimerkinnat))]
                (if-not (empty? lahetetyt)
                  (do
                    (<! (poista-lahetetyt-reittimerkinnat! db lahetetyt))
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

(defn paivita-lahettamattomien-merkintojen-maara [db pollausvali lahettamattomat-atom]
  (with-delay-loop pollausvali
    (with-transaction-to-store db asetukset/+reittimerkinta-store+ :readonly store
                               (with-count store lahettamattomia
        (reset! lahettamattomat-atom lahettamattomia)))))

(defn palauta-tarkastusajo [db action]
  (with-transaction-to-store db asetukset/+tarkastusajo-store+ :readonly store
                             (with-cursor store kursori ajo
      (action ajo)
      (idb/cursor-continue kursori))))

(defn persistoi-tarkastusajo [db tarkastusajo-id]
  (with-transaction-to-store db asetukset/+tarkastusajo-store+ :readwrite store
                             (with-get-object store tarkastusajo-id ajo
      (when (nil? ajo)
        (idb/put-object store {:tarkastusajo tarkastusajo-id
                               :reittipisteet []})))))

(defn tyhjenna-reittipisteet [db]
  (with-transaction-to-store db asetukset/+reittimerkinta-store+ :readwrite store
                             (.clear store)))

(defn tallenna-tarkastusajon-geometria [db tarkastusajo-id reittipisteet tarkastuspisteet]
  (with-transaction-to-store db asetukset/+tarkastusajo-store+ :readwrite store
                             (with-get-object store tarkastusajo-id ajo
      (when ajo
        (idb/put-object store (assoc (js->clj ajo)
                                     ;; tallenna vain 500 viimeistä reittipistettä
                                     :reittipisteet (clj->js (vec (take-last asetukset/+persistoitavien-max-maara+ reittipisteet)))
                                     :tarkastuspisteet (clj->js (vec (take-last asetukset/+persistoitavien-max-maara+ tarkastuspisteet)))))))))

(defn poista-tarkastusajo [db tarkastusajo-id]
  (with-transaction-to-store db asetukset/+tarkastusajo-store+ :readwrite store
                             (idb/delete-object store tarkastusajo-id)))

(defn- kirjaa-kertakirjaus [db kirjaus]
  (with-transaction-to-store db asetukset/+reittimerkinta-store+ :readwrite store
                             (idb/add-object store kirjaus)))

(defn tallenna-sovelluksen-tilasta-merkinta-indexeddbn!
  "Lukee sovelluksen tilan ja muodostaa siitä reittimerkinnän IndexedDB:n.
   Tätä on tarkoitus kutsua aina kun tila muuttuu oleellisesti (esim. sijainti tai havainnot vaihtuu)"
  []
  (.log js/console "Havainnot kantaan: " (pr-str (into #{} (remove nil?
                                                                   (conj @s/jatkuvat-havainnot
                                                                         @s/pistemainen-havainto)))))
  (kirjaa-kertakirjaus @s/idxdb
                       {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
                        :aikaleima (tc/to-long (lt/local-now))
                        :tarkastusajo @s/tarkastusajo-id
                        :havainnot (into #{} (remove nil?
                                                     (conj @s/jatkuvat-havainnot
                                                           @s/pistemainen-havainto)))
                        :mittaukset {:lumisuus @s/talvihoito-lumimaara
                                     :talvihoito-tasaisuus @s/talvihoito-tasaisuus
                                     :kitkamittaus @s/talvihoito-kitkamittaus
                                     :soratie-tasaisuus @s/soratie-tasaisuus
                                     :polyavyys @s/soratie-polyavyys
                                     :kiinteys @s/soratie-kiinteys}
                        ;; TODO Nämä tulee kai lomakkeelta? Pitää selvittää, miten toimii.
                        ;:kuvaus kuvaus
                        ;:laadunalitus (true? laadunalitus?)
                        ;:kuva kuva
                        }))

(defn- kaynnista-tarkastusajon-lokaali-tallennus [db tarkastusajo-atom]
  (let [ajo-id (cljs.core/atom nil)]
    (run!
     (if (and @ajo-id (not @tarkastusajo-atom))
       (do (poista-tarkastusajo db @ajo-id)
           (reset! ajo-id nil))
       (when (and (not @ajo-id) @tarkastusajo-atom)
         (persistoi-tarkastusajo db @tarkastusajo-atom)
         (reset! ajo-id @tarkastusajo-atom))))))

(defn kaynnista-reitintallennus [sijainnin-tallennus-mahdollinen-atom
                                 sijainti-atom
                                 db
                                 segmentti-atom
                                 reittipisteet-atom
                                 tallennus-kaynnissa-atom
                                 tarkastusajo-atom
                                 tarkastuspisteet-atom]
  (.log js/console "Reitintallennus käynnistetty")
  (kaynnista-tarkastusajon-lokaali-tallennus db tarkastusajo-atom)
  
  (run!
   (when (and @sijainnin-tallennus-mahdollinen-atom @tarkastusajo-atom)
     (tallenna-tarkastusajon-geometria db @tarkastusajo-atom @reittipisteet-atom @tarkastuspisteet-atom)))

  (run!
   (when (and @tallennus-kaynnissa-atom
              @segmentti-atom)
    (swap! reittipisteet-atom #(lisaa-piirrettava-reittipiste % @segmentti-atom))))

  (run!
    (when (and @sijainnin-tallennus-mahdollinen-atom
              @tallennus-kaynnissa-atom
              (:nykyinen @sijainti-atom))
      (tallenna-sovelluksen-tilasta-merkinta-indexeddbn!))))

(defn tietokannan-alustus []
  (idb/create-indexed-db "harja2" db-spec))
