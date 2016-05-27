(ns harja.domain.tierekisteri
  (:require [schema.core :as s]
            [clojure.string :as str]
            #?@(:cljs [[harja.loki :refer [log]]])))

(defn laske-tien-pituus
  ([tie] (laske-tien-pituus {} tie))
  ([osien-pituudet {aosa :tr-alkuosa
                    alkuet :tr-alkuetaisyys
                    losa :tr-loppuosa
                    loppuet :tr-loppuetaisyys}]
   (when (every? integer? [aosa losa alkuet loppuet])
     (if (= aosa losa)
       (- loppuet alkuet)
       (loop [pituus (- (get osien-pituudet aosa 0) alkuet)
              osa (inc aosa)]
         (let [osan-pituus (get osien-pituudet osa 0)]
           (if (>= osa losa)
             (+ pituus (Math/min loppuet osan-pituus))

             (recur (+ pituus osan-pituus)
                    (inc osa)))))))))

(defn tiekohteiden-jarjestys [kohde]
  ((juxt :tie :tr-numero :tienumero
        :aosa :tr-alkuosa
        :aet :tr-alkuetaisyys) kohde))

(defn tierekisteriosoite-tekstina
  [tr]
  (let [tie (or (:numero tr) (:tr-numero tr))
        aosa (or (:alkuosa tr) (:tr-alkuosa tr))
        aet (or (:alkuetaisyys tr) (:tr-alkuetaisyys tr))
        losa (or (:loppuosa tr) (:tr-loppuosa tr))
        let (or (:loppuetaisyys tr) (:tr-loppuetaisyys tr))]
    (if tie
      (str "Tie " tie " / "
           aosa " / "
           aet " / "
           losa " / "
           let)
      (str "Ei tierekisteriosoitetta"))))

(defn jarjesta-kohteiden-kohdeosat [kohteet]
  (mapv
    (fn [kohde]
      (assoc kohde :kohdeosat (sort-by tiekohteiden-jarjestys (:kohdeosat kohde))))
      kohteet))
