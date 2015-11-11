(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.soratien-hoitoluokat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.hoitoluokat :as hoitoluokat]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-hoitoluokka-entry [db soratie]
  (if (:the_geom soratie)
    (hoitoluokat/vie-hoitoluokkatauluun! db
                                         (:ajorata soratie)
                                         (:alkutieo soratie)
                                         (:tienro soratie)
                                         (:piirinro soratie)
                                         (:et_loppu soratie)
                                         (:loppu_tieo soratie)
                                         (:et_alku soratie)
                                         (:lopputieo soratie)
                                         (Integer/parseInt (:soratlk_ko soratie))
                                         (.toString (:the_geom soratie))
                                         "soratie")
    (log/warn "Soratiehoitoluokkaa ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error soratie))))

(defn vie-hoitoluokat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan soratiehoitoluokkatietoja kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (hoitoluokat/tuhoa-hoitoluokkadata! transaktio "soratie")
        (doseq [soratie (shapefile/tuo shapefile)]
          (vie-hoitoluokka-entry transaktio soratie))
        (log/debug "Soratiehoitoluokkatietojen tuonti kantaan valmis")))
    (log/debug "Soratiehoitoluokkatietojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
