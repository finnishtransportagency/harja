(ns harja.kyselyt.organisaatiot
  "Organisaatioihin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/organisaatiot.sql")