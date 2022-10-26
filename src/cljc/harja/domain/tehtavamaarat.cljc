(ns harja.domain.tehtavamaarat
  (:require [clojure.spec.alpha :as s]
            #?(:clj [harja.kyselyt.specql-db :refer [define-tables]])
            [harja.domain.urakka :as urakka]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["sopimus_tehtavamaara" ::sopimus-tehtavamaara
   {"id" ::sopimus-tehtavamaara-id
    "urakka" ::urakka/id
    "tehtava" ::toimenpidekoodi/id
    "maara" ::maara
    "hoitovuosi" ::hoitovuosi
    "muokattu" ::muokkaustiedot/muokattu
    "muokkaaja" ::muokkaustiedot/muokkaaja-id}])

(define-tables 
  ["sopimuksen_tehtavamaarat_tallennettu" ::sopimuksen-tehtavamaarat-tallennettu
   {"id" ::sopimuksen-tehtavamaara-tilan-id
    "urakka" ::urakka/id
    "tallennettu" ::tallennettu}])
