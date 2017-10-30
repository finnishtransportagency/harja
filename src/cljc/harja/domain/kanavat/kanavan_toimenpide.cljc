(ns harja.domain.kanavat.kanavan-toimenpide
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set :as set]
    [specql.rel :as rel]

    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["kan_toimenpidetyyppi" ::kanava-toimenpidetyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_toimenpide" ::kanava-toimenpiteet
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