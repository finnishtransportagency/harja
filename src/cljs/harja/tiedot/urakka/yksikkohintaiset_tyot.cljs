(ns harja.tiedot.urakka.yksikkohintaiset-tyot
  "Tämä nimiavaruus hallinnoi urakan yksikköhintaisia töitä."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.suunnittelu :as s])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-yksikkohintaiset-tyot [urakka-id]
  (k/post! :yksikkohintaiset-tyot urakka-id))


(defn tallenna-urakan-yksikkohintaiset-tyot
  "Tallentaa urakan yksikköhintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [{:keys [tyyppi id]} sopimusnumero tyot]
  (log "tallenna-urakan-yksikkohintaiset-tyot, urakka: " id "sopimus: " (first sopimusnumero))
  (log "työt" (pr-str tyot))
  (let [hyotykuorma {:urakka-id id
                     :sopimusnumero (first sopimusnumero)
                     :tyot (if (= :hoito tyyppi)
                             (into [] (s/jaa-rivien-hoitokaudet tyot
                                                                #(-> %
                                                                     (assoc :maara (:maara-kkt-10-12 %))
                                                                     (dissoc :maara-kkt-10-12))

                                                                #(-> %
                                                                     (assoc :maara (:maara-kkt-1-9 %))
                                                                     (dissoc :maara-kkt-10-12))))
                             
                             tyot)}]
    (k/post! :tallenna-urakan-yksikkohintaiset-tyot
             hyotykuorma)))

(defn kannan-rivit->tyorivi 
  "Kahdesta tietokannan työrivistä tehdään yksi käyttöliittymän rivi
   :maara   --> :maara-kkt-10-12
           --> :maara-kkt-1-9
   :alkupvm -->  hoitokauden alkupvm
   :loppupvm -->  hoitokauden loppupvm
   sen jälkeen poistetaan ylimääräiseksi jäänyt kenttä :maara"
  [kannan-rivit]
  ;; pohjaan jää alkupvm ja loppupvm jommasta kummasta hoitokauden "osasta"
  (let [kannan-rivi-kkt-10-12 (first (sort-by :alkupvm kannan-rivit))
        kannan-rivi-kkt-1-9 (second (sort-by :alkupvm kannan-rivit))]
    (dissoc (assoc (merge kannan-rivi-kkt-10-12
                       (zipmap (map #(if (= (.getYear (:alkupvm kannan-rivi-kkt-10-12))
                                            (.getYear (:alkupvm %)))
                                       :maara-kkt-10-12 :maara-kkt-1-9) kannan-rivit)
                               (map :maara kannan-rivit))               
                       {:yhteensa (reduce + 0 (map #(* (:yksikkohinta %) (:maara %)) kannan-rivit))})
                   :alkupvm (:alkupvm kannan-rivi-kkt-10-12)
                   :loppupvm (:loppupvm kannan-rivi-kkt-1-9))
            :maara)))
