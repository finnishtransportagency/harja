(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.valaistusurakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile])
  (:import (com.vividsolutions.jts.operation.polygonize Polygonizer)))

(defn tuo-urakka [db alueurakkanro geometriat]
  (if alueurakkanro
    (if (and geometriat (not (empty? geometriat)))
      (let [polygonizer (Polygonizer.)]
        (doseq [geometria geometriat]
          (.add polygonizer geometria))
        (let [polygonit (.getPolygons polygonizer)]
          (if (first (u/hae-valaistusurakka-alueurakkanumerolla db alueurakkanro))
            (u/paivita-valaistusurakka! db alueurakkanro polygonit)
            (u/luo-valaistusurakka<! db alueurakkanro polygonit))))

      (log/warn "Valaistusurakkaa ei voida tuoda ilman geometrioita"))
    (log/warn "Valaistusurakkaa ei voida tuoda ilman alueurakkanumeroa.")))

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan urakat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]

        (u/tuhoa-valaistusurakkadata! db)
        (let [urakat-alueittain (group-by :ualue (shapefile/tuo shapefile))
              urakat (mapv #({:alueurakkanro (first %) :geometriat (mapv :the_geom (second %))}) urakat-alueittain)]

          (doseq [urakka urakat]
            (tuo-urakka db (first urakka) (:the_geom (second urakka))))))

      ;; todo: tarviiko päivittää erikseen valaistusurakoille (u/paivita-urakka-alueiden-nakyma db)

      (log/debug "Alueurakoiden tuonti kantaan valmis."))
    (log/debug "Alueurakoiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
