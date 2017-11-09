(ns harja.domain.kanavat.kanavan-toimenpide
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.kanavat.kanavan-kohde :as kohde]
    [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
    [harja.domain.toimenpidekoodi :as toimenpidekoodi]
    [harja.domain.kayttaja :as kayttaja]
    [harja.domain.sopimus :as sopimus]
    [harja.domain.urakka :as urakka]

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
    "suorittaja" ::suorittaja-id
    ::suorittaja (specql.rel/has-one ::suorittaja-id
                                     :harja.domain.kayttaja/kayttaja
                                     :harja.domain.kayttaja/id)
    "kuittaaja" ::kuittaaja-id
    ::kuittaaja (specql.rel/has-one ::kuittaaja-id
                                    :harja.domain.kayttaja/kayttaja
                                    :harja.domain.kayttaja/id)}])

(def kaikki-kentat
  #{::id
    ::tyyppi
    ::pvm
    ::lisatieto
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
    [::suorittaja
     #{::kayttaja/id
       ::kayttaja/etunimi
       ::kayttaja/sukunimi
       ::kayttaja/kayttajanimi
       ::kayttaja/sahkoposti
       ::kayttaja/puhelin}]
    [::kuittaaja
     #{::kayttaja/id
       ::kayttaja/etunimi
       ::kayttaja/sukunimi
       ::kayttaja/kayttajanimi
       ::kayttaja/sahkoposti
       ::kayttaja/puhelin}]})

(s/def ::hae-kanavatoimenpiteet-kutsu
  (s/keys :req [::urakka/id
                ::sopimus/id
                ::toimenpidekoodi/id
                ::alkupvm
                ::loppupvm]
          :opt [::kanava-toimenpidetyyppi]))

(s/def ::hae-kanavatoimenpiteet-vastaus
  (s/coll-of ::kanava-toimenpide))