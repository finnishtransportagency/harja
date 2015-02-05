(ns harja.tiedot.navigaatio
  "Tämä nimiavaruus hallinnoi sovelluksen navigoinnin. Sisältää atomit, joilla eri sivuja ja polkua 
sovelluksessa ohjataan sekä kytkeytyy selaimen osoitepalkin #-polkuun ja historiaan. Tämä nimiavaruus
ei viittaa itse näkymiin, vaan näkymät voivat hakea täältä tarvitsemansa navigointitiedot."
 
  (:require
   ;; Reititykset
   [goog.events :as events]
   [goog.history.EventType :as EventType]
   [reagent.core :refer [atom]]
   
   [harja.asiakas.tapahtumat :as t]
   [harja.tiedot.urakat :as ur])
  
   (:require-macros [cljs.core.async.macros :refer [go]])
  
  (:import goog.History))

;; Atomi, joka sisältää valitun sivun
(defonce sivu (atom :urakat))

;; Atomi, joka sisältää valitun hallintayksikön
(def valittu-hallintayksikko "Tällä hetkellä valittu hallintayksikkö (tai nil)" (atom nil))

;; Atomi, joka sisältää valitun urakan
(def valittu-urakka "Tällä hetkellä valittu urakka (hoidon alueurakka / ylläpidon urakka) tai nil" (atom nil))

;; Atomi, joka sisältää valitun hallintayksikön urakat
(def urakkalista "Hallintayksikon urakat" (atom nil))

;; Rajapinta hallintayksikön valitsemiseen, jota viewit voivat kutsua
(defn valitse-hallintayksikko [yks]
  (reset! valittu-hallintayksikko yks)
  (reset! urakkalista nil)
  (reset! valittu-urakka nil)
  (if yks
    (do
      (go (reset! urakkalista (<! (ur/hae-hallintayksikon-urakat yks))))
      (t/julkaise! (assoc yks :aihe :hallintayksikko-valittu)))
    (t/julkaise! {:aihe :hallintayksikkovalinta-poistettu})))

(defn valitse-urakka [ur]
  (reset! valittu-urakka ur)
  (if ur
    (t/julkaise! (assoc ur :aihe :urakka-valittu))
    (t/julkaise! {:aihe :urakkavalinta-poistettu})))



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

 


   



