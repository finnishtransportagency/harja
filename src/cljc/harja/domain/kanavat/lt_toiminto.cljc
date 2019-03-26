(ns harja.domain.kanavat.lt-toiminto
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])

    [harja.domain.kanavat.kohde :as kohde]
    [harja.domain.kanavat.kohteenosa :as kohteenosa]
    [harja.domain.muokkaustiedot :as m])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["liikennetapahtuma_toimenpidetyyppi" ::lt-toimenpidetyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["liikennetapahtuma_palvelumuoto" ::kohteenosa/osan-palvelumuoto (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_liikennetapahtuma_toiminto" ::liikennetapahtuman-toiminto
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::liikennetapahtuma (specql.rel/has-one ::liikennetapahtuma-id
                                            :harja.domain.kanavat.liikennetapahtuma/liikennetapahtuma
                                            :harja.domain.kanavat.liikennetapahtuma/id)
    ::kohde (specql.rel/has-one ::kohde-id
                                :harja.domain.kanavat.kohde/kohde
                                :harja.domain.kanavat.kohde/id)
    ::kohteenosa (specql.rel/has-one ::kohteenosa-id
                                     :harja.domain.kanavat.kohteenosa/kohteenosa
                                     :harja.domain.kanavat.kohteenosa/id)}])

(def perustiedot
  #{::id
    ::toimenpide
    ::palvelumuoto
    ::kohde-id
    ::kohteenosa-id
    ::lkm})

(def kohteen-tiedot
  #{[::kohde kohde/perustiedot]})

(def kohteenosan-tiedot
  #{[::kohteenosa kohteenosa/perustiedot]})

(def metatiedot m/muokkauskentat)

(s/def ::toimenpiteet (s/coll-of #{:sulutus :tyhjennys :avaus :ei-avausta}))
