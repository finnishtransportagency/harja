(ns harja.tiedot.exif
  (:require [reagent.core :refer [atom]]
            [cljsjs.exif]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn lue-kuvan-exif-tiedot
  "Lukee kuvan EXIF-tiedot käyttäen exif.js-kirjastoa.
   Kun tiedot on luettu, kutsuu callback-funktiota antaen sille parametriksi
   funktion, jolta voi kysyä halutun EXIF-tagin tiedot antamalla
   tagin nimen parametriksi. Callback voi olla esim.

   (fn [exif-fn]
     (when-let [orientaatio (exif-fn \"Orientation\")]
       ...))

   Huomaa, että kuvan täytyy olla ladattuna sivulle ennen kuin tätä
   funktiota voi kutsua. Kannattaa kutsua esim <img> elementin
   :on-load eventissä.

   Huomaa, että EXIF-tiedot saadaan vain JPEG/TIFF-kuville.
   Jos yrität lukea muille kuville, saat callbackin, mutta
   exif-dataa ei ole."
  [kuva-node tiedot-luettu-callback]
  (when kuva-node
    (.getData js/EXIF
              kuva-node
              #(tiedot-luettu-callback
                 (fn [exif-tag-nimi]
                   (.getTag js/EXIF kuva-node exif-tag-nimi))))))
