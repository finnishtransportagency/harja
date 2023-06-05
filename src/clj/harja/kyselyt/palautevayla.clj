(ns harja.kyselyt.palautevayla
  (:require [specql.core :refer [fetch upsert! columns]]
            [clojure.set :as set]
            [harja.domain.palautevayla-domain :as palautevayla]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.pvm :as pvm]))

(defn lisaa-tai-paivita-aiheet [db aiheet]
  (let [aiheet-kannassa (fetch db ::palautevayla/aihe
                          #{::palautevayla/aihe-id
                            ::palautevayla/nimi
                            ::palautevayla/kaytossa?
                            ::palautevayla/jarjestys}
                          {})]
    (doseq [aihe aiheet]
      (let [aihe-kannassa (first (filter #(=
                                            (::palautevayla/aihe-id %)
                                            (::palautevayla/aihe-id aihe))
                                   aiheet-kannassa))]
        (when-not (= aihe-kannassa aihe)
          (upsert! db ::palautevayla/aihe
            (assoc aihe ::muokkaustiedot/muokattu (pvm/nyt))))))))

(defn lisaa-tai-paivita-tarkenteet [db tarkenteet]
  (let [tarkenteet-kannassa (fetch db ::palautevayla/tarkenne
                              #{::palautevayla/aihe-id
                                ::palautevayla/tarkenne-id
                                ::palautevayla/nimi
                                ::palautevayla/kaytossa?
                                ::palautevayla/jarjestys}
                              {})]
    (doseq [tarkenne tarkenteet]
      (let [tarkenne-kannassa (first (filter #(=
                                                (::palautevayla/tarkenne-id %)
                                                (::palautevayla/tarkenne-id tarkenne))
                                       tarkenteet-kannassa))]
        (when-not (= tarkenne-kannassa tarkenne)
          (upsert! db ::palautevayla/tarkenne
            (assoc tarkenne ::muokkaustiedot/muokattu (pvm/nyt))))))))

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
