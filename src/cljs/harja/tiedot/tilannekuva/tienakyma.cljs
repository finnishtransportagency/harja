(ns harja.tiedot.tilannekuva.tienakyma
  "Tien 'supernäkymän' tiedot."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.kartta :as kartta]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!] :as async]
            [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defonce tienakyma (atom {:valinnat {}
                          :sijainti nil
                          :haku-kaynnissa? nil
                          :tulokset nil}))

(defrecord PaivitaSijainti [sijainti])
(defrecord PaivitaValinnat [valinnat])
(defrecord Hae [])
(defrecord HakuValmis [tulokset])

(extend-protocol tuck/Event
  PaivitaSijainti
  (process-event [{s :sijainti} tienakyma]
    (assoc tienakyma :sijainti s))

  PaivitaValinnat
  (process-event [{uusi :valinnat} tienakyma]
    (let [vanha (:valinnat tienakyma)
          alku-muuttunut? (not= (:alku vanha) (:alku uusi))
          valinnat (as-> uusi v
                     ;; Jos alku muuttunut ja vanhassa alku ja loppu olivat samat,
                     ;; päivitä myös loppukenttä
                     (if (and alku-muuttunut?
                              (= (:alku vanha) (:loppu vanha)))
                       (assoc v :loppu (:alku uusi))
                       v))]
      (assoc tienakyma :valinnat valinnat)))

  Hae
  (process-event [_ tienakyma]
    (let [valinnat (:valinnat tienakyma)
          tulos! (tuck/send-async! ->HakuValmis)]
      (go
        (tulos! (<! (k/post! :hae-tienakymaan valinnat))))
      (assoc tienakyma
             :tulokset nil
             :tulokset-kartalla nil
             :haku-kaynnissa? true)))

  HakuValmis
  (process-event [{tulokset :tulokset} tienakyma]
    (assoc tienakyma
           :tulokset tulokset
           :tulokset-kartalla "FIXME: tulokset kartalla esitettävään muotoon"
           :haku-kaynnissa? false)))
