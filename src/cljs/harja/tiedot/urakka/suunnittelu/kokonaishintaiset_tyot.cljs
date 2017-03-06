(ns harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot
  "Tämä nimiavaruus hallinnoi urakan kokonaishintaisia töitä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn aseta-hoitokausi [hoitokaudet rivi]
  (let [p (pvm/luo-pvm (:vuosi rivi) (dec (:kuukausi rivi)) 1)]
    (loop [[hk & hoitokaudet] hoitokaudet]
      (if (nil? hk)
        ;; Ei löytynyt hoitokautta, palautetaan rivi
        (do (log "kokonaishintaiselle työlle ei löytynyt hoitokautta: " (pr-str rivi))
            nil)

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
            hoitokaudet (u/hoitokaudet ur)]
        (keep #(aseta-hoitokausi hoitokaudet %) res))))


(defn tallenna-kokonaishintaiset-tyot
  "Tallentaa urakan yksikköhintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka sopimusnumero tyot]
  (k/post! :tallenna-kokonaishintaiset-tyot
           {:urakka        urakka
            :sopimusnumero (first sopimusnumero)
            :tyot          (into [] tyot)}))
