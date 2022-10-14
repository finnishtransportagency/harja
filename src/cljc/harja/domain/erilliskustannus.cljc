(ns harja.domain.erilliskustannus
  "Erilliskustannuksiin (lähinnä bonuksiin) liittyvien asioiden domain määritykset:"
  (:require [clojure.spec.alpha :as s]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.organisaatio :as o]
            #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]]
                :cljs [[specql.impl.registry]]))
  #?(:cljs (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["erilliskustannus" ::erilliskustannus
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistettu?-sarake])
