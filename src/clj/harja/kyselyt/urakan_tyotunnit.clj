(ns harja.kyselyt.urakan-tyotunnit
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [harja.domain.specql-db :refer [db]]
    [harja.domain.urakan-tyotunnit :as ut]
    [specql.core :refer [fetch]]
    [specql.op :as op]))

(def kaikki-kentat
  #{::ut/id
    ::ut/vuosi
    ::ut/vuosikolmannes
    ::ut/tyotunnit})

(defn hae-urakan-tyotunnit [db urakka-id]
  (fetch db ::ut/urakan-tyotunnit kaikki-kentat
         (::ut/urakka urakka-id)))

(defn hae-urakan-vuosikolmanneksen-tyotunnit [db urakka-id vuosi vuosikolmannes]
  (fetch db ::ut/urakan-tyotunnit #{::ut/tyotunnit}
         (op/and
           {::ut/urakka urakka-id
            ::ut/vuosi vuosi
            ::ut/vuosikolmannes vuosikolmannes})))