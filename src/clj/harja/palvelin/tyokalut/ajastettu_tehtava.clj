(ns harja.palvelin.tyokalut.ajastettu-tehtava
  "Apufunktioita tehtävien ajastamiseen"

  (:require
    [chime :refer [chime-at]]
    [clj-time.core :as t]
    [clj-time.core :as t]
    [clj-time.periodic :refer [periodic-seq]])
  (:import (org.joda.time DateTimeZone)))

(defn ajasta-paivittain [[tunti minuutti sekuntti] tehtava]
  (chime-at (periodic-seq
              (.. (t/now)
                  (withZone (DateTimeZone/forID "Europe/Helsinki"))
                  (withTime tunti minuutti sekuntti 0))
              (t/days 1))
            tehtava))