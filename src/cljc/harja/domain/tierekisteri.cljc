(ns harja.domain.tierekisteri
  (:require [schema.core :as s]
            [clojure.string :as str]))

; FIXME Buginen (HAR-2303)
(defn laske-tien-pituus [{alkuet :aet loppuet :let}]
  (if (and alkuet loppuet)
    (Math/abs (- loppuet alkuet))))

(defn tiekohteiden-jarjestys [kohde]
  ((juxt :tie :tr-numero :tienumero
        :aosa :tr-alkuosa
        :aet :tr-alkuetaisyys) kohde))
