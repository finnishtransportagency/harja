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

;;FIXME: erityyppisten urakoiden päivämäärien luonti siistimmäksi

;; hoidon alueurakan hoitokausi on 1.10.YYYY - 30.9.YYYY+1. Käyttöliittymässä syötetään
;; tieto kullekin vuodelle erikseen per hoitokausi 
;; --> tarve pilkkoa hoitokauden määrät 10-12 ja 1-9 kk väleille, tietokannassa
;; yhden hoitokauden yksi työ menee siis kahdelle riville (jos molemmille vuosille syötetty tietoa)
(defn pilko-hoitokausien-tyot [tyot]
  ;; luodaan yhdestä rivistä kaksi riviä, hoitokauden molempien vuosien osat
  (mapcat   (fn [rivi]
              (let [alkupvm-10-12 (pvm/goog->js (pvm/luo-pvm (.getFullYear (pvm/goog->js (:alkupvm rivi))) 9 1)) ;;1.10.yyyy
                    loppupvm-10-12 (pvm/goog->js (pvm/luo-pvm (.getFullYear (pvm/goog->js (:alkupvm rivi))) 11 31)) ;;31.12.yyyy
                    alkupvm-1-9 (pvm/goog->js (pvm/luo-pvm (.getFullYear (pvm/goog->js (:loppupvm rivi))) 0 1))
                    loppupvm-1-9 (pvm/goog->js (pvm/luo-pvm (.getFullYear (pvm/goog->js (:loppupvm rivi))) 8 30))
                    ]
                
                [(dissoc (assoc rivi :alkupvm alkupvm-10-12 :loppupvm loppupvm-10-12 :maara (:maara-kkt-10-12 rivi)) :maara-kkt-1-9 :maara-kkt-10-12)
                 (dissoc (assoc rivi :alkupvm alkupvm-1-9 :loppupvm loppupvm-1-9 :maara (:maara-kkt-1-9 rivi)) :maara-kkt-1-9 :maara-kkt-10-12)
                 ])) tyot))

(defn tallenna-urakan-yksikkohintaiset-tyot
  "Tallentaa urakan yksikköhintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id sopimusnumero tyot]
  (log "tallenna-urakan-yksikkohintaiset-tyot, urakka: " urakka-id "sopimus: " (first sopimusnumero))
  (log "työt" tyot)
  
  (k/post! :tallenna-urakan-yksikkohintaiset-tyot 
           {:urakka-id urakka-id
            :sopimusnumero (first sopimusnumero)
            :tyot (into [] (pilko-hoitokausien-tyot tyot))
            }
           ))

(defn kannan-rivit->tyorivi 
  "Kahdesta tietokannan työrivistä tehdään yksi käyttöliittymän rivi
   :maara   --> :maara-kkt-10-12
            --> :maara-kkt-1-9
   :alkupvm -->  hoitokauden alkupvm
   :loppupvm -->  hoitokauden loppupvm
   sen jälkeen poistetaan ylimääräiseksi jäänyt kenttä :maara"
  [kannan-rivit]
  ;; pohjaan jää alkupvm ja loppupvm jommasta kummasta hoitokauden "osasta"
  (let [pohja (first kannan-rivit)
        kannan-rivi-kkt-10-12 (first (sort-by :alkupvm kannan-rivit))
        kannan-rivi-kkt-1-9 (second (sort-by :alkupvm kannan-rivit))]
    (dissoc (assoc (merge pohja
                       (zipmap (map #(if (= (.getYear (:alkupvm kannan-rivi-kkt-10-12))
                                            (.getYear (:alkupvm %)))
                                       :maara-kkt-10-12 :maara-kkt-1-9) kannan-rivit)
                               (map :maara kannan-rivit))               
                       {:yhteensa (reduce + 0 (map #(* (:yksikkohinta %) (:maara %)) kannan-rivit))})
                   :alkupvm (:alkupvm kannan-rivi-kkt-10-12)
                   :loppupvm (:loppupvm kannan-rivi-kkt-1-9))
            :maara)))