(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile
  (:require [harja.shp :as shp]))

(defn tuo [shapefile]
  (map shp/feature-propertyt (shp/featuret (shp/lue-shapefile shapefile))))