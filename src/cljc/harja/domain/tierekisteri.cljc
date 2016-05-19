(ns harja.domain.tierekisteri
  (:require [schema.core :as s]
            [clojure.string :as str]))

; FIXME Buginen (HAR-2303). Korjataan niin, että frontilla kutsutaan backendiä ja backissä haetaan suoraan
; kannasta
(defn laske-tien-pituus [tie]
  (let [alkuetaisyys (or (:aet tie)
                         (:tr-alkuetaisyys tie))
        loppuetaisyys (or (:let tie)
                          (:tr-loppuetaisyys tie))]
    (if (and alkuetaisyys loppuetaisyys)
     (Math/abs (- loppuetaisyys alkuetaisyys)))))

(defn tiekohteiden-jarjestys [kohde]
  ((juxt :tie :tr-numero :tienumero
        :aosa :tr-alkuosa
        :aet :tr-alkuetaisyys) kohde))
