(ns harja.kyselyt.palautejarjestelma
  (:require [specql.core :refer [fetch upsert! columns]]
            [harja.domain.palautejarjestelma-domain :as palautejarjestelma]))

(defn lisaa-tai-paivita-aiheet [db aiheet]
  (doseq [aihe aiheet]
    (upsert! db ::palautejarjestelma/aihe
      (dissoc aihe :kaytossa?))))

(defn lisaa-tai-paivita-tarkenteet [db tarkenteet]
  (doseq [tarkenne tarkenteet]
    (upsert! db ::palautejarjestelma/tarkenne
      (dissoc tarkenne :kaytossa?))))

(defn hae-aiheet-ja-tarkenteet [db]
  (fetch db ::palautejarjestelma/aihe
    (conj (columns ::palautejarjestelma/aihe)
      [::palautejarjestelma/tarkenteet (columns ::palautejarjestelma/tarkenne)])
    {::palautejarjestelma/kaytossa? true}))
