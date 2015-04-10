(ns harja.tiedot.urakka.kokonaishintaiset-tyot
  "Tämä nimiavaruus hallinnoi urakan yksikköhintaisia töitä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn aseta-hoitokausi [rivi]
  (let [alkupvm (if (<= 10 (:kuukausi rivi) 12)
                  (pvm/hoitokauden-alkupvm (:vuosi rivi))
                  (pvm/hoitokauden-alkupvm (dec (:vuosi rivi))))
        loppupvm (if (<= 10 (:kuukausi rivi) 12)
                   (pvm/hoitokauden-loppupvm (inc (:vuosi rivi)))
                   (pvm/hoitokauden-loppupvm (:vuosi rivi)))
        ]
    ;; lisätään kaikkiin riveihin valittu hoitokausi
    (assoc rivi :alkupvm alkupvm :loppupvm loppupvm)))


(defn hae-urakan-kokonaishintaiset-tyot [urakka-id]
  (go (let [res (<! (k/post! :kokonaishintaiset-tyot urakka-id))]
     (map #(aseta-hoitokausi %) res))))


(defn tallenna-kokonaishintaiset-tyot
  "Tallentaa urakan yksikköhintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id sopimusnumero tyot]
  (log "tallenna-urakan-kokonaishintaiset-tyot, urakka: " urakka-id "sopimus: " (first sopimusnumero))
  (log "työt" tyot)

  (k/post! :tallenna-kokonaishintaiset-tyot
           {:urakka-id urakka-id
            :sopimusnumero (first sopimusnumero)
            :tyot (into [] tyot)}))
