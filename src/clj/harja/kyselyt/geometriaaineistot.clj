(ns harja.kyselyt.geometriaaineistot
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [harja.domain.geometriaaineistot :as ga]
    [specql.core :refer [fetch update! insert! upsert!]]
    [jeesql.core :refer [defqueries]]
    [specql.op :as op]
    [harja.pvm :as pvm]
    [harja.kyselyt.konversio :as konv]))


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

(defn tallenna-geometria-aineistot [db geometria-ainestot]
    (upsert! db ::ga/geometria-aineistot #{::nimi
                                           :tiedostonimi
                                           ::voimassaolo-alkaa
                                           ::voimassaolo-paattyy}
             geometria-ainestot))


