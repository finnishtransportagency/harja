(ns harja.tiedot.urakka.yksikkohintaiset-tyot
  "Tämä nimiavaruus hallinnoi urakan yksikköhintaisia töitä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-yksikkohintaiset-tyot [urakka-id]
  (k/post! :yksikkohintaiset-tyot urakka-id
           (map #(pvm/muunna-aika % :alkupvm :loppupvm))))

(defn map-hoitokauden-vuodet [tyot hoitokausi]

  )

(defn hoitokauden-vuodet [tyot hoitokausi]
  ;; luodaan yhdestä rivistä kaksi riviä, hoitokauden molempien vuosien osat
  (mapcat (let [alkupvm-10-12 (pvm/goog->js (pvm/luo-pvm (.getFullYear (pvm/goog->js (:alkupvm hoitokausi))) 9 1)) ;;1.10.yyyy
                loppupvm-10-12 (pvm/goog->js (pvm/luo-pvm (.getFullYear (pvm/goog->js (:alkupvm hoitokausi))) 11 31)) ;;31.12.yyyy
                alkupvm-1-9 (pvm/goog->js (pvm/luo-pvm (.getFullYear (pvm/goog->js (:loppupvm hoitokausi))) 0 1))
                loppupvm-1-9 (pvm/goog->js (pvm/luo-pvm (.getFullYear (pvm/goog->js (:loppupvm hoitokausi))) 8 30))
                ]
            (fn [rivi]
              [(dissoc (assoc rivi :alkupvm alkupvm-10-12 :loppupvm loppupvm-10-12 :maara (:maara-kkt-10-12 rivi)) :maara-kkt-1-9 :maara-kkt-10-12)
               (dissoc (assoc rivi :alkupvm alkupvm-1-9 :loppupvm loppupvm-1-9 :maara (:maara-kkt-1-9 rivi)) :maara-kkt-1-9 :maara-kkt-10-12)
               ])) tyot))

(defn tallenna-urakan-yksikkohintaiset-tyot
  "Tallentaa urakan yksikköhintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id sopimusnumero hoitokausi tyot]
  (log "tallenna-urakan-yksikkohintaiset-tyot" urakka-id (first sopimusnumero)
        (pvm/goog->js (:alkupvm hoitokausi))  (pvm/goog->js (:loppupvm hoitokausi)))
  (log "työt" tyot)
  
  (k/post! :tallenna-urakan-yksikkohintaiset-tyot 
           {:urakka-id urakka-id
            :sopimusnumero (first sopimusnumero)
            :hoitokausi-alkupvm  (pvm/goog->js (:alkupvm hoitokausi)) 
            :hoitokausi-loppupvm (pvm/goog->js (:loppupvm hoitokausi))
            :tyot (into [] (hoitokauden-vuodet tyot hoitokausi))
            }
           ))
