(ns harja.domain.paikkaus
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
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
  ["paikkauskohde" ::paikkauskohde
   {"luoja-id" ::muokkaustiedot/luoja-id
    ::paikkaukset (specql.rel/has-many ::id
                                       ::paikkaus
                                       ::paikkauskohde-id)
    ::kustannukset (specql.rel/has-many ::id
                                        ::paikkauskustannus
                                        ::paikkauskohde-id)}]
  ["paikkaus" ::paikkaus
   {"luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "muokattu" ::muokkaustiedot/muokattu
    "poistaja-id" ::muokkaustiedot/poistaja-id
    "poistettu" ::muokkaustiedot/poistettu?
    ::paikkauskohde (specql.rel/has-one ::paikkauskohde-id
                                        ::paikkauskohde
                                        ::id)
    ::tienkohdat (specql.rel/has-many ::id
                                      ::paikkauksen-tienkohta
                                      ::paikkaus-id)
    ::materiaalit (specql.rel/has-many ::id
                                       ::paikkauksen_materiaali
                                       ::paikkaus-id)}]
  ["paikkauksen_tienkohta" ::paikkauksen-tienkohta]
  ["paikkauksen_materiaali" ::paikkauksen_materiaali]
  ["paikkaustoteuma" ::paikkaustoteuma
   {"luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "muokattu" ::muokkaustiedot/muokattu
    "poistaja-id" ::muokkaustiedot/poistaja-id
    "poistettu" ::muokkaustiedot/poistettu?}])

(def paikkauskohteen-perustiedot
  #{::id
    ::ulkoinen-id
    ::nimi})

(def paikkauksen-perustiedot
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

(def paikkaustoteuman-perustiedot
  #{::id
    ::urakka-id
    ::paikkauskohde-id
    ::ulkoinen-id
    ::toteuma-id
    ::kirjattu
    ::tyyppi
    ::selite
    ::hinta
    ::yksikko
    ::yksikkohinta
    ::maara})

(s/def ::aikavali (s/coll-of any? :kind? vector :count 2))
(s/def ::paikkaus-nimet (s/coll-of string? :kind set?))
(s/def ::tr map?)

(s/def ::urakan-paikkauskohteet-kysely (s/keys :req [::urakka-id]
                                               :opt-un [::aikavali ::paikkaus-nimet ::tr]))

(s/def ::urakan-paikkauskohteet-vastaus any?)