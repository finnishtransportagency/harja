(ns harja.domain.yleiset
  "Yleisiä skeemaentiteettejä."
  (:require [schema.core :as s]
            [clojure.string :as str]))

(def Osapuoli (s/enum :tilaaja :urakoitsija :konsultti))

(def Tierekisteriosoite
  {:numero s/Int
   :alkuosa s/Int
   :alkuetaisyys s/Int
   :loppuosa s/Int
   :loppuetaisyys s/Int})

;; Sijainti on tarkalleen 2 koordinaattia, x ja y
(def Sijainti [s/Num])

(def Teksti (s/both s/Str (s/pred (comp not str/blank?))))
