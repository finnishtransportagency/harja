(ns harja.ui.svg-sprite
  "Makro SVG-spritejen määrittelyyn"
  (:require [clojure.xml :as xml :refer [parse]]
            [clojure.java.io :as io]
            [clojure.zip :as zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [clojure.string :as str]))

(def polku "resources/public/laadunseuranta/img")

(defn- lataa-svg-sprite-tiedosto [tiedosto]
  (xml-zip (parse (io/file (str "resources/public/laadunseuranta/img/" tiedosto)))))

(defn- element-hiccup [elt]
  (if (string? elt)
    elt
    (let [{:keys [tag attrs content]} elt]
      (into [tag (or attrs {})]
            (map element-hiccup content)))))

(defmacro maarittele-svg-spritet [width height tiedosto]
  (let [doc (lataa-svg-sprite-tiedosto tiedosto)
        sprites
        (z/xml-> doc :symbol
                 (fn [symbol]
                   {:id (-> symbol
                            (z/xml1-> (z/attr :id))
                            (str/replace #"_" "-")
                            (str/replace #" " "-"))
                    :view-box (z/xml1-> symbol (z/attr :viewBox))
                    :svg (element-hiccup (first (zip/down symbol)))}))]
    `(do
       ~@(for [sprites (partition-all 16 sprites)]
           `(do
              ~@(for [{:keys [id svg view-box]} sprites
                      :let [fn-name (symbol id)]]
                  `(defn ~fn-name
                     ([] (~fn-name ~width ~height))
                     ([width# height#]
                      [:svg {:width width# :height height#
                             :viewBox ~view-box}
                       ~svg]))))))))
