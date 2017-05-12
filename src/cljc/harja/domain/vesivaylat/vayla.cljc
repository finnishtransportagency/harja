(ns harja.domain.vesivaylat.vayla
  "Väylän tiedot"
  (:require
    [clojure.spec :as s]
    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]])
    [clojure.string :as str])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_vayla" ::reimari-vayla]
  ["vv_vayla" ::vayla])

;; TODO Korvaa specql:n tuella muuttaa suoraan setiksi keywordeja
(s/def ::tyyppi #{:muu :kauppamerenkulku})

(def tyypit (s/describe ::tyyppi))

(defn tyyppi-fmt [tyyppi]
  (when tyyppi
    (str/capitalize (name tyyppi))))