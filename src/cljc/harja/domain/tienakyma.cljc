(ns harja.domain.tienakyma
  "Tienäkymän tietojen spec-määritykset"
  (:require [clojure.spec.alpha :as s]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.infopaneeli :as infopaneeli]
            #?@(:clj [[clojure.future :refer :all]])
            [harja.geo :as geo]))

(s/def ::alku inst?)
(s/def ::loppu inst?)
(s/def ::sijainti ::geo/geometria)

(s/def ::hakuehdot
  (s/keys :req-un [::sijainti ::alku ::loppu ::tr/tierekisteriosoite]))

(s/def ::tulokset
  (s/every ::infopaneeli/tulos))
