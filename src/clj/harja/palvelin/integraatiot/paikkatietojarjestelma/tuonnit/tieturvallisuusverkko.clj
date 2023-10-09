(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieturvallisuusverkko
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.tieturvallisuusverkko :as tieturvallisuusverkko-kyselyt]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-tieturvallisuusverkko-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan tieturvallisuusverkko kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        ;; Tuhotaan olemassa oleva tieturvallisuusverkko
        (tieturvallisuusverkko-kyselyt/tuhoa-tieturvallisuusverkko! db)
        (doseq [datarivi (shapefile/tuo shapefile)]
          (let [datarivi (-> datarivi
                           (update :the_geom #(.toString %))
                           (update :aosa #(Integer/parseInt %))
                           (update :let #(Integer/parseInt %))
                           (update :tie #(Integer/parseInt %))
                           (update :losa #(Integer/parseInt %))
                           (update :aet #(Integer/parseInt %))
                           (update :pituus #(Integer/parseInt %)))]
            (tieturvallisuusverkko-kyselyt/lisaa-tieturvallisuusverkko! db datarivi)))))
    (throw (Exception. (format "Error. Tuontia ei suoriteta." shapefile)))))
