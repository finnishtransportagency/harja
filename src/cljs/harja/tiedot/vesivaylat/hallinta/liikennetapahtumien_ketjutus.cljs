(ns harja.tiedot.vesivaylat.hallinta.liikennetapahtumien-ketjutus
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [cljs.core.async :as async]
            [harja.pvm :as pvm])
(:require-macros [cljs.core.async.macros :refer [go]]))

(defonce tila
  (atom {:nakymassa? false
         :valittu-sopimus nil
         :tallennus-kaynnissa? false
         :sopimuksien-haku-kaynnissa? false
         :haetut-sopimukset nil}))

(defrecord ValitseSopimus [sopimus])
(defrecord Nakymassa? [nakymassa?])
;;(defrecord UusiSopimus [])
;;(defrecord TallennaSopimus [sopimus])
;;(defrecord SopimusTallennettu [sopimus])
;;(defrecord SopimusEiTallennettu [virhe])
;;(defrecord SopimustaMuokattu [sopimus])
(defrecord HaeSopimukset [])
(defrecord SopimuksetHaettu [sopimukset])
(defrecord SopimuksetEiHaettu [virhe])

(extend-protocol tuck/Event
  ValitseSopimus
  (process-event [{sopimus :sopimus} app]
    (assoc app :valittu-sopimus sopimus))

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeSopimukset
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->SopimuksetHaettu)
          fail! (tuck/send-async! ->SopimuksetEiHaettu)]
      (go
        (try
          (let [vastaus (async/<! (k/post! :hae-vesivayla-kanavien-hoito-sopimukset {}))] 
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :sopimuksien-haku-kaynnissa? true))

  SopimuksetHaettu
  (process-event [{sopimukset :sopimukset} app]
    (assoc app :haetut-sopimukset sopimukset
      :sopimuksien-haku-kaynnissa? false))

  SopimuksetEiHaettu
  (process-event [_ app]
    (viesti/nayta! [:span "Virhe sopimuksien haussa!"] :danger)
    (assoc app :sopimuksien-haku-kaynnissa? false)))
