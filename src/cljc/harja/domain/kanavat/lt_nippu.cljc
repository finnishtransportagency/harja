(ns harja.domain.kanavat.lt-nippu
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
    [harja.domain.kanavat.kanavan-kohde :as kohde]
    [harja.domain.urakka :as ur])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["liikennetapahtuma_suunta" ::lt-suunta (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_liikennetapahtuma_nippu" ::liikennetapahtuman-nippu
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::liikennetapahtuma (specql.rel/has-one ::liikennetapahtuma-id
                                            :harja.domain.kanavat.liikennetapahtuma/liikennetapahtuma
                                            :harja.domain.kanavat.liikennetapahtuma/id)}])

(def perustiedot
  #{::id
    ::lkm
    ::suunta})



