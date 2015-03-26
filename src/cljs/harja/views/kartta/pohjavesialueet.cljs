(ns harja.views.kartta.pohjavesialueet
  "Pohjavesialueet karttataso. Hakee palvelimelta valitun hallintayksikön alueella olevat pohjavesialueet."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :refer [valittu-hallintayksikko]]
            [harja.asiakas.kommunikaatio :as k]
            [cognitect.transit :as t] 
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def taso-pohjavesialueet (atom false))
(def pohjavesialueet (atom []))

;; Pohjavesialueet eivät muutu edes vuosittain, joten voimme turvallisesti cachettaa
(defn tallenna-pohjavesialueet
  "Tallentaa ladatut pohjavesialueet annetulle hallintayksikölle localStorageen."
  [hal alueet]
  (.setItem js/localStorage (str "pohjavesialueet-" hal) (t/write (t/writer :json) alueet)))

(defn lue-pohjavesialueet
  "Lukee localStoragesta muitissa olevat pohjavesialueet"
  [hal]
  (try
    (let [alueet (.getItem js/localStorage(str "pohjavesialueet-" hal))]
      (when alueet
        (t/read (t/reader :json) alueet)))
    (catch :default _
      nil)))
            
    
(run! (let [nakyvissa? @taso-pohjavesialueet
            hal (:id @valittu-hallintayksikko)]
        (if (or (not nakyvissa?)
                (nil? hal))
          ;; jos taso ei ole näkyvissä tai hallintayksikköä ei valittu => asetetaan heti tyhjä
          (reset! pohjavesialueet [])
          
          ;; taso näkyvissä ja hallintayksikkö valittu, haetaan alueet
          (if-let [pa (lue-pohjavesialueet hal)]
            ;; muistissa oli aiemmin ladatut alueet, palautetaan ne
            (reset! pohjavesialueet pa)
            
            ;; ei muistissa, haetaan ne
            (go
              (let [res (<! (k/post! :hae-pohjavesialueet hal))]
                (tallenna-pohjavesialueet hal res)
                (when (= hal (:id @valittu-hallintayksikko))
                  ;; jos hallintayksikköä ei ole ehditty muuttaa ennen kuin vastaus tuli
                  (reset! pohjavesialueet res)))))))) 

  
