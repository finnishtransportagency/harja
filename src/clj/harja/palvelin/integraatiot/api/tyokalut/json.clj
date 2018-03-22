(ns harja.palvelin.integraatiot.api.tyokalut.json
  "APIn kautta tulevan JSONin käsittelyn apureita."
  (:require [harja.kyselyt.konversio :as konv]
            [harja.geo :as geo]
            [clj-time.format :as format]
            [taoensso.timbre :as log]
            [clj-time.coerce :as coerce]
            [clj-time.coerce :as c])
  (:import (java.text SimpleDateFormat)))

(defn aika-string->java-sql-date [paivamaara]
  (when paivamaara
    (konv/sql-date (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") paivamaara))))

(defn aika-string->java-util-date [paivamaara]
  (when paivamaara
    (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") paivamaara)))

(defn aika-string->java-sql-timestamp [paivamaara]
  (when paivamaara
    (konv/sql-timestamp (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") paivamaara))))

(defn aika-string->joda-time [paivamaara]
  (when paivamaara
    (c/from-date (aika-string->java-util-date paivamaara))))

(defn pvm-string->java-sql-date [paivamaara]
  (when paivamaara
    (konv/sql-date (.parse (SimpleDateFormat. "yyyy-MM-dd") paivamaara))))

(defn pvm-string->java-util-date [paivamaara]
  (when paivamaara
    (.parse (SimpleDateFormat. "yyyy-MM-dd") paivamaara)))

(defn pvm-string->joda-date [paivamaara]
  (when paivamaara
    (c/from-date (.parse (SimpleDateFormat. "yyyy-MM-dd") paivamaara))))

(defn json-pvm [paivamaara]
  (.format (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") paivamaara))

(defn henkilo->nimi
  "Antaa json skeeman mukaiselle henkilölle nimen yhdistämällä etu- ja sukunimet."
  [henkilo]
  (when henkilo
    (str (:etunimi henkilo) " " (:sukunimi henkilo))))

(defn sijainti->tr
  "Antaa json skeeman mukaiselle sijainnille tierekisterosoitteen Harja muodossa."
  [sijainti]
  (some-> sijainti
          :tie
          ((fn [{numero :numero alkuosa :aosa alkuetaisyys :aet loppuosa :losa loppuetaisyys :let}]
             {:numero        numero
              :alkuosa       alkuosa
              :alkuetaisyys  alkuetaisyys
              :loppuosa      loppuosa
              :loppuetaisyys loppuetaisyys}))))

(defn sijainti->point
  "Antaa json skeeman mukaisen sijainnin koordinaatit [x y] vektorina."
  [sijainti]
  (some-> sijainti
          :koordinaatit
          ((fn [{x :x y :y}]
             [x y]))))

(defn parsi-json-pvm-vectorista
  "Muuntaa avainpolussa olevan vectorin jokaisen mapin itemin pvm-avain -kentän Clojurelle sopivaan muotoon."
  [map avainpolku pvm-avain]
  (log/debug "Muutetaan mapin " (pr-str map) " polussa " (pr-str avainpolku) " olevan vectorin jokaisen itemin avaimen " pvm-avain " takaa löytyvä pvm clojure-muotoon")
  (-> map
      (assoc-in avainpolku
                (when-let [vector (some-> map (get-in avainpolku))]
                  (mapv (fn [item]
                          (assoc item pvm-avain (aika-string->java-sql-date (pvm-avain item))))
                        vector)))))

(defn nil-turvallinen-bigdec [arvo]
  (when (not (nil? arvo))
    (bigdec arvo)))
