(ns harja.domain.kanavat.kanavan-toimenpide
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.kanavat.kanavan-kohde :as kohde]
    ;; [harja.domain.kanavat.hinta :as hinta]
    ;; [harja.domain.kanavat.tyo :as tyo]
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
  ;; ["kan_toimenpide_hinta" ::toimenpide->hinta]
  ["kan_toimenpide" ::kanava-toimenpide
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {"urakka" ::urakka-id
    "sopimus" ::sopimus-id
    "muu_toimenpide" ::muu-toimenpide
    ;; ::hinnat (specql.rel/has-many ::id ::tp-hinta/hinta ::tp-hinta/toimenpide)
    "toimenpideinstanssi" ::toimenpideinstanssi-id
    "kohde" ::kohde-id
    ::kohde (specql.rel/has-one ::kohde-id
                                :harja.domain.kanavat.kanavan-kohde/kohde
                                :harja.domain.kanavat.kanavan-kohde/id)
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

(def viittaus-idt #{::urakka-id ::sopimus-id ::kohde-id ::toimenpidekoodi-id ::kuittaaja-id})


(def muokkaustiedot
  #{::muokkaustiedot/luoja-id
    ::muokkaustiedot/luotu
    ::muokkaustiedot/muokkaaja-id
    ::muokkaustiedot/muokattu
    ::muokkaustiedot/poistaja-id
    ::muokkaustiedot/poistettu?})

(def kohteen-tiedot
  #{[::kohde
     #{::kohde/id
       ::kohde/nimi
       ::kohde/tyyppi
       ::kohde/sijainti}]})

(def huoltokohteen-tiedot
  #{[::huoltokohde
     #{::huoltokohde/id
       ::huoltokohde/nimi}]})

(def toimenpiteen-tiedot
  #{[::toimenpidekoodi
     #{::toimenpidekoodi/id
       ::toimenpidekoodi/nimi}]})

(def kuittaajan-tiedot
  #{[::kuittaaja
     #{::kayttaja/id
       ::kayttaja/etunimi
       ::kayttaja/sukunimi
       ::kayttaja/kayttajanimi
       ::kayttaja/sahkoposti
       ::kayttaja/puhelin}]})

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

(s/def ::tallenna-kanavatoimenpiteen-hinnoittelu-kysely
  (s/keys
   :req [::urakka-id
         ::id
         :harja.domain.kanavat.hinta/tallennettavat-hinnat
         :harja.domain.kanavat.tyo/tallennettavat-tyot]))

(s/def ::tallenna-kanavatoimenpiteen-hinnoittelu-vastaus
  ::kanava-toimenpide)

(s/def ::tallenna-kanavatoimenpide-kutsu
  (s/keys :req [::hae-kanavatoimenpiteet-kysely
                ::kanava-toimenpide]))
