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

(defn tyyppien-jarjestys [tyyppi]
  (case tyyppi
    nil 0
    :kauppamerenkulku 1
    :muu 2
    99))

(defn tyyppi-fmt [tyyppi]
  (case tyyppi
    :kauppamerenkulku "Kauppamerenkulku"
    :muu "Muu"
    ;; Formatoidaan sinne päin
    (str/capitalize (name tyyppi))))

(defn vaylan-nimi-idlla [vaylat vayla-id]
  (::nimi (first (filter
                   #(= (::id %) vayla-id)
                   vaylat))))