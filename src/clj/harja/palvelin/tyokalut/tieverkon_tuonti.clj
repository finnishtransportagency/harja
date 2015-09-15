(ns harja.palvelin.tyokalut.tieverkon-tuonti
  (:require [harja.shp :as shp]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.tieverkko :as k]))

(defn tuo-tieverkko [shapefile]
  (map shp/feature-propertyt (shp/featuret (shp/lue-shapefile shapefile))))

(defn vie-tieverkko-entry [db tv]
  (k/vie-tieverkkotauluun! db (:osoite3 tv) (:tie tv) (:ajorata tv) (:osa tv)
                           (:tiepiiri tv) (:tr_pituus tv) (.toString (:the_geom tv))))

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

(defn vie-tieverkko-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan tieosoiteverkkoa kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (k/tuhoa-tieverkkodata! transaktio)
        (doseq [tv (tuo-tieverkko shapefile)]
          (vie-tieverkko-entry transaktio tv))
        (log/debug "Tieosoiteverkon tuonti kantaan valmis.")))
    (log/debug "Tieosoiteverkon tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))

(defn vie-hoitoluokat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan hoitoluokkatietoja kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (k/tuhoa-hoitoluokkadata! transaktio)
        (doseq [tv (tuo-tieverkko shapefile)]
          (vie-hoitoluokka-entry transaktio tv))
        (log/debug "Hoitoluokkatietojen tuonti kantaan valmis")))
    (log/debug "Hoitoluokkatietojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))

(defn tee-tuontiajat [aikavali-tuntia]
  (periodic-seq (t/plus (t/now) (t/minutes 1)) (t/hours aikavali-tuntia)))

(defn tee-tuontitehtava [this]
  (log/debug "Ajastetaan tieosoiteverkon tuontitehtävä " (:aikavali this) " tunnin väleillä")
  (chime-at (tee-tuontiajat (:aikavali this))
            (fn [_]
              (try
                (do (vie-tieverkko-kantaan (:db this) (:tieverkko-shapefile this))
                    (vie-hoitoluokat-kantaan (:db this) (:hoitoluokka-shapefile this)))
                (catch Exception e
                  (log/debug "Virhe tieosoiteverkon tuonnissa, tiedostoja ei ehkä löydy"))))))

(defrecord Tieverkontuonti [tieverkko-shapefile hoitoluokka-shapefile aikavali]
  component/Lifecycle
  (start [this]
    (assoc this :tieosoiteverkon-tuontitehtava (tee-tuontitehtava this)))
  (stop [this]
    (let [poista-tuontitehtava (:tieosoiteverkon-tuontitehtava this)]
      (poista-tuontitehtava))
    this))
