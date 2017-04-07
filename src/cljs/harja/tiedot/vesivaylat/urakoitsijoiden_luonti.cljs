(ns harja.tiedot.vesivaylat.urakoitsijoiden-luonti
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [cljs.core.async :as async])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def uusi-urakoitsija {})

(defonce tila
  (atom {:nakymassa? false
         :valittu-urakoitsija nil
         :tallennus-kaynnissa? false
         :urakoitsijoiden-haku-kaynnissa? false
         :haetut-urakoitsijat nil}))

(defrecord ValitseUrakoitsija [urakoitsija])
(defrecord Nakymassa? [nakymassa?])
(defrecord UusiUrakoitsija [])
(defrecord TallennaUrakoitsija [urakoitsija])
(defrecord UrakoitsijaTallennettu [urakoitsija])
(defrecord UrakoitsijaEiTallennettu [virhe])
(defrecord UrakoitsijaaMuokattu [urakoitsija])
(defrecord HaeUrakoitsijat [])
(defrecord UrakoitsijatHaettu [urakoitsijat])
(defrecord UrakoitsijatEiHaettu [virhe])

(extend-protocol tuck/Event
  ValitseUrakoitsija
  (process-event [{urakoitsija :urakoitsija} app]
    (assoc app :valittu-urakoitsija urakoitsija))

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  UusiUrakoitsija
  (process-event [_ app]
    (assoc app :valittu-urakoitsija uusi-urakoitsija))

  TallennaUrakoitsija
  (process-event [{urakoitsija :urakoitsija} app]
    (assert (some? (:haetut-urakoitsijat app)) "Urakoitsija ei voi yrittää tallentaa, ennen kuin urakoitsijoiden haku on valmis.")
    (let [tulos! (tuck/send-async! ->UrakoitsijaTallennettu)
          fail! (tuck/send-async! ->UrakoitsijaEiTallennettu)]
      (go
        (try
          (let [vastaus urakoitsija] ;;TODO lisää tallennus
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :tallennus-kaynnissa? true))

  UrakoitsijaTallennettu
  (process-event [{urakoitsija :urakoitsija} app]
    (viesti/nayta! "Urakoitsija tallennettu!")
    (let [vanhat (group-by :id (:haetut-urakoitsijat app))
          uusi {(:id urakoitsija) [urakoitsija]}]
      ;; Yhdistetään tallennettu jo haettuihin.
      ;; Gridiin tultaessa Grid hakee vielä taustalla kaikki urakoitsijat
      (assoc app :haetut-urakoitsijat (vec (apply concat (vals (merge vanhat uusi))))
                 :tallennus-kaynnissa? false
                 :valittu-urakoitsija nil)))

  UrakoitsijaEiTallennettu
  (process-event [{virhe :virhe} app]
    (viesti/nayta! [:span "Virhe tallennuksessa! Urakoitsijaa ei tallennettu."] :danger)
    (assoc app :tallennus-kaynnissa? false
               :valittu-urakoitsija nil))

  UrakoitsijaaMuokattu
  (process-event [{urakoitsija :urakoitsija} app]
    (assoc app :valittu-urakoitsija urakoitsija))

  HaeUrakoitsijat
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->UrakoitsijatHaettu)
          fail! (tuck/send-async! ->UrakoitsijatEiHaettu)]
      (go
        (try
          (let [vastaus [{:nimi "Kalle" :id 1}] #_(async/<! (k/post! :hae-harjassa-luodut-urakoitsijat {}))] ;;FIXME toteuta palvelu
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :urakoitsijoiden-haku-kaynnissa? true))

  UrakoitsijatHaettu
  (process-event [{urakoitsijat :urakoitsijat} app]
    (assoc app :haetut-urakoitsijat urakoitsijat
               :urakoitsijoiden-haku-kaynnissa? false))

  UrakoitsijatEiHaettu
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe urakoitsijoiden haussa!"] :danger)
    (assoc app :urakoitsijoiden-haku-kaynnissa? false)))