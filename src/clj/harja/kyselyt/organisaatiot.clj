(ns harja.kyselyt.organisaatiot
  "Organisaatioihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/organisaatiot.sql"
  {:positional? true})

(declare listaa-organisaatiot-analytiikalle)
