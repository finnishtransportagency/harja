(ns harja.domain.kanavat.lt-ketjutus
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set :as set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m]
    [harja.domain.kanavat.lt-alus :as alus]
    [harja.domain.kanavat.kohde :as kohde])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["liikennetapahtuma_suunta" ::alus/aluksen-suunta (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_liikennetapahtuma_ketjutus" ::liikennetapahtuman-ketjutus
   {::kuitattu-tapahtumaan (specql.rel/has-one ::tapahtumaan-id
                                               :harja.domain.kanavat.liikennetapahtuma/liikennetapahtuma
                                               :harja.domain.kanavat.liikennetapahtuma/id)
    ::tapahtumasta (specql.rel/has-one ::tapahtumasta-id
                                       :harja.domain.kanavat.liikennetapahtuma/liikennetapahtuma
                                       :harja.domain.kanavat.liikennetapahtuma/id)
    ::alus (specql.rel/has-one ::alus-id
                               :harja.domain.kanavat.lt-alus/liikennetapahtuman-alus
                               :harja.domain.kanavat.lt-alus/id)
    ::kohteelta (specql.rel/has-one ::kohteelta-id
                                    :harja.domain.kanavat.kohde/kohde
                                    :harja.domain.kanavat.kohde/id)
    ::kohteelle (specql.rel/has-one ::kohteelle-id
                                :harja.domain.kanavat.kohde/kohde
                                :harja.domain.kanavat.kohde/id)}])

(def perustiedot
  #{::kohteelta-id
    ::kohteelle-id
    ::urakka-id
    ::sopimus-id
    ::tapahtumaan-id
    ::alus-id})

(def aluksen-tiedot
  #{[::alus alus/perustiedot]})

(def kohteelle-tiedot
  #{[::kohteelle kohde/perustiedot]})

(def kohteelta-tiedot
  #{[::kohteelta kohde/perustiedot]})

(def tapahtumasta-tiedot
  #{[::tapahtumasta #{:harja.domain.kanavat.liikennetapahtuma/lisatieto
                      :harja.domain.kanavat.liikennetapahtuma/aika}]})

(def metatiedot m/muokkauskentat)
