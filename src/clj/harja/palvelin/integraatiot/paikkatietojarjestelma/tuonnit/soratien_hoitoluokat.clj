(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.hoitoluokat :as hoitoluokat]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-hoitoluokka-entry [db tv]
  (hoitoluokat/vie-hoitoluokkatauluun! db
                                     (:ajorata tv)
                                     (:aosa tv)
                                     (:tie tv)
                                     (:piirinro tv)
                                     (:let tv)
                                     (:losa tv)
                                     (:aet tv)
                                     (:osa tv)
                                     (int (:kplk tv))
                                     (.toString (:the_geom tv))
                                     "soratie"))

(defn vie-hoitoluokat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan soratiehoitoluokkatietoja kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (hoitoluokat/tuhoa-hoitoluokkadata! transaktio "soratie")
        (doseq [tv (shapefile/tuo shapefile)]
          (vie-hoitoluokka-entry transaktio tv))
        (log/debug "Soratiehoitoluokkatietojen tuonti kantaan valmis")))
    (log/debug "Soratiehoitoluokkatietojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
