(ns harja.kyselyt.vesivaylat.kiintiot
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! upsert!]]
            [specql.op :as op]
            [specql.rel :as rel]
            [taoensso.timbre :as log]

            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.kiintio :as kiintio]))

(defn hae-kiintiot [db tiedot]
  (let [urakka-id (::kiintio/urakka-id tiedot)]
    (into
      []
      (comp
        (map #(assoc % ::kiintio/toimenpiteet (into []
                                                    harja.kyselyt.vesivaylat.toimenpiteet/toimenpiteet-xf
                                                    (::kiintio/toimenpiteet %)))))
      (fetch db
            ::kiintio/kiintio
            (set/union kiintio/perustiedot
                       kiintio/kiintion-toimenpiteet)
            (op/and
              {::kiintio/urakka-id urakka-id}
              {::m/poistettu? false})))))