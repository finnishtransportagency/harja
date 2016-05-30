(ns harja.domain.tierekisteri
  (:require [schema.core :as s]
            [clojure.string :as str]
            #?@(:cljs [[harja.loki :refer [log]]])))

(defn samalla-tiella? [tie1 tie2]
  (= (:tr-numero tie1) (:tr-numero tie2)))

(defn ennen?
  "Tarkistaa alkaako tie1 osa ennen tie2 osaa. Osien tulee olla samalla tienumerolla.
  Jos osat ovat eri teilla, palauttaa nil."
  [tie1 tie2]
  (when (samalla-tiella? tie1 tie2)
    (or (< (:tr-alkuosa tie1) (:tr-alkuosa tie2))
        (and (= (:tr-alkuosa tie1) (:tr-alkuosa tie2))
             (< (:tr-alkuetaisyys tie1) (:tr-alkuetaisyys tie2))))))

(defn alku
  "Palauttaa annetun tien alkuosan ja alkuetäisyyden vektorina"
  [{:keys [tr-alkuosa tr-alkuetaisyys]}]
  [tr-alkuosa tr-alkuetaisyys])

(defn loppu
  "Palauttaa annetun tien loppuosan ja loppuetäisyyden vektorina"
  [{:keys [tr-loppuosa tr-loppuetaisyys]}]
  [tr-loppuosa tr-loppuetaisyys])

(defn jalkeen?
  "Tarkistaa loppuuko tie1 osa ennen tie2 osaa. Osien tulee olla samalla tienumerolla.
  Jos osat ovat eri teillä, palauttaa nil."
  [tie1 tie2]
  (when (samalla-tiella? tie1 tie2)
    (or (> (:tr-loppuosa tie1) (:tr-loppuosa tie2))
        (and (= (:tr-loppuosa tie1) (:tr-loppuosa tie2))
             (> (:tr-loppuetaisyys tie1) (:tr-loppuetaisyys tie2))))))

(defn laske-tien-pituus
  ([tie] (laske-tien-pituus {} tie))
  ([osien-pituudet {aosa :tr-alkuosa
                    alkuet :tr-alkuetaisyys
                    losa :tr-loppuosa
                    loppuet :tr-loppuetaisyys}]
   (when (every? integer? [aosa losa alkuet loppuet])
     (let [pit
           (if (= aosa losa)
             (Math/abs (- loppuet alkuet))
             (loop [pituus (- (get osien-pituudet aosa 0) alkuet)
                    osa (inc aosa)]
               (let [osan-pituus (get osien-pituudet osa 0)]
                 (if (>= osa losa)
                   (+ pituus (Math/min loppuet osan-pituus))

                   (recur (+ pituus osan-pituus)
                          (inc osa))))))]
       #_(println "A: " aosa " " alkuet " -- L: " losa " " loppuet
                "  =>  " pit)
       pit))))

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
