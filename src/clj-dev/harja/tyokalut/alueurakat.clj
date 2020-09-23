(ns harja.tyokalut.alueurakat
  "Työkalu hoidon alueurakoiden geometrian muuntamiseksi shp->pg"
  (:require [harja.shp :as shp]
            [clojure.java.io :as io])
  (:gen-class))

(defn alueurakka->sql [{:keys [the_geom urakka_nro ely_nro]}]
  (str
   "INSERT INTO alueurakka (alueurakkanro, alue, elynumero) VALUES ('" urakka_nro "', "
   (shp/geom->pg the_geom)
   ", "
   ely_nro
   ");\n"))

(defn -main [& args]
  (assert (= 2 (count args)) "Anna 2 parametria: Hoidon alueurakat SHP tiedosto ja tehtävä SQL tiedosto")
  (let [[tiedosto tulos] args]
    (let [tiedosto (io/file tiedosto)]
      (assert (.canRead tiedosto)
              (str "Alueurakoiden SHP tiedostoa ei voi lukea: " (.getAbsolutePath tiedosto)))
      (->> tiedosto
           shp/lue-shapefile
           shp/featuret
           (map shp/feature-propertyt)
           (map alueurakka->sql)
           (reduce str)
           (spit tulos)))))

