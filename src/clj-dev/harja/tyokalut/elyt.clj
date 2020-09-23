(ns harja.tyokalut.elyt
  "Komponentti hallintayksiköiden rajojen hakemiseksi, tiepuolella haetaa Elyt_infra.shp tiedostosta."
  (:import (org.geotools.data.shapefile ShapefileDataStore))
  (:require [clojure.java.io :as io]
            [harja.domain.ely :as ely]
            [harja.shp :as shp])
  (:gen-class))

(defn testaa [^ShapefileDataStore shp]
  (.getCo shp))

(defn- lue-elyt
  "Lukee LiVin Elyt_infra.shp tiedostosta ELYt (ent. tiepiirit) ja palauttaa niiden tiedot mäppinä."
  [tiedosto]
  (let [ely-featuret (shp/featuret (shp/lue-shapefile tiedosto))
        ely-propertyt (map shp/feature-propertyt ely-featuret)]
    (zipmap (map :numero ely-propertyt)
            (map (fn [e]
                   {:nimi   (:nimi e)
                    :numero (:numero e)
                    :alue   (:the_geom e)})
                 ely-propertyt))))

(defn elyt->sql
  "Muodostaa SHP:sta luetuista ELYista SQL INSERT lauseet hallintayksikkötauluun"
  [elyt]
  (for [{:keys [nimi numero alue]} (sort-by :numero (vals elyt))]
    (str "\nINSERT INTO hallintayksikko (liikennemuoto, nimi, lyhenne, alue) VALUES ('T', '"
         nimi "', '"
         (ely/elynumero->lyhenne numero) "', "
         (shp/geom->pg alue) ");")))

(defn -main [& args]
  (assert (= 2 (count args)) "Anna 2 parametrita: ELY SHP tiedosto ja tehtävä SQL tiedosto")
  (let [[tiedosto tulos] args]
    (let [tiedosto (io/file tiedosto)]
      (assert (.canRead tiedosto)
              (str "Elyjen SHP tiedostoa ei voi lukea: " (.getAbsolutePath tiedosto)))
      (->> tiedosto
           lue-elyt
           elyt->sql
           (reduce str)
           (spit tulos)))))
