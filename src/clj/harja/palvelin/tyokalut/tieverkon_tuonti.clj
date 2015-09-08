(ns harja.palvelin.tyokalut.tieverkon-tuonti
  (:require [harja.shp :as shp]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [clojure.java.jdbc :as jdbc]
            [clj-time.periodic :refer [periodic-seq]]
            [chime :refer [chime-at]]
            [harja.kyselyt.tieverkko :as k]))

(def +tieverkko-shp-example+ "file:///Users/markolau/Documents/TR_PTJ_data/Tieosoiteverkko.shp")

(defn tuo-tieverkko [shapefile]
  (map shp/feature-propertyt (shp/featuret (shp/lue-shapefile shapefile))))

(defn vie-entry [db tv]
  (k/vie-tieverkkotauluun! db (:osoite3 tv) (:tie tv) (:ajorata tv) (:osa tv)
                           (:tiepiiri tv) (:tr_pituus tv) "I" (.toString (:the_geom tv)) (hash tv)))

(defn vie-kantaan [db shapefile]
  (log/debug (str "Tuodaan tieosoiteverkkoa kantaan tiedostosta " shapefile))
  (jdbc/with-db-transaction [transaktio db]
    (k/tuhoa-tieverkkodata! transaktio)
    (doseq [tv (tuo-tieverkko shapefile)]
      (vie-entry transaktio tv))
    (log/debug "Tieosoiteverkon tuonti kantaan valmis.")))

(defn tee-tuontiajat []
  (periodic-seq (t/now) (t/minutes 5)))

(defn tee-tuontitehtava [this]
  (log/debug "Ajastetaan tieosoiteverkon tuontitehtävä")
  (chime-at (tee-tuontiajat)
            (fn [_]
              (vie-kantaan (:db this) +tieverkko-shp-example+))))

(defrecord Tieverkontuonti []
  component/Lifecycle
  (start [this]
    (assoc this :tieosoiteverkon-tuontitehtava (tee-tuontitehtava this)))
  (stop [this]
    (let [poista-tuontitehtava (:tieosoiteverkon-tuontitehtava this)]
      (poista-tuontitehtava))
    this))
