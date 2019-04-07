(ns harja.kyselyt.vesivaylat.tyot
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [harja.domain.toimenpidekoodi :as tpk]
            [taoensso.timbre :as log]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.tyo :as tyo]
            [specql.op :as op]
            [harja.domain.vesivaylat.hinta :as hinta]))

(defn hae-hinnoittelujen-tyot [db hinnoittelu-idt]
  (specql/fetch db
                ::tyo/tyo
                (set/union tyo/perustiedot tyo/viittaus-idt)
                {::tyo/hinnoittelu-id (op/in hinnoittelu-idt)
                 ::m/poistettu? false}))
