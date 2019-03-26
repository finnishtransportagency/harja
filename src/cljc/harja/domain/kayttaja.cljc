(ns harja.domain.kayttaja
  (:require [clojure.spec.alpha :as s]
            [harja.domain.organisaatio :as o]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.organisaatio :as o]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            ])
    #?(:clj
            [specql.rel :as rel]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kayttaja" ::kayttaja
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {"organisaatio" ::organisaatio-id
    ::organisaatio (specql.rel/has-one ::organisaatio-id
                                       :harja.domain.organisaatio/organisaatio
                                       :harja.domain.organisaatio/id)}])

(def perustiedot
  #{::id
    ::kayttajanimi
    ::etunimi
    ::sukunimi
    ::sahkoposti
    ::puhelin})

(def kayttajan-organisaatio
  #{[::organisaatio o/urakoitsijan-perustiedot]})

(defn kokonimi [kayttaja]
  (str (::etunimi kayttaja) " " (::sukunimi kayttaja)))

(defn kayttaja->str [k]
  (str (::etunimi k) " " (::sukunimi k)))
