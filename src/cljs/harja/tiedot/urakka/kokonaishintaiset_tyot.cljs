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

;; Tulevat palvelusta muodossa
;; [{:vuosi 2005, :kuukausi 10, :summa 3500.0, :maksupvm #inst "2005-10-14T21:00:00.000-00:00", :toimenpideinstanssi 7, :sopimus 2, :tpi_nimi Oulu Talvihoito TP}
;; ...
;; {:vuosi 2005, :kuukausi 11, :summa 3500.0, :maksupvm #inst "2005-11-14T22:00:00.000-00:00", :toimenpideinstanssi 7, :sopimus 2, :tpi_nimi Oulu Talvihoito TP}]

;; Tavoitemuoto
;; []
(defn hae-urakan-kokonaishintaiset-tyot [urakka-id]
  (log "tiedot,  hae-urakan-kokonaishintaiset-tyot" urakka-id)
  (go (let [res (<! (k/post! :kokonaishintaiset-tyot urakka-id))]
     (map #(aseta-hoitokausi %) res))))


(defn tallenna-urakan-kokonaishintaiset-tyot
  "Tallentaa urakan yksikköhintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id sopimusnumero tyot]
  (log "tallenna-urakan-kokonaishintaiset-tyot, urakka: " urakka-id "sopimus: " (first sopimusnumero))
  (log "työt" tyot)

  (k/post! :tallenna-urakan-kokonaishintaiset-tyot
           {:urakka-id urakka-id
            :sopimusnumero (first sopimusnumero)
            :tyot (into [] tyot)
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
  (log "kokhint kannanrivit" kannan-rivit)
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
