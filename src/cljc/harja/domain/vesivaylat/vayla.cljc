(ns harja.domain.vesivaylat.vayla
  "Väylän tiedot"
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
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
   harja.domain.muokkaustiedot/poistettu?-sarake
     #_{::turvalaitteet (specql.rel/has-many ::id :harja.domain.vesivaylat.turvalaite/turvalaite :harja.domain.vesivaylat.turvalaite/vayla-id)}])

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

(def turvalaitteet #{[::turvalaitteet #{:harja.domain.vesivaylat.turvalaite/id}]})

(def viittaukset (clojure.set/union turvalaitteet))

(def kaikki-kentat
  (clojure.set/union
    perustiedot
    viittaukset))

;; Palvelut

(s/def ::hakuteksti string?)

(s/def ::hae-vaylat-kysely
  (s/keys :op-un [::hakuteksti ::vaylatyyppi]))

(s/def ::hae-vaylat-vastaus
  (s/coll-of (s/keys :req [::id ::nimi ::tyyppi])))
