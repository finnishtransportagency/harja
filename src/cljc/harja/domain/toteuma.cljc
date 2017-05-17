(ns harja.domain.toteuma
  "Toteumaan liittyvien asioiden domain määritykset:
  toteuman eri tyypit, toteuman reittipisteet."
  (:require [clojure.spec.alpha :as s]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.organisaatio :as o]
            #?(:clj [harja.kyselyt.specql-db :refer [define-tables]])
            #?(:clj [clojure.future :refer :all])
            #?(:cljs [specql.impl.registry]))
  #?(:cljs (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["toteuma" ::toteuma
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistettu?-sarake
   {"urakka" ::urakka-id
    "sopimus" ::sopimus-id
    "suorittajan_ytunnus" ::suorittajan-ytunnus
    "suorittajan_nimi" ::suorittajan-nimi
    "ulkoinen_id " ::ulkoinen-id
    "tr_numero" ::tr/numero
    "tr_alkuosa" ::tr/alkuosa
    "tr_alkuetaisyys" ::tr/alkuetaisyys
    "tr_loppuosa" ::tr/loppuosa
    "tr_loppuetaisyys" ::tr/loppuetaisyys}])
