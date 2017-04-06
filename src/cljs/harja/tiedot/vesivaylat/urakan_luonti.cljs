(ns harja.tiedot.vesivaylat.urakan-luonti
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :as async]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [tuck.core :as tuck]
            [cljs.pprint :refer [pprint]]
            [harja.tyokalut.functor :refer [fmap]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.local-storage :refer [local-storage-atom]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def uusi-urakka {})

(defonce tila
  (atom {:nakymassa? false
         :valittu-urakka nil
         :tallennus-kaynnissa? false
         :urakoiden-haku-kaynnissa? false
         :haetut-urakat nil}))

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
          (let [vastaus urakka]
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
    (assoc app :urakoiden-haku-kaynnnissa? true))

  UrakatHaettu
  (process-event [{urakat :urakat} app]
    (assoc app :haetut-urakat urakat
               :urakoiden-haku-kaynnissa? false))

  UrakatEiHaettu
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe urakoiden haussa!"] :danger)
    (assoc app :urakoiden-haku-kaynnissa? false)))

