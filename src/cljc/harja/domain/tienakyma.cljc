(ns harja.domain.tienakyma
  "Tienäkymän tietojen spec-määritykset"
  (:require [clojure.spec :as s]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.infopaneeli :as infopaneeli]
            #?@(:clj [[clojure.future :refer :all]])))

(s/def ::alku inst?)
(s/def ::loppu inst?)

(s/def ::hakuehdot
  (s/keys :req-un [::sijainti ::alku ::loppu ::tr/tierekisteriosoite]))

(s/def ::tulokset
  (s/coll-of ::infopaneeli/tulos))
