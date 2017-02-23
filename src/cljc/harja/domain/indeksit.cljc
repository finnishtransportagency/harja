(ns harja.domain.indeksit
  "Domain tietojen määrittely indekseillä. Palvelujen käyttämät specit."
  (:require [clojure.spec :as s]
            #?@(:clj [[clojure.future :refer :all]])
            [harja.domain.urakka :as urakka]))

 {:id 2,
  :urakka 5,
  :lahtotason-vuosi 2016,
  :lahtotason-kuukausi 9,
  :indeksi
  {:id 86,
   :indeksinimi "Platts: FO 3,5%S CIF NWE Cargo",
   :arvo 206.29M,
   :raakaaine "bitumi",
   :urakkatyyppi "paallystys"}}

(s/def ::paallystysurakan-indeksi
  (s/keys :req-un [::id ::urakka ::lahtotason-vuosi ::lahtotason-kuukausi
                   ::indeksi]))

(s/def ::urakka-id nat-int?)
(s/def ::id int?)
(s/def ::urakka nat-int?)
(s/def ::lahtotason-vuosi (s/int-in 1970 2050))
(s/def ::lahtotason-kuukausi (s/int-in 1 13))

(s/def ::indeksinimi string?)
(s/def ::arvo number?)
(s/def ::raakaaine string?)
(s/def ::urakkatyyppi ::urakka/tyyppi)

(s/def ::indeksi
  (s/keys :req-un [::id ::indeksinimi]
          :opt-un [::raakaaine ::urakkatyyppi  ::arvo]))

(s/def ::paallystysurakan-indeksit
  (s/coll-of ::paallystysurakan-indeksi))
