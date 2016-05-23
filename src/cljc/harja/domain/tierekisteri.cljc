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