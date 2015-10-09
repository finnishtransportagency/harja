(ns harja.kyselyt.geometriapaivitykset
  "Geometriapäivityksiin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/geometriapaivitykset.sql")