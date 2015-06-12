(ns harja.tiedot.urakka.urakan-toimenpiteet
  "Urakan toimenpiteet"
  (:require [reagent.core :refer [atom] :as r]

            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn hae-urakan-toimenpiteet-ja-tehtavat
  "Hakee urakan toimenpiteet (3. taso) ja tehtävät (4. taso) urakan id:llä."
  [urakka-id]
  (k/post! :urakan-toimenpiteet-ja-tehtavat urakka-id))

(defn hae-urakan-toimenpiteet
  "Hakee urakan toimenpiteet (3. taso) urakan id:llä."
  [urakka-id]
  (k/post! :urakan-toimenpiteet urakka-id))

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