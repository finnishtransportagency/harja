(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.valaistusurakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile])
  (:import (com.vividsolutions.jts.operation.polygonize Polygonizer)))

#_(defn luo-tai-paivita-urakka [db urakka]
    (let [alueen-mukaan (group-by :ualue (map feature-propertyt (featuret shape)))

          geometria (:the_geom urakka)
          polygonizer (Polygonizer.)]


      (if (first (u/hae-alueurakka-numerolla db (str (:gridcode urakka))))
        (.add polygonizer geometria)


        (doseq [{geometria :the_geom} (get alueen-mukaan 0)])

        (u/paivita-valaistusurakka! db urakkanumero geometria piirinumero)
        (u/luo-valaistusurakka<! db urakkanumero geometria piirinumero))))

(defn luo-tai-paivita-urakka [db urakka]
  )

(defn vie-urakka-entry [db urakka]
  (if (:the_geom urakka)
    (luo-tai-paivita-urakka db urakka)
    (log/warn "Valaistusurakkaa ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error urakka))))

(defn vie-urakka [param1 param2]

  )

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan urakat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]

        #_ (u/tuhoa-valaistusurakkadata! db)
        (let [urakat-alueittain (group-by :ualue (shapefile/tuo shapefile))
              urakat (mapv #({:alueurakkanro (first %) :geometriat (mapv :the_geom (second %))}) urakat-alueittain)]

          (doseq [urakka urakat]
            (vie-urakka (first urakka) (:the_geom (second urakka)))
            #_(vie-urakka-entry db urakka))))
      ;; tarviiko päivittää erikseen valaistusurakoille (u/paivita-urakka-alueiden-nakyma db)
      (log/debug "Alueurakoiden tuonti kantaan valmis."))
    (log/debug "Alueurakoiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
