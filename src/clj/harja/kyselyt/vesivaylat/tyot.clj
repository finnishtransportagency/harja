(ns harja.kyselyt.vesivaylat.tyot
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [harja.domain.toimenpidekoodi :as tpk]
            [taoensso.timbre :as log]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.tyo :as tyo]))

(defn- hae-hinnoittelun-tyot [db hinnoittelu-d]
  (specql/fetch db
                ::tyo/tyo
                tyo/perustiedot
                {::tyo/hinnoittelu-id hinnoittelu-d
                 ::m/poistettu? false}))