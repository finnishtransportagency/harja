(ns harja.palvelin.api.tyokalut.json
  "APIn kautta tulevan JSONin käsittelyn apureita."
  (:require [harja.kyselyt.konversio :as konv]
            [harja.geo :as geo])
  (:import (java.text SimpleDateFormat)))

(defn parsi-aika [paivamaara]
  (konv/sql-date (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") paivamaara)))

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
             {:numero numero
              :alkuosa alkuosa
              :alkuetaisyys alkuetaisyys
              :loppuosa loppuosa
              :loppuetaisyys loppuetaisyys}))))

(defn sijainti->point
  "Antaa json skeeman mukaisen sijainnin koordinaatit [x y] vektorina."
  [sijainti]
  (some-> sijainti
          :koordinaatit
          ((fn [{x :x y :y}]
             [x y]))))
