(ns harja.tyokalut.elyt
  "Komponentti hallintayksiköiden rajojen hakemiseksi, tiepuolella haetaa Elyt_infra.shp tiedostosta."
  (:import (org.geotools.data.shapefile ShapefileDataStore)
           (org.geotools.map MapContent FeatureLayer))
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:gen-class))



(defn testaa [^ShapefileDataStore shp]
  (.getCo shp))
(defn- feature-propertyt
  "Muuntaa yhden featuren kaikki property mäpiksi, jossa avain on keyword."
  [feature]
  (loop [acc {}
         [p & ps] (seq (.getProperties feature))]
    (if-not p
      acc
      (recur (assoc acc
               (keyword (.toLowerCase (.getLocalPart (.getName p))))
               (.getValue p))
             ps))))
    
(defn- lue-elyt
  "Lukee LiVin Elyt_infra.shp tiedostosta ELYt (ent. tiepiirit) ja palauttaa niiden tiedot mäppinä."
  [tiedosto]
  (let [ely-featuret (-> tiedosto
                         io/as-url
                         ShapefileDataStore.
                         .getFeatureSource
                         .getFeatures .toArray seq)
        ely-propertyt (map feature-propertyt ely-featuret)]
    (zipmap (map :numero ely-propertyt)
            (map (fn [e]
                   {:nimi (:nimi e)
                    :numero (:numero e)
                    :alue (:the_geom e)})
                 ely-propertyt))))

(def lyhenteet {"Pohjois-Pohjanmaa ja Kainuu" "POP"
                "Etelä-Pohjanmaa" "EPO"
                "Lappi" "LAP"
                "Keski-Suomi" "KES"
                "Kaakkois-Suomi" "KAS"
                "Pirkanmaa" "PIR"
                "Pohjois-Savo" "POS"
                "Varsinais-Suomi" "VAR"
                "Uusimaa" "UUD"})


(defn geom->pg [alue]
  (if (= 1 (.getNumGeometries alue))
    ;; Sisältää yksittäisen polygonin, otetaan se vain
    (let [p (.getGeometryN alue 0)]
      (str "ST_GeomFromText('POLYGON(("
           (str/join ", "
                     (map #(str (.x %) " " (.y %)) (seq (.getCoordinates p)))) ;;"x1 y1, x2 y2, xN yN"
           "))')::GEOMETRY"))

    ;; useita polygoneja
    (str "ST_GeomFromText('MULTIPOLYGON("
         (str/join ","
                   (loop [acc []
                          i 0]
                     (if (= i (.getNumGeometries alue))
                       acc
                       (recur (conj acc
                                    (str "((" (str/join ", "
                                                        (map #(str (.x %) " " (.y %))
                                                             (seq (.getCoordinates (.getGeometryN alue i))))) "))"))
                              (inc i)))))
         ")')::GEOMETRY")))
                     
                   

(defn elyt->sql
  "Muodostaa SHP:sta luetuista ELYista SQL INSERT lauseet hallintayksikkötauluun"
  [elyt]
  (for [{:keys [nimi alue]} (sort-by :numero (vals elyt))]
    (str "\nINSERT INTO hallintayksikko (liikennemuoto, nimi, lyhenne, alue) VALUES ('T', '" nimi "', '" (lyhenteet nimi) "', "
         (geom->pg alue) ");")))

    
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
  
