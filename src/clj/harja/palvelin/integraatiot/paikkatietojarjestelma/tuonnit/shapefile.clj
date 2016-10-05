(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile
  (:require [harja.shp :as shp]
            [taoensso.timbre :as log])
  (:import (java.io FileNotFoundException)))

(defn tuo [shapefile]
  (try
    (let [sh (shp/lue-shapefile shapefile)
          props (map shp/feature-propertyt (shp/featuret sh))]
      (.dispose sh)
      props)
    (catch RuntimeException e
      (if (instance? FileNotFoundException (.getCause e))
        (log/warn "Shape-fileä:" shapefile "ei voitu lukea. Tiedostoa ei löydy.")
        (throw e)))))

(defn tuo-ryhmiteltyna [shapefile ryhma-kentta ryhma-callback]
  (try
    (let [sh (shp/lue-shapefile shapefile)]
      (shp/featuret-ryhmiteltyna sh ryhma-kentta ryhma-callback)
      (.dispose sh))
    (catch RuntimeException e
      (if (instance? FileNotFoundException (.getCause e))
        (log/warn "Shape-fileä:" shapefile "ei voitu lukea. Tiedostoa ei löydy.")
        (throw e)))))
