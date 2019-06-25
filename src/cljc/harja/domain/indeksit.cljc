(ns harja.domain.indeksit
  "Domain tietojen määrittely indekseillä. Palvelujen käyttämät specit."
  (:require [clojure.spec.alpha :as s]
            [harja.domain.urakka :as urakka]))

(s/def ::paallystysurakan-indeksi
  (s/keys :req-un [::id ::urakka ::lahtotason-vuosi ::lahtotason-kuukausi
                   ::indeksi]))

(s/def ::urakka-id nat-int?)
(s/def ::id int?)
(s/def ::urakka nat-int?)
(s/def ::lahtotason-vuosi (s/int-in 1970 2050))
(s/def ::lahtotason-kuukausi (s/int-in 1 13))

(s/def ::indeksinimi string?)
(s/def ::arvo (s/nilable number?)) ; Arvo voi olla NULL (ei vielä tiedossa)
(s/def ::raakaaine string?)
(s/def ::urakkatyyppi ::urakka/urakkatyyppi-kw)

(s/def ::indeksi
  (s/keys :req-un [::id ::indeksinimi]
          :opt-un [::raakaaine ::urakkatyyppi  ::arvo]))

(s/def ::paallystysurakan-indeksit
  (s/coll-of ::paallystysurakan-indeksi))
