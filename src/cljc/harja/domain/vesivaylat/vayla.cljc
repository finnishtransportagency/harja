(ns harja.domain.vesivaylat.vayla
  "Väylän tiedot"
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]
    [specql.rel :as rel]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reimari_vayla" ::reimari-vayla
   {"nro" ::r-nro
    "nimi" ::r-nimi
    "ryhma" ::r-tyhma}]
  ["vv_vaylatyyppi" ::vaylatyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["vv_vayla" ::vayla
   {
    #?@(:clj [::turvalaite (rel/has-many ::id :harja.domain.vesivaylat.turvalaite/turvalaite :harja.domain.vesivaylat.turvalaite/vayla-id)])}])

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
    :muu "Muu vesiliikenne"
    ;; Formatoidaan sinne päin
    (str/capitalize (name tyyppi))))

(defn vaylan-nimi-idlla [vaylat vayla-id]
  (::nimi (first (filter
                   #(= (::id %) vayla-id)
                   vaylat))))

(def perustiedot
  #{::id
    ::nimi
    ::tyyppi})

(def turvalaite #{[::turvalaite #{:harja.domain.vesivaylat.turvalaite/id}]})

(def viittaukset (clojure.set/union turvalaite))

(def kaikki-kentat
  (clojure.set/union
    perustiedot
    viittaukset))
