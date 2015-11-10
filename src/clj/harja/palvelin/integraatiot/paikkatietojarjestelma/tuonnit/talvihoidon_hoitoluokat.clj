(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.talvihoidon-hoitoluokat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.hoitoluokat :as hoitoluokat]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn vie-hoitoluokka-entry [db talvihoito]
  (if (:the_geom talvihoito)
    (hoitoluokat/vie-hoitoluokkatauluun! db
                                         (:ajorata talvihoito)
                                         (:aosa talvihoito)
                                         (:tie talvihoito)
                                         (:piirinro talvihoito)
                                         (:let talvihoito)
                                         (:losa talvihoito)
                                         (:aet talvihoito)
                                         (:osa talvihoito)
                                         (int (:kplk talvihoito))
                                         (.toString (:the_geom talvihoito))
                                         "talvihoito"))
    (log/warn "Talvihoitoluokkaa ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error talvihoito)))

(defn vie-hoitoluokat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan talvihoitoluokkatietoja kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (hoitoluokat/tuhoa-hoitoluokkadata! transaktio "talvihoito")
        (doseq [soratie (shapefile/tuo shapefile)]
          (vie-hoitoluokka-entry transaktio soratie))
        (log/debug "Talvihoitoluokkatietojen tuonti kantaan valmis")))
    (log/debug "Talvihoitoluokkatietojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
