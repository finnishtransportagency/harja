(ns harja.domain.tienakyma
  (:require [clojure.spec :as s]
            [harja.domain.tierekisteri :as tr]
            [harja.pvm :as pvm]))

(s/def ::alku inst?)
(s/def ::loppu inst?)

(s/def ::hakuehdot
  (s/keys :req-un [::sijainti ::alku ::loppu ::tr/tierekisteriosoite]))
