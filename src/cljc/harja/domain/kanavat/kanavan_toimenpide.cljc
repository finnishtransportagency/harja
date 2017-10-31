(ns harja.domain.kanavat.kanavan-toimenpide
  (:require
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
   {::kohde (specql.rel/has-one ::kohde
                                :harja.domain.kanavat.kanavan-kohde/kohde
                                :harja.domain.kanavat.kanavan-kohde/id)
    ::huoltokohde (specql.rel/has-one ::huoltokohde
                                      :harja.domain.kanavat.kanavan-huoltokohde/huoltokohde
                                      :harja.domain.kanavat.kanavan-huoltokohde/id)
    ::toimenpidekoodi (specql.rel/has-one ::toimenpidekoodi
                                          :harja.domain.toimenpidekoodi/toimenpidekoodi
                                          :harja.domain.toimenpidekoodi/id)
    ::suorittaja (specql.rel/has-one ::suorittaja
                                     :harja.domain.kayttaja/kayttaja
                                     :harja.domain.kayttaja/id)
    ::kuittaaja (specql.rel/has-one ::kuittaaja
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