(ns harja.domain.kanavat.kanavan-toimenpide
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.kanavat.kanavan-kohde :as kohde]
    [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
    [harja.domain.toimenpidekoodi :as toimenpidekoodi]
    [harja.domain.kayttaja :as kayttaja]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]]))
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
    "muu_toimenpide" ::muu-toimenpide
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

(def kaikki-kentat
  #{::id
    ::tyyppi
    ::pvm
    ::muu-toimenpide
    ::lisatieto
    ::suorittaja
    ::sopimus-id
    [::kohde
     #{::kohde/id
       ::kohde/nimi
       ::kohde/tyyppi
       ::kohde/sijainti}]
    [::huoltokohde
     #{::huoltokohde/id
       ::huoltokohde/nimi}]
    [::toimenpidekoodi
     #{::toimenpidekoodi/id
       ::toimenpidekoodi/nimi}]
    [::kuittaaja
     #{::kayttaja/id
       ::kayttaja/etunimi
       ::kayttaja/sukunimi
       ::kayttaja/kayttajanimi
       ::kayttaja/sahkoposti
       ::kayttaja/puhelin}]})

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