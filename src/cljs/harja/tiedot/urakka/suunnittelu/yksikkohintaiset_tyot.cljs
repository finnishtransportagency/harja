(ns harja.tiedot.urakka.suunnittelu.yksikkohintaiset-tyot
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

(defn kokonaishinta [t]
  (* (:yksikkohinta t) (:maara t)))

(defn summa [i]
  (reduce + 0 i))

(defn valitse-kuukausimaarat [kannan-rivi-kkt-10-12 i]
  (if (= (.getYear (:alkupvm kannan-rivi-kkt-10-12))
         (.getYear (:alkupvm i)))
    :maara-kkt-10-12 :maara-kkt-1-9))

(defn kannan-rivit->tyorivi 
  "Kahdesta tietokannan työrivistä tehdään yksi käyttöliittymän rivi
   :maara   --> :maara-kkt-10-12
           --> :maara-kkt-1-9
   :alkupvm -->  hoitokauden alkupvm
   :loppupvm -->  hoitokauden loppupvm
   sen jälkeen poistetaan ylimääräiseksi jäänyt kenttä :maara"
  [kannan-rivit]
  ;; pohjaan jää alkupvm ja loppupvm jommasta kummasta hoitokauden "osasta"
  (let [rivit-jarjestyksessa (sort-by :alkupvm kannan-rivit)
        kannan-rivi-kkt-10-12 (first rivit-jarjestyksessa)
        kannan-rivi-kkt-1-9 (second rivit-jarjestyksessa)]
    
    (-> kannan-rivi-kkt-10-12
        (merge 
         (zipmap (map #(valitse-kuukausimaarat kannan-rivi-kkt-10-12 %) kannan-rivit)
                 (map :maara kannan-rivit))               
         {:yhteensa (summa (map kokonaishinta kannan-rivit))})
        (assoc 
         :alkupvm (:alkupvm kannan-rivi-kkt-10-12)
         :loppupvm (:loppupvm kannan-rivi-kkt-1-9))
        (dissoc 
         :maara))))
