(ns harja.domain.kanavat.kanavan-toimenpide
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.kanavat.kohde :as kohde]
    [harja.domain.kanavat.kohteenosa :as osa]
    [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
    [harja.domain.toimenpidekoodi :as toimenpidekoodi]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.domain.kayttaja :as kayttaja]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]])
    [clojure.set :as set])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_toimenpidetyyppi" ::kanava-toimenpidetyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_toimenpide" ::kanava-toimenpide
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {"urakka" ::urakka-id
    "sopimus" ::sopimus-id
    "toimenpideinstanssi" ::toimenpideinstanssi-id
    "muu_toimenpide" ::muu-toimenpide

    ::kohde (specql.rel/has-one ::kohde-id
                                :harja.domain.kanavat.kohde/kohde
                                :harja.domain.kanavat.kohde/id)

    ::kohteenosa (specql.rel/has-one ::kohteenosa-id
                                     :harja.domain.kanavat.kohteenosa/kohteenosa
                                     :harja.domain.kanavat.kohteenosa/id)

    "huoltokohde" ::huoltokohde-id
    ::huoltokohde (specql.rel/has-one ::huoltokohde-id
                                      :harja.domain.kanavat.kanavan-huoltokohde/huoltokohde
                                      :harja.domain.kanavat.kanavan-huoltokohde/id)

    "toimenpidekoodi" ::toimenpidekoodi-id
    ::toimenpidekoodi (specql.rel/has-one ::toimenpidekoodi-id
                                          :harja.domain.toimenpidekoodi/toimenpidekoodi
                                          :harja.domain.toimenpidekoodi/id)
    "kuittaaja" ::kuittaaja-id
    ::kuittaaja (specql.rel/has-one ::kuittaaja-id
                                    :harja.domain.kayttaja/kayttaja
                                    :harja.domain.kayttaja/id)}])

(def muokkaustiedot
  #{::muokkaustiedot/luoja-id
    ::muokkaustiedot/luotu
    ::muokkaustiedot/muokkaaja-id
    ::muokkaustiedot/muokattu
    ::muokkaustiedot/poistaja-id
    ::muokkaustiedot/poistettu?})

(def kohteen-tiedot
  #{[::kohde kohde/perustiedot]})

(def kohteenosan-tiedot
  #{[::kohteenosa osa/perustiedot]})

(def huoltokohteen-tiedot
  #{[::huoltokohde huoltokohde/perustiedot]})

(def toimenpiteen-tiedot
  #{[::toimenpidekoodi toimenpidekoodi/perustiedot]})

(def kuittaajan-tiedot
  #{[::kuittaaja kayttaja/perustiedot]})

(def perustiedot
  #{::id
    ::tyyppi
    ::pvm
    ::muu-toimenpide
    ::lisatieto
    ::suorittaja
    ::sopimus-id
    ::toimenpideinstanssi-id})

(def perustiedot-viittauksineen
  (set/union perustiedot
             muokkaustiedot
             kohteen-tiedot
             kohteenosan-tiedot
             huoltokohteen-tiedot
             toimenpiteen-tiedot
             kuittaajan-tiedot))

(s/def ::hae-kanavatoimenpiteet-kysely
  (s/keys :req [::urakka-id
                ::sopimus-id
                ::kanava-toimenpidetyyppi
                ::toimenpidekoodi/id]
          :req-un [::alkupvm
                   ::loppupvm]))

(s/def ::hae-kanavatoimenpiteet-vastaus
  (s/coll-of ::kanava-toimenpide))

(s/def ::tallenna-kanavatoimenpide-kutsu
  (s/keys :req [::hae-kanavatoimenpiteet-kysely
                ::kanava-toimenpide]))