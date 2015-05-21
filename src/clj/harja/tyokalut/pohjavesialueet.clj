(ns harja.tyokalut.pohjavesialueet
  "Pohjavesialueiden geometriatietojen tallennus."
  (:require [harja.shp :as shp]))

;; Luetaan tr_syke_pvalue shapesta.
;; TR:n mukaan kentän PVSUOJA arvo on "1" jos alueella on suojoaus.

(defn lue-pohjavesialueet
  "Lukee pohjavesialueet .shp tiedostosta."
  [tiedosto]
  
  (let [s (shp/lue-shapefile tiedosto)]
    ;;(.setCharset s (java.nio.charset.Charset/forName "UTF-8"))
    (->> s
         shp/featuret
         (map shp/feature-propertyt)
         
         (filter #(= (:pvsuola %) 1))
         )))
       
(defn pohjavesialue->sql [{:keys [pvaluetunn pvaluenimi muutospvm the_geom]}]
  (str "\nINSERT INTO pohjavesialue (tunnus, nimi, alue, muokattu) VALUES ('" pvaluetunn "', '" pvaluenimi "', "
       "ST_GeomFromText('" the_geom "')::GEOMETRY, '"
       (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm") muutospvm)
       "');\n"))

(defn -main [& args]
  (assert (= 2 (count args)) "Anna 2 parametria: PvesiALue.shp sijainti ja SQL tiedosto, joka kirjoitetaan.")
  (let [p (lue-pohjavesialueet (java.io.File. (first args)))]
    (spit (second args)
          (reduce str (map pohjavesialue->sql p)))))
