(ns harja.domain.tierekisteri.validointi
  "Yhteiset validointikäsittelyt frontille ja bäkkärille tierekisterin suhteen"
  (:require [clojure.string :as str]
            #?@(:clj [
                      [clj-time.core :as t]])
            #?@(:cljs [[cljs-time.core :as t]])))


(defn validoi-tieosoite
  "Lisää jo saatuihin virheisiin mahdolliset tierekisteriosoitteen virheet"
  [validointivirheet tie alkuosa loppuosa alkuetaisyys loppuetaisyys]
  (let [virheet (as-> #{} virheet
                                (if (and tie alkuosa alkuetaisyys loppuosa loppuetaisyys)
                                  virheet
                                  (conj virheet "Osa tierekisteriosoitteesta puuttuu. "))
                                (if (and (= alkuosa loppuosa) (>= alkuetaisyys loppuetaisyys))
                                  (conj virheet "Tierekisteriosoitteen loppuetäisyys pienempi kuin alkuetäisyys. ")
                                  virheet)
                                (if (and (>= loppuosa alkuosa))
                                  virheet
                                  (conj virheet "Tierekisteriosoitteen loppuosa pienempi kuin alkuosa. ")))
        virheet (if-not (empty? virheet)
                  (conj validointivirheet virheet)
                  validointivirheet)]
    virheet))