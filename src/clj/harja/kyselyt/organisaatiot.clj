(ns harja.kyselyt.organisaatiot
  "Organisaatioihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/organisaatiot.sql"
  {:positional? true})

(declare listaa-organisaatiot-analytiikalle hae-ely-id-sampo-hashilla hae-vesivayla-organisaation-id-lyhenteella luo-organisaatio<!
  hae-id-y-tunnuksella)
