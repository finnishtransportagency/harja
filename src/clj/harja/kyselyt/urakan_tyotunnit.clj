(ns harja.kyselyt.urakan-tyotunnit
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [harja.domain.urakan-tyotunnit :as ut]
    [specql.core :refer [fetch update! insert!]]
    [specql.op :as op]
    [harja.pvm :as pvm]
    [harja.kyselyt.konversio :as konv]))

(def kaikki-kentat
  #{::ut/id
    ::ut/urakka
    ::ut/vuosi
    ::ut/vuosikolmannes
    ::ut/tyotunnit
    ::ut/lahetetty
    ::ut/lahetys-onnistunut})

(defn tallenna-urakan-tyotunnit [db tyotunnit]
  (insert! db ::ut/urakan-tyotunnit-vuosikolmanneksella tyotunnit))

(defn hae-urakan-tyotunnit [db hakuehdot]
  (fetch db ::ut/urakan-tyotunnit kaikki-kentat hakuehdot))

(defn hae-urakan-vuosikolmanneksen-tyotunnit [db urakka-id vuosi vuosikolmannes]
  (::ut/tyotunnit
    (first (hae-urakan-tyotunnit db
                                 {::urakan-tyotunnit/urakka urakka-id
                                  ::urakan-tyotunnit/vuosi vuosi
                                  ::urakan-tyotunnit/vuosikolmannes vuosikolmannes}))))


(defn hae-lahettamattomat-tai-epaonnistuneet-tyotunnit [db]
  (fetch db ::ut/urakan-tyotunnit kaikki-kentat
         (op/or {::ut/lahetetty op/null?}
                {::ut/lahetys-onnistunut false})))

(defn lokita-lahetys [db urakka-id vuosi vuosikolmannes onnistunut?]
  (update! db ::ut/urakan-tyotunnit
           {::ut/lahetys-onnistunut onnistunut?
            ::ut/lahetetty (konv/sql-timestamp (pvm/nyt))}
           {::ut/urakka urakka-id
            ::ut/vuosi vuosi
            ::ut/vuosikolmannes vuosikolmannes}))
