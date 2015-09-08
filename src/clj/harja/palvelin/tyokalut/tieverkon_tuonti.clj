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

(defn vie-entry [db tv]
  (k/vie-tieverkkotauluun! db (:osoite3 tv) (:tie tv) (:ajorata tv) (:osa tv)
                           (:tiepiiri tv) (:tr_pituus tv) "I" (.toString (:the_geom tv)) (hash tv)))

(defn vie-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan tieosoiteverkkoa kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [transaktio db]
        (k/tuhoa-tieverkkodata! transaktio)
        (doseq [tv (tuo-tieverkko shapefile)]
          (vie-entry transaktio tv))
        (log/debug "Tieosoiteverkon tuonti kantaan valmis.")))
    (log/debug "Tieosoiteverkon tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))

(defn tee-tuontiajat [aikavali-tuntia]
  (periodic-seq (t/now) (t/hours aikavali-tuntia)))

(defn tee-tuontitehtava [this]
  (log/debug "Ajastetaan tieosoiteverkon tuontitehtävä " (:aikavali this) " tunnin väleillä")
  (chime-at (tee-tuontiajat (:aikavali this))
            (fn [_]
              (vie-kantaan (:db this) (:shapefile this)))))

(defrecord Tieverkontuonti [shapefile aikavali]
  component/Lifecycle
  (start [this]
    (assoc this :tieosoiteverkon-tuontitehtava (tee-tuontitehtava this)))
  (stop [this]
    (let [poista-tuontitehtava (:tieosoiteverkon-tuontitehtava this)]
      (poista-tuontitehtava))
    this))
