(ns harja.kyselyt.organisaatiot
  "Organisaatioihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/organisaatiot.sql"
  {:positional? true})

(declare listaa-organisaatiot-analytiikalle hae-vesivayla-organisaation-id-lyhenteella luo-organisaatio<!
  hae-id-y-tunnuksella paivita-elinvoimakeskus-geometria! hae-organisaatio hae-elinvoimakeskus
  hae-elinvoimakeskus-nimella listaa-organisaatiot onko-olemassa hae-elinvoimakeskus-id-kustannuspaikalla)
