(ns harja.tiedot.navigaatio
  "Tämä nimiavaruus hallinnoi sovelluksen navigoinnin. Sisältää atomit, joilla eri sivuja ja polkua 
sovelluksessa ohjataan sekä kytkeytyy selaimen osoitepalkin #-polkuun ja historiaan. Tämä nimiavaruus
ei viittaa itse näkymiin, vaan näkymät voivat hakea täältä tarvitsemansa navigointitiedot."
 
  (:require
   ;; Reititykset
   [goog.events :as events]
   [goog.history.EventType :as EventType]
   
   [reagent.core :refer [atom]])

  
  
  (:import goog.History))

;; Atomi, joka sisältää navigaation tilan
(defonce sivu (atom :urakat))

(defn kasittele-url!
  "Käsittelee urlin (route) muutokset."
  [url]
  (.log js/console url))

;; Quick and dirty history configuration.
(let [h (History.)]
  (events/listen h EventType/NAVIGATE #(kasittele-url! (.-token %)))
  (doto h (.setEnabled true)))

(defn vaihda-sivu!
  "Vaihda nykyinen sivu haluttuun."
  [uusi-sivu]
  (reset! sivu uusi-sivu))

 


   



