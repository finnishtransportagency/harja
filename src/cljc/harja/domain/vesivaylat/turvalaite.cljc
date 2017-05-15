(ns harja.domain.vesivaylat.turvalaite
  "Turvalaitteen tiedot"
  (:require
    [clojure.spec :as s]
    [clojure.string :as str]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]
              [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_turvalaite" ::reimari-turvalaite]
  ["vv_turvalaite" ::turvalaite])

;; TODO Korvaa specql:n tuella muuttaa suoraan setiksi keywordeja
(s/def ::tyyppi #{:kiintea :poiju :viitta})

(def tyypit (s/describe ::tyyppi))

(defn tyyppi-fmt [tyyppi]
  (case tyyppi
    :kiintea "Kiinteä"
    :poiju "Poiju"
    :viitta "Viitta"
    ;; Formatoidaan sinne päin
    (str/capitalize (name tyyppi))))