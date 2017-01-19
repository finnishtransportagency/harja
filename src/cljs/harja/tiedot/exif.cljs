(ns harja.tiedot.exif
  (:require [reagent.core :refer [atom]]
            [cljsjs.exif]
            [harja.loki :refer [log]]
            [cljs.core.async :refer [<!]]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn lue-kuvan-exif-tag
  "Lukee kuvan EXIF-tagin käyttäen exif.js-kirjastoa.
   Vastaus annetaan callback-funktiolle.

   Huomaa, että kuvan täytyy olla ladattuna sivulle ennen kuin tätä
   funktiota voi kutsua. Kannattaa kutsua esim <img> elementin
   :on-load eventissä."
  [kuva-node exif-tag vastaus-callback]
  (.getData js/EXIF kuva-node
            (fn []
              (vastaus-callback
                (.getTag js/EXIF (js-this) exif-tag)))))