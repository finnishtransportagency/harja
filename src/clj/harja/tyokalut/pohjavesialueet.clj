(ns harja.tyokalut.pohjavesialueet
  "Pohjavesialueiden geometriatietojen tallennus."
  (:require [harja.shp :as shp]))

(defn lue-pohjavesialueet [tiedosto]
  "Lukee pohjavesialueet .shp tiedostosta."
  [tiedosto]

  (let [s (shp/lue-shapefile tiedosto)]
    (.setCharset s (java.nio.charset.Charset/forName "UTF-8"))
    (->> s
         shp/featuret
         (map shp/feature-propertyt)
         
         ;; tässä voidaan ottaa joko "Varsinainen muodostumisalue" tai "Pohjavesialue"
         ;; jokaisesta alueesta on sekä suurempi muodostumisalue ja pohjavesialue samalla tunnuksella
         (filter #(= (:subtype %) "Pohjavesialue")))) )
       
(defn pohjavesialue->sql [{:keys [pvaluetunn pvaluenimi muutospvm the_geom]}]
  (str "\nINSERT INTO pohjavesialue (tunnus, nimi, alue, muokattu) VALUES ('" pvaluetunn "', '" pvaluenimi "', "
       (shp/geom->pg the_geom) ", '"
       (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm") muutospvm)
       "');\n"))

(defn -main [& args]
  (assert (= 2 (count args)) "Anna 2 parametria: PvesiALue.shp sijainti ja SQL tiedosto, joka kirjoitetaan.")
  (let [p (lue-pohjavesialueet (java.io.File. (first args)))]
    (spit (second args)
          (reduce str (map pohjavesialue->sql p)))))
