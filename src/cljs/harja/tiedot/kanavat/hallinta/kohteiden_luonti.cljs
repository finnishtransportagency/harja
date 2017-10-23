(ns harja.tiedot.kanavat.hallinta.kohteiden-luonti
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :as async]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tt]

            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.kanavan-kohde :as kohde])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:nakymassa? false
                 :kohteiden-haku-kaynnissa? false
                 :kohdelomake-auki? false
                 :kohderivit nil
                 :kanavat nil}))

(defrecord Nakymassa? [nakymassa?])
(defrecord HaeKohteet [])
(defrecord KohteetHaettu [tulos])
(defrecord KohteetEiHaettu [virhe])
(defrecord AvaaKohdeLomake [])

(defn kohderivit [tulos]
  (mapcat
    (fn [kanava-ja-kohteet]
      (map
        (fn [kohde]
          (-> kohde
              (assoc ::kanava/id (::kanava/id kanava-ja-kohteet))
              (assoc ::kanava/nimi (::kanava/nimi kanava-ja-kohteet))
              (assoc :rivin-teksti (str
                                     (when-let [nimi (::kanava/nimi kanava-ja-kohteet)]
                                       (str nimi ", "))
                                     (when-let [nimi (::kohde/nimi kohde)]
                                       (str nimi ", "))
                                     (when-let [tyyppi (kohde/tyyppi->str (::kohde/tyyppi kohde))]
                                       (str tyyppi))))))
        (::kanava/kohteet kanava-ja-kohteet)))
    tulos))

(defn kanavat [tulos]
  (map #(select-keys % #{::kanava/id ::kanava/nimi}) tulos))

(extend-protocol tuck/Event
  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

  HaeKohteet
  (process-event [_ app]
    (if-not (:kohteiden-haku-kaynnissa? app)
      (-> app
          (tt/get! :hae-kanavat-ja-kohteet
                   {:onnistui ->KohteetHaettu
                    :epaonnistui ->KohteetEiHaettu})
          (assoc :kohteiden-haku-kaynnissa? true))

      app))

  KohteetHaettu
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :kohderivit (kohderivit tulos))
        (assoc :kanavat (kanavat tulos))
        (assoc :kohteiden-haku-kaynnissa? false)))

  KohteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Kohteiden haku epäonnistui!" :danger)
    (-> app
        (assoc :kohteiden-haku-kaynnissa? false)))

  AvaaKohdeLomake
  (process-event [_ app]
    (assoc app :kohdelomake-auki? true)))