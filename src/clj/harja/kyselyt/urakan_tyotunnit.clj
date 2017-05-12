(ns harja.kyselyt.urakan-tyotunnit
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [harja.domain.urakan-tyotunnit :as ut]
    [specql.core :refer [fetch update! insert! upsert!]]
    [specql.op :as op]
    [harja.pvm :as pvm]
    [harja.kyselyt.konversio :as konv]))

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
  (fetch db ::ut/urakan-tyotunnit-vuosikolmanneksittain ut/kaikki-kentat
         (op/or {::ut/lahetetty op/null?}
                {::ut/lahetys-onnistunut false})))

(defn lokita-lahetys [db urakka-id vuosi vuosikolmannes onnistunut?]
  (update! db ::ut/urakan-tyotunnit
           {::ut/lahetys-onnistunut onnistunut?
            ::ut/lahetetty (konv/sql-timestamp (pvm/nyt))}
           {::ut/urakka-id urakka-id
            ::ut/vuosi vuosi
            ::ut/vuosikolmannes vuosikolmannes}))
