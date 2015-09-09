(ns harja.domain.yleiset
  "Yleisiä skeemaentiteettejä."
  (:require [schema.core :as s]
            [clojure.string :as str]))

(def Osapuoli (s/enum :tilaaja :urakoitsija :konsultti))

(def Tierekisteriosoite
  {:numero s/Int
   :alkuosa s/Int
   :alkuetaisyys s/Int
   (s/optional-key :loppuosa) s/Int
   (s/optional-key :loppuetaisyys) s/Int})

(def Coordinate [s/Num])

(def Line {:type (s/eq :line)
           :points [Coordinate]})

(def MultiLine {:type (s/eq :multiline)
                :lines [Line]})

(def Point {:type (s/eq :point)
            :coordinates Coordinate})

;; Sijainti on joko viiva tai piste
(def Sijainti (s/either MultiLine Point))

(def Teksti (s/both s/Str (s/pred (comp not str/blank?))))
