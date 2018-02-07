(ns harja.domain.paikkaus
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.muokkaustiedot :as muokkautiedot]
    [harja.kyselyt.specql :as harja-specql]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:clj
     (:import (org.postgis PGgeometry))))

(define-tables
  ["paikkauskohde" ::paikkauskohde]
  ["paikkaustoteuma" ::paikkaustoteuma
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::paikkauskohde (specql.rel/has-one ::paikkauskohde-id
                                        ::paikkauskohde
                                        ::id)
    ::tienkohdat (specql.rel/has-many ::id
                                      ::paikkauksen-tienkohta
                                      ::paikkaustoteuma-id)
    ::materiaalit (specql.rel/has-many ::id
                                       ::paikkauksen-materiaalit
                                       ::paikkaustoteuma-id)}]
  ["paikkauksen_tienkohta" ::paikkauksen-tienkohta]
  ["paikkauksen_materiaalit" ::paikkauksen-materiaalit])

(def paikkauskohteen-perustiedot
  #{::id
    ::ulkoinen-id
    ::nimi})

(def paikkaustoteuman-perustiedot
  #{::id
    ::urakka-id
    ::paikkauskohde-id
    ::ulkoinen-id
    ::alkuaika
    ::loppuaika
    ::tierekisteriosoite
    ::tyomenetelma
    ::massatyyppi
    ::leveys
    ::massamenekki
    ::raekoko
    ::kuulamylly
    [::paikkauskohde #{::nimi
                       ::ulkoinen-id}]
    [::tienkohdat #{::ajorata
                    ::reunat
                    ::ajourat
                    ::ajouravalit
                    ::keskisaumat}]
    [::materiaalit #{::esiintyma
                     ::kuulamylly-arvo
                     ::muotoarvo
                     ::sideainetyyppi
                     ::pitoisuus
                     ::lisa-aineet}]})