(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.tieverkko :as k]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-hoitoluokka-entry [db tv]
  (k/vie-hoitoluokkatauluun! db
                             (:ajorata tv)
                             (:aosa tv)
                             (:tie tv)
                             (:piirinro tv)
                             (:let tv)
                             (:losa tv)
                             (:aet tv)
                             (:osa tv)
                             (int (:kplk tv))
                             (.toString (:the_geom tv))))

(defn vie-hoitoluokat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan hoitoluokkatietoja kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (k/tuhoa-hoitoluokkadata! transaktio)
        (doseq [tv (shapefile/tuo shapefile)]
          (vie-hoitoluokka-entry transaktio tv))
        (log/debug "Hoitoluokkatietojen tuonti kantaan valmis")))
    (log/debug "Hoitoluokkatietojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
