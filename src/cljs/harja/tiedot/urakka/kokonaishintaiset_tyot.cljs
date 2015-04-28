(ns harja.tiedot.urakka.kokonaishintaiset-tyot
  "Tämä nimiavaruus hallinnoi urakan yksikköhintaisia töitä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.urakka.suunnittelu :as s]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn aseta-hoitokausi [hoitokaudet rivi]
  (let [p (pvm/luo-pvm (:vuosi rivi) (dec (:kuukausi rivi)) 1)]
    (loop [[hk & hoitokaudet] hoitokaudet]
      (if (nil? hk)
        ;; Ei löytynyt hoitokautta, palautetaan rivi
        (do (log "kokonaishintaiselle työlle ei löytynyt hoitokautta: " (pr-str rivi))
            rivi)
        
        (let [[alkupvm loppupvm] hk]
          ;;(log "TESTAA ONKO " (pr-str p) " HOITOKAUDESSA " (pr-str hk) "? " (and (or (pvm/sama-pvm? alkupvm p)
          ;;                                                                           (pvm/jalkeen? p alkupvm))
          ;;                                                                       (or (pvm/sama-pvm? loppupvm p)
          ;;                                                                           (pvm/ennen? p loppupvm))))
          (if (and (or (pvm/sama-pvm? alkupvm p)
                       (pvm/jalkeen? p alkupvm))
                   (or (pvm/sama-pvm? loppupvm p)
                       (pvm/ennen? p loppupvm)))
            (assoc rivi
              :alkupvm alkupvm
              :loppupvm loppupvm)

            (recur hoitokaudet)))))))


(defn hae-urakan-kokonaishintaiset-tyot [{:keys [tyyppi id] :as ur}]
  (go (let [res (<! (k/post! :kokonaishintaiset-tyot id))
            hoitokaudet (s/hoitokaudet ur)]
        (map #(aseta-hoitokausi hoitokaudet %) res))))


(defn tallenna-kokonaishintaiset-tyot
  "Tallentaa urakan yksikköhintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id sopimusnumero tyot]
  (log "tallenna-urakan-kokonaishintaiset-tyot, urakka: " urakka-id "sopimus: " (first sopimusnumero))
  (log "työt" tyot)

  (k/post! :tallenna-kokonaishintaiset-tyot
           {:urakka-id urakka-id
            :sopimusnumero (first sopimusnumero)
            :tyot (into [] tyot)}))
