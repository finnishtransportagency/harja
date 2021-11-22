(ns harja.kyselyt.tieverkko
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/kyselyt/tieverkko.sql"
            {:positional? true})

(defn hae-tr-osoite-valille-ehka
  "Hakee TR osoitteen pisteille. Jos teile ei löydy yhteistä pistettä, palauttaa nil."
  [db x1 y1 x2 y2 threshold]
  (let [rivi (first (hae-tr-osoite-valille* db x1 y1 x2 y2 threshold))]
    (and (:tie rivi)
         rivi)))

(defn hae-tr-osoite-ehka
  "Hakee TR osoitteen pisteelle, jos osoitetta ei löydy, palauttaa nil."
  [db x y threshold]
  (let [rivi (first (hae-tr-osoite* db x y threshold))]
    (and (:tie rivi)
         rivi)))

(defn hae-trpisteiden-valinen-tieto-raaka
  "Raaka tulos 'laske_tr_tiedot' SQL funktiosta"
  [db {:keys [tr-numero tr-alkuosa tr-loppuosa] :as params}]
  (map (fn [tieto]
         (update tieto :pituudet konv/jsonb->clojuremap))
       (hae-trpisteiden-valinen-tieto db params))  )

(defn hae-trpisteiden-valinen-tieto-yhdistaa
  [db {:keys [tr-numero tr-alkuosa tr-loppuosa] :as params}]
  (println "petar ovde " (str params))
  (hae-trpisteiden-valinen-tieto-raaka db params))

(defn onko-tierekisteriosoite-validi? [db tie aosa aet losa loppuet]
  (let [osoite {:tie tie :aosa aosa :aet aet :losa losa :loppuet loppuet}]
    (some? (tierekisteriosoite-viivaksi db osoite))))

(defn ovatko-tierekisteriosoitteen-etaisyydet-validit?[db tie aosa aet losa loppuet]
  (let [osoite {:tie tie :aosa aosa :aet aet :losa losa :loppuet loppuet}]
    (onko-osoitteen-etaisyydet-validit? db osoite)))
