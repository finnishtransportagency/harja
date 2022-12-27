(ns harja.tiedot.vesivaylat.hallinta.urakoiden-luonti
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :as async]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [tuck.core :as tuck]
            [cljs.pprint :refer [pprint]]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.domain.urakka :as u]
            [harja.domain.organisaatio :as o]
            [harja.domain.sopimus :as s]
            [harja.domain.hanke :as h]
            [harja.ui.viesti :as viesti]
            [namespacefy.core :refer [namespacefy]]
            [harja.tyokalut.local-storage :refer [local-storage-atom]]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tyhja-sopimus {::s/nimi nil ::s/alku nil ::s/loppu nil ::s/paasopimus-id nil ::s/id nil})
(def uusi-urakka {::u/sopimukset [tyhja-sopimus]})

(defonce tila
  (atom {:nakymassa? false
         :valittu-urakka nil
         :tallennus-kaynnissa? false
         :urakoiden-haku-kaynnissa? false
         :haetut-urakat nil
         :haetut-hallintayksikot nil
         :haetut-urakoitsijat nil
         :haetut-hankkeet nil
         :haetut-sopimukset nil
         :kaynnissa-olevat-sahkelahetykset #{}}))

(defn sopimukset-paasopimuksella [sopimukset paasopimus]
  (->>
    sopimukset
    ;; Asetetaan sopimuksille tieto siitä, mikä sopimus on niiden pääsopimus
    ;; Kuitenkin itse pääsopimukselle asetetaan paasopimus-id:ksi nil
    (map #(assoc % ::s/paasopimus-id (when (not= (::s/id %) (::s/id paasopimus))
                                       (::s/id paasopimus))))))

(defn vapaa-sopimus? [s] (nil? (get-in s [::s/urakka ::u/id])))

(defn sopiva-sopimus-urakalle? [u s]
  (or (vapaa-sopimus? s)
      (= (::u/id u) (get-in s [::s/urakka ::u/id]))))

(defn vapaat-sopimukset [urakka sopimukset urakan-sopimukset]
  (->> sopimukset
       (filter (partial sopiva-sopimus-urakalle? urakka))
       (remove (comp (into #{} (keep ::s/id urakan-sopimukset)) ::s/id))))

(defn uusin-tieto [hanke sopimukset urakoitsija urakka]
  (sort-by #(or (:muokattu %) (:luotu %))
           pvm/jalkeen?
           (conj sopimukset hanke urakoitsija urakka)))

(defn- lahetykset-uusimmasta-vanhimpaan [lahetykset]
  (sort-by :lahetetty pvm/jalkeen? lahetykset))

(defn uusin-lahetys [lahetykset]
  (first (lahetykset-uusimmasta-vanhimpaan lahetykset)))

(defn uusin-onnistunut-lahetys [lahetykset]
  (first (filter :onnistui (lahetykset-uusimmasta-vanhimpaan lahetykset))))


(defrecord ValitseUrakka [urakka])
(defrecord Nakymassa? [nakymassa?])
(defrecord UusiUrakka [])
(defrecord TallennaUrakka [urakka])
(defrecord UrakkaTallennettu [urakka])
(defrecord UrakkaEiTallennettu [virhe])
(defrecord UrakkaaMuokattu [urakka])
(defrecord HaeUrakat [])
(defrecord UrakatHaettu [urakat])
(defrecord UrakatEiHaettu [virhe])
(defrecord PaivitaSopimuksetGrid [sopimukset])
(defrecord HaeLomakevaihtoehdot [])
(defrecord LomakevaihtoehdotHaettu [tulos])
(defrecord LomakevaihtoehdotEiHaettu [virhe])

(extend-protocol tuck/Event
  ValitseUrakka
  (process-event [{urakka :urakka} app]
    (assoc app :valittu-urakka urakka))

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  UusiUrakka
  (process-event [_ app]
    (assoc app :valittu-urakka uusi-urakka))

  TallennaUrakka
  (process-event [{urakka :urakka} app]
    (assert (some? (:haetut-urakat app)) "Urakkaa ei voi yrittää tallentaa, ennen kuin urakoiden haku on valmis.")
    (let [tulos! (tuck/send-async! ->UrakkaTallennettu)
          fail! (tuck/send-async! ->UrakkaEiTallennettu)]
      (go
        (try
          (let [vastaus (async/<! (k/post! :tallenna-vesivaylaurakka
                                           (update urakka
                                                   ::u/sopimukset
                                                   #(->> %
                                                         ;; grid antaa uusille riveille negatiivisen id:n,
                                                         ;; mutta riville annetaan "oikea id", kun sopimus valitaan.
                                                         ;; Rivillä on neg. id vain, jos sopimus jäi valitsematta.
                                                         (filter (comp id-olemassa? ::s/id))))))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :tallennus-kaynnissa? true))

  UrakkaTallennettu
  (process-event [{urakka :urakka} app]
    (viesti/nayta! "Urakka tallennettu!")
    (let [vanhat (group-by ::u/id (:haetut-urakat app))
          uusi {(::u/id urakka) [urakka]}]
      ;; Yhdistetään tallennettu jo haettuihin.
      ;; Gridiin tultaessa Grid hakee vielä taustalla kaikki hankkeet
      ;; Tietokannasta asiat tulevat järjestettynä, mutta yritetään tässä jo saada oikea järjestys aikaan
      (assoc app :haetut-urakat
                 (sort-by ::u/alkupvm pvm/jalkeen?
                          (vec (apply concat
                                      (vals (merge vanhat uusi)))))
                 :tallennus-kaynnissa? false
                 :valittu-urakka nil)))

  UrakkaEiTallennettu
  (process-event [{virhe :virhe} app]
    (viesti/nayta! [:span "Virhe tallennuksessa! Urakkaa ei tallennettu."] :danger)
    (assoc app :tallennus-kaynnissa? false
               :valittu-urakka nil))

  UrakkaaMuokattu
  (process-event [{urakka :urakka} app]
    (assoc app :valittu-urakka urakka))

  HaeUrakat
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->UrakatHaettu)
          fail! (tuck/send-async! ->UrakatEiHaettu)]
      (go
        (try
          (let [vastaus (async/<! (k/post! :hae-harjassa-luodut-urakat {}))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :urakoiden-haku-kaynnissa? true))

  UrakatHaettu
  (process-event [{urakat :urakat} app]
    (assoc app :haetut-urakat urakat
               :urakoiden-haku-kaynnissa? false))

  UrakatEiHaettu
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe urakoiden haussa!"] :danger)
    (assoc app :urakoiden-haku-kaynnissa? false))

  PaivitaSopimuksetGrid
  (process-event [{sopimukset :sopimukset} {urakka :valittu-urakka :as app}]
    (let [urakan-sopimukset-ilman-poistettuja (remove
                                                (comp
                                                  (into #{} (map ::s/id (filter :poistettu sopimukset)))
                                                  ::s/id)
                                                (::u/sopimukset urakka))
          paasopimus (s/ainoa-paasopimus urakan-sopimukset-ilman-poistettuja)
          ;; Jos pääsopimukseksi merkitty pääsopimus ei enää ole gridissä,
          ;; poista pääsopimusmerkintä - yhtäkään sopimusta ei merkitä pääsopimukseksi.
          paasopimus-id (when ((into #{} (map ::s/id sopimukset)) (::s/id paasopimus))
                          (::s/id paasopimus))]
      (->> sopimukset
           ;; Asetetaan sopimukset viittaamaan pääsopimukseen
           (map #(assoc % ::s/paasopimus-id paasopimus-id))
           ;; Jos sopimus on pääsopimus, :paasopimus-id asetetaan nilliksi
           (map #(update % ::s/paasopimus-id (fn [ps] (when-not (= ps (::s/id %)) ps))))
           (assoc-in app [:valittu-urakka ::u/sopimukset]))))

  HaeLomakevaihtoehdot
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->LomakevaihtoehdotHaettu)
          fail! (tuck/send-async! ->LomakevaihtoehdotEiHaettu)]
      (go
        (try
          (let [hallintayksikot (k/post! :hallintayksikot {:liikennemuoto :vesi})
                hankkeet (k/post! :hae-harjassa-luodut-hankkeet {})
                urakoitsijat (k/post! :hae-urakoitsijat-urakkatietoineen {})
                sopimukset (k/post! :hae-harjassa-luodut-sopimukset {})
                vastaus {:hallintayksikot (async/<! hallintayksikot)
                         :hankkeet (async/<! hankkeet)
                         :urakoitsijat (async/<! urakoitsijat)
                         :sopimukset (async/<! sopimukset)}]
            (if (some k/virhe? (vals vastaus))
              (fail! vastaus)
              (tulos! (assoc vastaus :hallintayksikot
                                     (namespacefy
                                       (:hallintayksikot vastaus)
                                       {:ns :harja.domain.organisaatio})))))
          (catch :default e
            (fail! nil)
            (throw e)))))
    app)

  LomakevaihtoehdotHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :haetut-hallintayksikot (remove
                                         (comp (partial = "Kanavat ja avattavat sillat") ::o/nimi)
                                         (sort-by ::o/nimi (:hallintayksikot tulos)))
               :haetut-urakoitsijat (sort-by ::o/nimi (:urakoitsijat tulos))
               :haetut-hankkeet (sort-by ::h/nimi (remove #(pvm/jalkeen? (pvm/nyt) (::h/loppupvm %)) (:hankkeet tulos)))
               :haetut-sopimukset (sort-by ::s/alkupvm pvm/jalkeen? (:sopimukset tulos))))

  LomakevaihtoehdotEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Hupsista, ongelmia Harjan kanssa juttelussa." :danger)
    app))

