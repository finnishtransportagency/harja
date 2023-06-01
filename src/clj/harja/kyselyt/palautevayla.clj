(ns harja.kyselyt.palautevayla
  (:require [specql.core :refer [fetch upsert! columns]]
            [harja.domain.palautevayla-domain :as palautevayla]
            [clojure.set :as set]))

(defn lisaa-tai-paivita-aiheet [db aiheet]
  (doseq [aihe aiheet]
    (upsert! db ::palautevayla/aihe
      (dissoc aihe :kaytossa?))))

(defn lisaa-tai-paivita-tarkenteet [db tarkenteet]
  (doseq [tarkenne tarkenteet]
    (upsert! db ::palautevayla/tarkenne
      (dissoc tarkenne :kaytossa?))))

(defn hae-aiheet-ja-tarkenteet [db]
  (sort-by :jarjestys
    (map
      (fn [aihe]
        (as-> aihe aihe
          (set/rename-keys aihe palautevayla/domain->api)
          (update aihe :tarkenteet
            #(map (fn [tarkenne] (set/rename-keys tarkenne palautevayla/domain->api)) %))
          (update aihe :tarkenteet
            #(filter :kaytossa? %))
          (update aihe :tarkenteet
            #(sort-by :jarjestys %))))
      (fetch db ::palautevayla/aihe
        (conj (columns ::palautevayla/aihe)
          [::palautevayla/tarkenteet (columns ::palautevayla/tarkenne)])
        {::palautevayla/kaytossa? true}))))
