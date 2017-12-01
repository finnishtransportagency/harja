(ns harja.domain.kanavat.lt-ketjutus
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m]
    [harja.domain.kanavat.lt-alus :as alus])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["liikennetapahtuma_suunta" ::alus/aluksen-suunta (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_liikennetapahtuma_ketjutus" ::liikennetapahtuman-ketjutus
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::kuitattu-tapahtumaan (specql.rel/has-one ::kuitattu-id
                                               :harja.domain.kanavat.liikennetapahtuma/liikennetapahtuma
                                               :harja.domain.kanavat.liikennetapahtuma/id)
    ::alus (specql.rel/has-one ::alus-id
                               :harja.domain.kanavat.lt-alus/liikennetapahtuman-alus
                               :harja.domain.kanavat.lt-alus/id)}])

(def perustiedot
  #{::id
    ::suunta
    ::aika

    ::kohde-id
    ::urakka-id
    ::sopimus-id
    ::kuitattu-id
    ::alus-id})

(def aluksen-tiedot
  #{[::alus alus/perustiedot]})

(def metatiedot m/muokkauskentat)
