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
                                  (conj virheet (str "Osa tieosoitteesta puuttuu: "
                                                  (when (nil? tie) "tienumero, ")
                                                  (when (nil? alkuosa) "alkuosa, ")
                                                  (when (nil? alkuetaisyys) "alkuetaisyys, ")
                                                  (when (nil? loppuosa) "loppuosa, ")
                                                  (when (nil? loppuetaisyys) "loppuetaisyys, ")))))
        virheet (if-not (empty? virheet)
                  (conj validointivirheet virheet)
                  validointivirheet)]
    virheet))
