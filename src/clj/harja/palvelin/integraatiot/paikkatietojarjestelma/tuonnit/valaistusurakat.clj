(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.valaistusurakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [clojure.string :as str])
  (:import (com.vividsolutions.jts.operation.polygonize Polygonizer)))

(defn tuo-urakka [db alueurakkanro geometriat]
  (if alueurakkanro
    (if (and geometriat (not (empty? geometriat)))
      (let [polygonizer (Polygonizer.)]
        (doseq [geometria geometriat]
          (.add polygonizer geometria))
        (let [polygonit (.getPolygons polygonizer)
              geometriat (str "MULTIPOLYGON ("(str/join "," (map #(subs (str %) 8) polygonit)) ")")]
          (println "---> polygonien määrä: " (count (.getPolygons polygonizer)))
          (println "---> geometriat: " geometriat)
          (when (pos? (count polygonit))
            (if (first (u/hae-valaistusurakka-alueurakkanumerolla db alueurakkanro))
              (u/paivita-valaistusurakka! db geometriat alueurakkanro)
              (u/luo-valaistusurakka<! db alueurakkanro geometriat)))))

      (log/warn "Valaistusurakkaa ei voida tuoda ilman geometrioita"))
    (log/warn "Valaistusurakkaa ei voida tuoda ilman alueurakkanumeroa.")))

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan valaistusurakat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]

        (u/tuhoa-valaistusurakkadata! db)
        (let [urakat-alueittain (group-by :ualue (shapefile/tuo shapefile))
              urakat (mapv #(hash-map
                             :alueurakkanro (str (first %))
                             :geometriat (mapv :the_geom (second %)))
                           urakat-alueittain)]

          (doseq [urakka urakat]
            (tuo-urakka db (:alueurakkanro urakka) (:geometriat urakka)))))

      ;; todo: tarviiko päivittää erikseen valaistusurakoille (u/paivita-urakka-alueiden-nakyma db)

      (log/debug "Valaistusurakoiden tuonti kantaan valmis."))
    (log/debug "Valaistusurakoiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
