(ns harja.tyokalut.html
  "Apureita yksinkertaisten HTML-elementtien tekemiseen (esim. sähköpostilähetystä varten)"
  (:require [clojure.xml :refer [parse]]
            [clojure.java.io :as io]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clj-time.format :as f]
            [clj-time.coerce :as tc]
            [clojure.data.zip.xml :as z]))

(defn taulukko [kentta-arvo-parit]
  [:table
   (for [[kentta arvo] kentta-arvo-parit]
     [:tr
      [:td [:b kentta]]
      [:td arvo]])])