(ns harja.palvelin.tyokalut.ajastettu-tehtava
  "Apufunktioita tehtävien ajastamiseen"

  (:require
    [chime :refer [chime-at]]
    [taoensso.timbre :as log]
    [clj-time.core :as t]
    [clj-time.periodic :refer [periodic-seq]]
    [clojure.string :as str])
  (:import (org.joda.time DateTimeZone DateTimeConstants)))

(def virhekasittely
  {:error-handler #(log/error "Käsittelemätön poikkeus ajastetussa tehtävässä:" %)})

(defn ajasta-paivittain [[tunti minuutti sekuntti] tehtava]
  (when (and tunti minuutti sekuntti)
    (chime-at (periodic-seq
                (.. (t/now)
                    (withZone (DateTimeZone/forID "Europe/Helsinki"))
                    (withTime tunti minuutti sekuntti 0))
                (t/days 1))
              tehtava
              virhekasittely)))

(defn ajasta-minuutin-valein [minuutit tehtava]
  (when minuutit
    (chime-at (periodic-seq
                (.. (t/now)
                    (withZone (DateTimeZone/forID "Europe/Helsinki")))
                (t/minutes minuutit))
              tehtava
              virhekasittely)))

(defn ajasta-viikonpaivana [paiva [tunti minuutti sekuntti] tehtava]
  (let [paiva (str/lower-case paiva)
        paiva (cond
                (str/starts-with? paiva "ma") DateTimeConstants/MONDAY
                (str/starts-with? paiva "ti") DateTimeConstants/TUESDAY
                (str/starts-with? paiva "ke") DateTimeConstants/WEDNESDAY
                (str/starts-with? paiva "to") DateTimeConstants/THURSDAY
                (str/starts-with? paiva "pe") DateTimeConstants/FRIDAY
                (str/starts-with? paiva "la") DateTimeConstants/SATURDAY
                (str/starts-with? paiva "su") DateTimeConstants/SUNDAY)]
    (when (and paiva tunti minuutti sekuntti)
     (chime-at (->> (periodic-seq
                      (.. (t/now)
                          (withZone (DateTimeZone/forID "Europe/Helsinki"))
                          (withTime tunti minuutti sekuntti 0))
                      (t/days 1))

                    (filter (comp #{paiva} #(.getDayOfWeek %))))
               tehtava
               virhekasittely))))
