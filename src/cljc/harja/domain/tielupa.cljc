(ns harja.domain.tielupa
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.kanavat.kohde :as kohde]
    [harja.domain.kanavat.kohteenosa :as osa]
    [harja.domain.kanavat.hinta :as hinta]
    [harja.domain.kanavat.tyo :as tyo]
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
