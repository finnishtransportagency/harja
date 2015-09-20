(ns harja.tyokalut.vkm
  "TR-osoitehaku"

  (:require [cljs.core.async :refer [<! >! chan put! close!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn koordinaatti->trosoite-kahdella [[x1 y1] [x2 y2]]
  (k/post! :hae-tr-pisteilla {:x1 x1 :y1 y1 :x2 x2 :y2 y2}))

(defn koordinaatti->trosoite [[x y]]
  (k/post! :hae-tr-pisteella {:x x :y y}))

(defn virhe?
  "Tarkistaa epäonnistuiko VKM kutsu"
  [tulos]
  (contains? tulos :virhe))
       
(defn virhe [tulos]
  "Pisteelle ei löydy tietä")

  
