(ns harja.tiedot.vesivaylat.urakoiden-luonti
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :as async]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [tuck.core :as tuck]
            [cljs.pprint :refer [pprint]]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.local-storage :refer [local-storage-atom]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tyhja-sopimus {:nimi nil :alku nil :loppu nil :paasopimus nil :id nil})
(def uusi-urakka {:sopimukset [tyhja-sopimus]})

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

(defn paasopimus [sopimukset]
  (first (filter (comp some? #{(some :paasopimus sopimukset)} :id) sopimukset)))

(defn aseta-paasopimus [sopimukset sopimus]
  (map
    #(assoc % :paasopimus (if (= (:id %) (:id sopimus)) nil (:id sopimus)))
    sopimukset))

(defn paasopimus? [sopimukset sopimus]
  (boolean (when-let [ps (paasopimus sopimukset)] (= (:id sopimus) (:id ps)))))

(defn vapaa-sopimus? [s] (nil? (get-in s [:urakka :id])))

(defn vapaat-sopimukset [sopimukset urakan-sopimukset]
  (remove (comp (into #{} (keep :id urakan-sopimukset)) :id) (filter vapaa-sopimus? sopimukset)))

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

(defn urakan-sahke-tila [{:keys [hanke sopimukset urakoitsija sahkelahetykset] :as urakka}]
  (let [uusin-tieto (uusin-tieto hanke sopimukset urakoitsija urakka)
        uusin-lahetys (uusin-lahetys sahkelahetykset)
        uusin-onnistunut (uusin-onnistunut-lahetys sahkelahetykset)]
    (cond
      (nil? uusin-lahetys)
      :lahettamatta

      (nil? uusin-onnistunut)
      :epaonnistunut

      (pvm/jalkeen? uusin-onnistunut uusin-tieto)
      :lahetetty

      (and
        (not (:onnistui uusin-lahetys))
        (pvm/jalkeen? uusin-lahetys uusin-tieto))
      :epaonnistunut

      (pvm/jalkeen? uusin-tieto uusin-onnistunut)
      :lahettamatta

      :else :lahetetty)))

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
(defrecord LahetaUrakkaSahkeeseen [urakka])
(defrecord SahkeeseenLahetetty [tulos urakka])
(defrecord SahkeeseenEiLahetetty [virhe urakka])

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
          (let [vastaus (async/<! (k/post! :tallenna-urakka
                                           (update urakka
                                                   :sopimukset
                                                   #(->> %
                                                         ;; grid antaa uusille riveille negatiivisen id:n,
                                                         ;; mutta riville annetaan "oikea id", kun sopimus valitaan.
                                                         ;; Rivillä on neg. id vain, jos sopimus jäi valitsematta.
                                                         (remove (comp neg? :id))))))]
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
    (let [vanhat (group-by :id (:haetut-urakat app))
          uusi {(:id urakka) [urakka]}]
      (assoc app :haetut-urakat (vec (apply concat (vals (merge vanhat uusi))))
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
  (process-event [{sopimukset :sopimukset} app]
    (assoc-in app [:valittu-urakka :sopimukset] sopimukset))

  HaeLomakevaihtoehdot
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->LomakevaihtoehdotHaettu)
          fail! (tuck/send-async! ->LomakevaihtoehdotEiHaettu)]
      (go
        (try
          (let [hallintayksikot (async/<! (k/post! :hallintayksikot :vesi))
                hankkeet (async/<! (k/post! :hae-harjassa-luodut-hankkeet {}))
                urakoitsijat (async/<! (k/post! :vesivayla-urakoitsijat {}))
                sopimukset (async/<! (k/post! :hae-harjassa-luodut-sopimukset {}))
                vastaus {:hallintayksikot hallintayksikot
                         :hankkeet hankkeet
                         :urakoitsijat urakoitsijat
                         :sopimukset sopimukset}]
            (if (some k/virhe? (vals vastaus))
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    app)

  LomakevaihtoehdotHaettu
  (process-event [{tulos :tulos} app]
    (assoc app :haetut-hallintayksikot (:hallintayksikot tulos)
               :haetut-urakoitsijat (:urakoitsijat tulos)
               :haetut-hankkeet (remove #(pvm/jalkeen? (pvm/nyt) (:loppupvm %)) (:hankkeet tulos))
               :haetut-sopimukset (:sopimukset tulos)))

  LomakevaihtoehdotEiHaettu
  (process-event [_ app]
    (viesti/nayta! [:span "Hupsista, ongelmia Harjan kanssa juttelussa."] :danger)
    app)

  LahetaUrakkaSahkeeseen
  (process-event [{urakka :urakka} app]
    (let [tulos! (tuck/send-async! ->SahkeeseenLahetetty urakka)
          fail! (tuck/send-async! ->SahkeeseenEiLahetetty urakka)]
      (go
        (try
          (let [vastaus (<! (k/post! :laheta-urakka-sahkeeseen (:id urakka)))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (update app :kaynnissa-olevat-sahkelahetykset conj (:id urakka)))

  SahkeeseenLahetetty
  (process-event [{tulos :tulos urakka :urakka} app]
    (update app :kaynnissa-olevat-sahkelahetykset disj (:id urakka)))

  SahkeeseenEiLahetetty
  (process-event [{virhe :virhe urakka :urakka} app]
    (viesti/nayta! [:span "Urakan '" (:nimi urakka) "' lähetys epäonnistui."] :danger)
    (update app :kaynnissa-olevat-sahkelahetykset disj (:id urakka))))

