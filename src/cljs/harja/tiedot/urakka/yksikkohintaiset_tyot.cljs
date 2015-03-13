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
            :tyot (map #(pvm/muunna-aika-js % :alkupvm :loppupvm) tyot)
            }
           ))
