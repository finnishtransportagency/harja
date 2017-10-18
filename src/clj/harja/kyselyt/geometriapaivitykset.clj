(ns harja.kyselyt.geometriapaivitykset
  "Geometriapäivityksiin liittyvät tietokantakyselyt"
  (:require [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [clj-time.coerce :as time-coerce]
            [harja.kyselyt.geometriaaineistot :as geometria-aineistot]
            [harja.domain.geometriaaineistot :as ga]))

(defqueries "harja/kyselyt/geometriapaivitykset.sql"
            {:positional? true})

(defn pitaako-paivittaa? [db paivitystunnus tiedoston-muutospvm]
  (let [aineisto (geometria-aineistot/hae-voimassaoleva-geometria-aineisto db paivitystunnus)
        paivityksen-tiedot (first (harja.kyselyt.geometriapaivitykset/hae-paivitys db paivitystunnus))
        viimeisin-paivitys (:viimeisin_paivitys paivityksen-tiedot)]
    (log/debug (format "Geometriapäivitys: %s on päivitetty viimeksi: %s. Tiedosto on muuttunut viimeksi: %s " paivitystunnus viimeisin-paivitys tiedoston-muutospvm))
    (or (nil? viimeisin-paivitys)
        (not (pvm/valissa?
               (time-coerce/from-sql-time viimeisin-paivitys)
               (time-coerce/from-sql-time (::ga/voimassaolo-alkaa aineisto))
               (time-coerce/from-sql-time (::ga/voimassaolo-paattyy aineisto))
               false))
        (pvm/jalkeen?
          (time-coerce/from-sql-time tiedoston-muutospvm)
          (time-coerce/from-sql-time viimeisin-paivitys)))))

(defn harjan-verkon-pvm [db]
  (or (hae-karttapvm db) (pvm/nyt)))
