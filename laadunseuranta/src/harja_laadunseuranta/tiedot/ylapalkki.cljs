(ns harja-laadunseuranta.tiedot.ylapalkki
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.sovellus :as s]))

(defn havaintonappi-painettu! []
  (swap! s/nayta-paanavigointi? not))