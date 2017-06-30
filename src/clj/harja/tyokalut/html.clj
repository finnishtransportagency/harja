(ns harja.tyokalut.html
  "Apureita yksinkertaisten HTML-elementtien tekemiseen (esim. sähköpostilähetystä varten)"
  (:require [clojure.xml :refer [parse]]
            [clojure.java.io :as io]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [hiccup.core :refer [html h]]
            [clj-time.format :as f]
            [clj-time.coerce :as tc]
            [clojure.data.zip.xml :as z]))

(defn tietoja
  "Luo yksinkertaisen kenttä-arvo pareja sisältävän listauksen.
   Arvo voi olla tekstiä tai HTML-elementti.
   HUOM! Arvojen sanitointi on kutsujapään vastuulla!"
  [kentta-arvo-parit]
  [:table
   (for [[kentta arvo] kentta-arvo-parit]
     [:tr
      [:td [:b kentta]]
      [:td arvo]])])

(defn nappilinkki
  "Luo yksinkertaisen nappi-linkin.
   Napin teksti sanitoidaan."
  [napin-teksti linkki]
  [:table {:width "100%" :border "0" :cellspacing "0" :cellpadding "0"}
   [:tr
    [:td
     [:table {:border "0" :cellspacing "0" :cellpadding "0"}
      [:tr
       [:td {:bgcolor "#EB7035" :style "padding: 12px; border-radius: 3px;" :align "center"}
        [:a {:href linkki
             :style "font-size: 16px; font-family: Helvetica, Arial, sans-serif; font-weight: normal; color: #ffffff; text-decoration: none; display: inline-block;"}
         (h napin-teksti)]]]]]]])