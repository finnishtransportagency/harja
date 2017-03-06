(ns harja.tiedot.urakka.urakan-toimenpiteet
  "Urakan toimenpiteet"
  (:require [reagent.core :refer [atom] :as r]

            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-toimenpiteet-ja-tehtavat
  "Hakee urakan toimenpiteet (3. taso) ja tehtävät (4. taso) urakan id:llä."
  [urakka-id]
  (k/post! :urakan-toimenpiteet-ja-tehtavat urakka-id))

(defn hae-urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat
  "Hakee urakan kokonaishintaiset toimenpiteet (3. taso) ja tehtävät (4. taso) urakan id:llä."
  [urakka-id]
  (k/post! :urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat urakka-id))

(defn hae-urakan-yksikkohintaiset-toimenpiteet-ja-tehtavat
  "Hakee urakan yksikköhintaiset toimenpiteet (3. taso) ja tehtävät (4. taso) urakan id:llä."
  [urakka-id]
  (k/post! :urakan-yksikkohintaiset-toimenpiteet-ja-tehtavat urakka-id))

(defn hae-urakan-muutoshintaiset-toimenpiteet-ja-tehtavat
  "Hakee urakan muutoshintaiset toimenpiteet (3. taso) ja tehtävät (4. taso) urakan id:llä."
  [urakka-id]
  (k/post! :urakan-muutoshintaiset-toimenpiteet-ja-tehtavat urakka-id))

(defn- toimenpideinstanssin-sort-avain [{t3 :t3_koodi nimi :tpi_nimi}]
  [(case t3
     "23104" 1 ; Talvihoito ensimmäisenä
     "23116" 2 ; Liikenneympäristön hoito toisena
     "23124" 3 ; Soratien hoito kolmantena
     4)      ; kaikki muut sen jälkeen
   nimi])

(defn hae-urakan-toimenpiteet
  "Hakee urakan toimenpiteet (3. taso) urakan id:llä."
  [urakka-id]
  (go
    (vec (sort-by toimenpideinstanssin-sort-avain
                  (<! (k/post! :urakan-toimenpiteet urakka-id))))))

;; yleisiä pikkuapureita
(defn toimenpideinstanssin-tehtavat [tpi-id tp-instanssit tehtavat-tasoineen]
  (let [tpin-koodi (:id (first (filter #(= (:tpi_id %) tpi-id) tp-instanssit)))]
    (into []
      (filter (fn [t]
                (= (:id (nth t 2)) tpin-koodi)) tehtavat-tasoineen))))

(defn tehtava-nimella
  [tehtavan-nimi tehtavat]
  "Palauttaa tehtävän nimeä vastaan"
  (first (filter #(= (:nimi %) tehtavan-nimi) tehtavat)))

(defn tehtava-idlla
  [id tehtavat]
  "Palauttaa tehtävän id:tä vastaan"
  (first (filter #(= (:id %) id) tehtavat)))

(defn toimenpideinstanssi-idlla
  "Palauttaa toimenpideinstanssin id:tä vastaan"
  [tpi-id toimenpideinstanssit]
  (first (filter #(= (:tpi_id %) tpi-id)
                 toimenpideinstanssit)))

(defn tehtava-urakassa
  "Hakee tehtävärivin urakan toimenpidekoodeista. Tehtävän tpk-id on 4. tason tehtävän
  toimenpidekoodin tunniste."
  [tehtavan-tpk-id urakan-toimenpiteet-ja-tehtavat]
  (some (fn [[_ _ _ tehtava :as rivi]]
          (when (= (:id tehtava) tehtavan-tpk-id)
            rivi))
        urakan-toimenpiteet-ja-tehtavat))
