(ns harja.kyselyt.urakan-tyotunnit
  (:require
    [harja.domain.urakan-tyotunnit :as ut]
    [specql.core :refer [fetch upsert!]]
    [jeesql.core :refer [defqueries]]
    [specql.op :as op]))

(declare hae-urakat-joilla-puuttuu-kolmanneksen-tunnit)

(defqueries "harja/kyselyt/urakan_tyotunnit.sql")

(defn tallenna-urakan-tyotunnit [db tyotunnit]
  (upsert! db ::ut/urakan-tyotunnit #{::ut/urakka-id ::ut/vuosi ::ut/vuosikolmannes} tyotunnit))

(defn hae-urakan-tyotunnit [db hakuehdot]
  (fetch db ::ut/urakan-tyotunnit ut/kaikki-kentat hakuehdot))

(defn hae-urakan-vuosikolmanneksen-tyotunnit [db urakka-id vuosi vuosikolmannes]
  (::ut/tyotunnit
    (first (hae-urakan-tyotunnit
             db
             {::ut/urakka-id urakka-id
              ::ut/vuosi vuosi
              ::ut/vuosikolmannes vuosikolmannes}))))

(defn paivita-urakan-kuluvan-vuosikolmanneksen-tyotunnit [db urakka-id tunnit]
  (let [kolmannes (ut/kuluva-vuosikolmannes)
        arvot (merge {::ut/urakka-id urakka-id
                      ::ut/tyotunnit tunnit}
                     kolmannes)]
    (upsert! db ::ut/urakan-tyotunnit #{::ut/urakka-id ::ut/vuosi ::ut/vuosikolmannes} arvot)))

(defn hae-kuluvan-vuosikolmanneksen-tyotunnit [db urakka-id]
  (first (hae-urakan-tyotunnit db (merge {::ut/urakka-id urakka-id} (ut/kuluva-vuosikolmannes)))))

(defn hae-lahettamattomat-tai-epaonnistuneet-tyotunnit [db]
  (fetch db ::ut/urakan-tyotunnit ut/kaikki-kentat
         (op/or {::ut/lahetetty op/null?}
                {::ut/lahetys-onnistunut false})))
