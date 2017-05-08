(ns harja.kyselyt.urakan-tyotunnit
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [harja.domain.urakan-tyotunnit :as ut]
    [specql.core :refer [fetch update! insert! upsert!]]
    [specql.op :as op]
    [harja.pvm :as pvm]
    [harja.kyselyt.konversio :as konv]))

(def kaikki-kentat
  #{::ut/id
    ::ut/urakka-id
    ::ut/vuosi
    ::ut/vuosikolmannes
    ::ut/tyotunnit
    ::ut/lahetetty
    ::ut/lahetys-onnistunut})

(defn tallenna-urakan-tyotunnit [db tyotunnit]
  (insert! db ::ut/urakan-tyotunnit tyotunnit))

(defn hae-urakan-tyotunnit [db hakuehdot]
  (fetch db ::ut/urakan-tyotunnit kaikki-kentat hakuehdot))

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
                     kolmannes)
        ehdot (dissoc arvot ::ut/tyotunnit)]
    ;; todo: jostain syystä tämä tekee aina insertin
    (upsert! db ::ut/urakan-tyotunnit arvot ehdot)))

(defn hae-kuluvan-vuosikolmanneksen-tyotunnit [db urakka-id]
  (first (hae-urakan-tyotunnit db (merge {::ut/urakka-id urakka-id} (ut/kuluva-vuosikolmannes)))))

(defn hae-lahettamattomat-tai-epaonnistuneet-tyotunnit [db]
  (fetch db ::ut/urakan-tyotunnit-vuosikolmanneksittain kaikki-kentat
         (op/or {::ut/lahetetty op/null?}
                {::ut/lahetys-onnistunut false})))

(defn lokita-lahetys [db urakka-id vuosi vuosikolmannes onnistunut?]
  (update! db ::ut/urakan-tyotunnit
           {::ut/lahetys-onnistunut onnistunut?
            ::ut/lahetetty (konv/sql-timestamp (pvm/nyt))}
           {::ut/urakka urakka-id
            ::ut/vuosi vuosi
            ::ut/vuosikolmannes vuosikolmannes}))
