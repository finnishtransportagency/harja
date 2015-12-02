(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.alueurakat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.urakat :as u]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn paivita-alueurakka [db urakka]
  (u/paivita-alueurakka! db
                         (str (:gridcode urakka))
                         (.toString (:the_geom urakka))
                         (int (:piirinro urakka))))

(defn luo-alueurakka [db urakka]
  (u/luo-alueurakka<! (db)
                      (str (:gridcode urakka))
                      (.toString (:the_geom urakka))
                      (int (:piirinro urakka))))

(defn luo-tai-paivita-urakka [db urakka]
  (if (first (u/hae-alueurakka-numerolla db (str (:gridcode urakka))))
    (paivita-alueurakka db urakka)
    (luo-alueurakka db urakka)))

(defn vie-urakka-entry [db urakka]
  (if (:the_geom urakka)
    (luo-tai-paivita-urakka db urakka)
    (log/warn "Alueurakkaa ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error urakka))))

(defn vie-urakat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan urakat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (u/tuhoa-alueurakkadata! transaktio)
        (doseq [urakka (shapefile/tuo shapefile)]
          (vie-urakka-entry transaktio urakka)))
      (log/debug "Alueurakoiden tuonti kantaan valmis."))
    (log/debug "Alueurakoiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))