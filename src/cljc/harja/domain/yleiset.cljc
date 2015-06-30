(ns harja.domain.yleiset
  "Yleisiä skeemaentiteettejä."
  (:require [schema.core :as s]))

(def Osapuoli (s/enum :tilaaja :urakoitsija :konsultti))

(def Tierekisteriosoite
  {:numero s/Int
   :alkuosa s/Int
   :alkuetaisyys s/Int
   :loppuosa s/Int
   :loppuetaisyys s/Int})
