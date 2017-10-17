(ns harja.kyselyt.geometriaaineistot
  (:require
    [harja.domain.geometriaaineistot :as ga]
    [specql.core :refer [fetch update! insert! upsert! delete!]]
    [jeesql.core :refer [defqueries]]))

(defn tallenna-urakan-tyotunnit [db geometria-aineistot]
  (upsert! db
           ::ga/geometria-aineistot
           #{::ga/nimi
             ::ga/tiedostonimi
             ::ga/voimassaolo-alkaa
             ::ga/voimassaolo-paattyy}
           geometria-aineistot))

(defn hae-geometria-aineistot [db]
  (fetch db ::ga/geometria-aineistot ga/kaikki-kentat {}))

(defn tallenna-geometria-aineisto [db geometria-ainesto]
  (upsert! db ::ga/geometria-aineistot geometria-ainesto))

(defn poista-geometria-aineisto [db id]
  (delete! db ::ga/geometria-aineistot {::ga/id id}))


