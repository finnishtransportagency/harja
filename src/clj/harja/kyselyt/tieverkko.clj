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

(defn laske-max-loppuetaisyys [osoitteet]
  (let [laske-loppuetaisyys (fn [{:keys [pituus tr-alkuetaisyys]}]
                              (+ pituus tr-alkuetaisyys))
        loppuetaisyydet (map laske-loppuetaisyys osoitteet)]
    (apply max loppuetaisyydet)))

(defn muodosta-ajoradat
  "Löydä osiot, ja yhdistä 'continuous' kaistat.

   Inputista:    [{:pituus 1500, :tr-kaista 11, :tr-ajorata 1, :tr-alkuetaisyys 0}...],

   tekee output: [{:osiot [{:pituus 1500,
                            :kaistat [{:pituus 1500, :tr-kaista 11, :tr-alkuetaisyys 0}],
                            :tr-alkuetaisyys 0}],
                   :tr-ajorata 1}...]"
  [osoitteet]
  (let [osoitteet-by-ajoradat (group-by :tr-ajorata osoitteet)
        muodosta-ajorata (fn [[ajorata osoitteet]]
                           (println "petar osoitteet " ajorata " = " (str osoitteet))
                           (let [osoitteet-jarjestyksessa (sort-by :tr-alkuetaisyys osoitteet)
                                 alkuetaisyys (:tr-alkuetaisyys (first osoitteet-jarjestyksessa))
                                 pituus (- (laske-max-loppuetaisyys osoitteet-jarjestyksessa) alkuetaisyys)
                                 muodosta-kaistat (fn [{:keys [pituus tr-kaista tr-alkuetaisyys]}]
                                                    {:pituus pituus :tr-kaista tr-kaista :tr-alkuetaisyys tr-alkuetaisyys})
                                 kaistat (map muodosta-kaistat osoitteet)]
                             {:osiot [{:pituus pituus
                                       :kaistat (vec kaistat)
                                       :tr-alkuetaisyys alkuetaisyys}]
                              :tr-ajorata ajorata}))]
    (println "petar osoitet-by-ajoradat " (str osoitteet-by-ajoradat))
    (vec (map muodosta-ajorata osoitteet-by-ajoradat))))

(defn hae-trpisteiden-valinen-tieto-yhdistaa
  [db {:keys [tr-numero tr-alkuosa tr-loppuosa] :as params}]
  (println "petar ovde " (str params))
  (let [raaka-osat (hae-trpisteiden-valinen-tieto-raaka db params)
        kasitele-raaka (fn [raaka-osa]
                         (let [pituudet (:pituudet raaka-osa)
                               pituudet-ajoradat (assoc pituudet :ajoradat (muodosta-ajoradat (:osoitteet pituudet)))
                               pituudet-lopputulos (dissoc pituudet-ajoradat :osoitteet)]
                           (assoc raaka-osa :pituudet pituudet-lopputulos)))]
    (map kasitele-raaka raaka-osat)))

(defn onko-tierekisteriosoite-validi? [db tie aosa aet losa loppuet]
  (let [osoite {:tie tie :aosa aosa :aet aet :losa losa :loppuet loppuet}]
    (some? (tierekisteriosoite-viivaksi db osoite))))

(defn ovatko-tierekisteriosoitteen-etaisyydet-validit? [db tie aosa aet losa loppuet]
  (let [osoite {:tie tie :aosa aosa :aet aet :losa losa :loppuet loppuet}]
    (onko-osoitteen-etaisyydet-validit? db osoite)))
