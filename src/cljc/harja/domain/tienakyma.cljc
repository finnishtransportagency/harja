(ns harja.domain.tienakyma
  "Tienäkymän tietojen spec-määritykset"
  (:require [clojure.spec.alpha :as s]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.infopaneeli :as infopaneeli]
            [harja.geo :as geo]))

(s/def ::alku inst?)
(s/def ::loppu inst?)
(s/def ::sijainti ::geo/geometria)
(s/def ::tulos (s/every ::infopaneeli/tulos))
(s/def ::timeout? boolean?)

(s/def ::hakuehdot
  (s/keys :req-un [::sijainti ::alku ::loppu ::tr/tierekisteriosoite]))

(s/def ::tulokset
  (s/keys :req-un [::tulos]
          :opt-un [::timeout?]))
