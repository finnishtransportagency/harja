(ns harja.domain.toteuma
  "Toteumaan liittyvien asioiden domain määritykset:
  toteuman eri tyypit, toteuman reittipisteet."
  (:require [clojure.spec.alpha :as s]
            [harja.domain.muokkaustiedot :as m]
            #?(:clj [harja.kyselyt.specql-db :refer [define-tables]])
            #?(:clj [clojure.future :refer :all])
            #?(:cljs [specql.impl.registry]))
  #?(:cljs (:require-macros [harja.kyselyt.specql-db :refer [define-tables]]
                            )))

(define-tables
  ["toteuma" ::toteuma
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistettu?-sarake])
