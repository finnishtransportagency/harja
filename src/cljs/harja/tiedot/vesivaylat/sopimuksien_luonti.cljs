(ns harja.tiedot.vesivaylat.sopimuksien-luonti
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [cljs.core.async :as async])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def uusi-sopimus {})

(defonce tila
  (atom {:nakymassa? false
         :valittu-sopimus nil
         :tallennus-kaynnissa? false
         :sopimuksien-haku-kaynnissa? false
         :haetut-sopimukset nil}))

(defrecord ValitseSopimus [sopimus])
(defrecord Nakymassa? [nakymassa?])
(defrecord UusiSopimus [])
(defrecord TallennaSopimus [sopimus])
(defrecord SopimusTallennettu [sopimus])
(defrecord SopimusEiTallennettu [virhe])
(defrecord SopimustaMuokattu [sopimus])
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

  UusiSopimus
  (process-event [_ app]
    (assoc app :valittu-sopimus uusi-sopimus))

  TallennaSopimus
  (process-event [{sopimus :sopimus} app]
    (assert (some? (:haetut-sopimukset app)) "Sopimusta ei voi yrittää tallentaa, ennen kuin sopimuksien haku on valmis.")
    (let [tulos! (tuck/send-async! ->SopimusTallennettu)
          fail! (tuck/send-async! ->SopimusEiTallennettu)]
      (go
        (try
          (let [vastaus (async/<! (k/post! :tallenna-sopimus sopimus))]
            (if (k/virhe? vastaus)
              (fail! vastaus)
              (tulos! vastaus)))
          (catch :default e
            (fail! nil)
            (throw e)))))
    (assoc app :tallennus-kaynnissa? true))

  SopimusTallennettu
  (process-event [{sopimus :sopimus} app]
    (viesti/nayta! "Sopimus tallennettu!")
    (let [vanhat (group-by :id (:haetut-sopimukset app))
          uusi {(:id sopimus) [sopimus]}]
      ;; Yhdistetään tallennettu jo haettuihin.
      ;; Gridiin tultaessa Grid hakee vielä taustalla kaikki sopimukset
      (assoc app :haetut-sopimukset (vec (apply concat (vals (merge vanhat uusi))))
                 :tallennus-kaynnissa? false
                 :valittu-sopimus nil)))

  SopimusEiTallennettu
  (process-event [{virhe :virhe} app]
    (viesti/nayta! [:span "Virhe tallennuksessa! Sopimusta ei tallennettu."] :danger)
    (assoc app :tallennus-kaynnissa? false
               :valittu-sopimus nil))

  SopimustaMuokattu
  (process-event [{sopimus :sopimus} app]
    (assoc app :valittu-sopimus sopimus))

  HaeSopimukset
  (process-event [_ app]
    (let [tulos! (tuck/send-async! ->SopimuksetHaettu)
          fail! (tuck/send-async! ->SopimuksetEiHaettu)]
      (go
        (try
          (let [vastaus (async/<! (k/post! :hae-harjassa-luodut-sopimukset {}))]
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
