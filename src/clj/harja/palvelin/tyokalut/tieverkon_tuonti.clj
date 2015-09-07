(ns harja.palvelin.tyokalut.tieverkon-tuonti
  (:require [harja.shp :as shp]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.main :as m]
            [harja.kyselyt.tieverkko :as k]))

(def +tieverkko-shp-example+ "file:///Users/markolau/Documents/TR_PTJ_data/Tieosoiteverkko.shp")

(defn tuo-tieverkko [shapefile]
  (map shp/feature-propertyt (shp/featuret (shp/lue-shapefile shapefile))))

(defn vie-entry [db tv]
  (k/vie-tierekisteri! db (:osoite3 tv) (:tie tv) (:ajorata tv) (:osa tv)
                       (:tiepiiri tv) (:tr_pituus tv) "I" (.toString (:the_geom tv)) (hash tv)))

(defn vie-kantaan [db shapefile]
  (log/debug (str "Tuodaan tieosoiteverkkoa kantaan tiedostosta " shapefile))
  (doseq [tv (tuo-tieverkko shapefile)]
    (vie-entry db tv))
  (log/debug "Tieosoiteverkon tuonti kantaan valmis."))

(defn vie-esimerkki-tieverkko []
  (m/with-db db
    (vie-kantaan db +tieverkko-shp-example+)))

(defrecord Tieverkontuonti []
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))
