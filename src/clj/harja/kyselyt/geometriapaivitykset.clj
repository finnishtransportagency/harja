(ns harja.kyselyt.geometriapaivitykset
  "Geometriapäivityksiin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [clj-time.coerce :as time-coerce]))

(defqueries "harja/kyselyt/geometriapaivitykset.sql")

(defn pitaako-paivittaa? [db paivitystunnus tiedoston-muutospvm]
  (let [paivityksen-tiedot (first (harja.kyselyt.geometriapaivitykset/hae-paivitys db paivitystunnus))
        viimeisin-paivitys (:viimeisin_paivitys paivityksen-tiedot)]
    (or (nil? viimeisin-paivitys)
        (pvm/jalkeen?
          (time-coerce/from-sql-time tiedoston-muutospvm)
          (time-coerce/from-sql-time viimeisin-paivitys)))))