(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile
  (:require [harja.shp :as shp]))

(defn tuo [shapefile]
  (let [sh (shp/lue-shapefile shapefile)
        props (map shp/feature-propertyt (shp/featuret sh))]
    (.dispose sh)
    props))
