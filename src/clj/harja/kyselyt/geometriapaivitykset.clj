(ns harja.kyselyt.geometriapaivitykset
  "Geometriapäivityksiin liittyvät tietokantakyselyt"
  (:require [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [clj-time.coerce :as time-coerce]))

(defqueries "harja/kyselyt/geometriapaivitykset.sql"
  {:positional? true})

(defn pitaako-paivittaa? [db paivitystunnus tiedoston-muutospvm]
  (let [paivityksen-tiedot (first (harja.kyselyt.geometriapaivitykset/hae-paivitys db paivitystunnus))
        viimeisin-paivitys (:viimeisin_paivitys paivityksen-tiedot)]
    (log/debug (format "Geometriapäivitys: %s on päivitetty viimeksi: %s. Tiedosto on muuttunut viimeksi: %s " paivitystunnus viimeisin-paivitys tiedoston-muutospvm))
    (or (nil? viimeisin-paivitys)
        (pvm/jalkeen?
          (time-coerce/from-sql-time tiedoston-muutospvm)
          (time-coerce/from-sql-time viimeisin-paivitys)))))
