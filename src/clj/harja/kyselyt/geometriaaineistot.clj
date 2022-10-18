(ns harja.kyselyt.geometriaaineistot
  (:require
    [harja.domain.geometriaaineistot :as ga]
    [specql.core :refer [fetch update! insert! upsert! delete!]]
    [specql.op :as op]
    [jeesql.core :refer [defqueries]]
    [harja.pvm :as pvm]))

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

(defn hae-voimassaoleva-geometria-aineisto [db nimi]
      (first (fetch db ::ga/geometria-aineistot ga/kaikki-kentat
                (op/and
                  {::ga/nimi nimi}
                  (op/or {::ga/voimassaolo-alkaa op/null?}
                         {::ga/voimassaolo-alkaa (op/<= (pvm/nyt))})
                  (op/or {::ga/voimassaolo-paattyy op/null?}
                         {::ga/voimassaolo-paattyy (op/>= (pvm/nyt))})))))

(defn tallenna-geometria-aineisto [db geometria-ainesto]
  (upsert! db ::ga/geometria-aineistot geometria-ainesto))

(defn poista-geometria-aineisto [db id]
  (delete! db ::ga/geometria-aineistot {::ga/id id}))


