(ns harja-laadunseuranta.tiedot.puhe
  (:require [reagent.core :as reagent :refer [atom]]))

(defn- kasittele-tulos [event komento-fn]
  (loop [i (.-resultIndex event)]
    (when-let [e (aget (.-results event) i)]
      (when (.-isFinal e)
        (komento-fn (.-transcript (aget e 0)))))
    (when (< i (.-length (.-results event)))
      (recur (inc i)))))

(defn puhu [teksti]
  (let [u (js/SpeechSynthesisUtterance.)]
    (set! (.-lang u) "fi-FI")
    (set! (.-text u) teksti)
    (js/window.speechSynthesis.speak u)))

(defn alusta-puheentunnistus [komento-fn]
  (js/console.log "Alustetaan puheentunnistus")
  (if js/webkitSpeechRecognition
    (let [tunnistin (js/webkitSpeechRecognition.)]
      (set! (.-continuous tunnistin) true)
      (set! (.-interimResults tunnistin) false)
      (set! (.-lang tunnistin) "fi-FI")
      (set! (.-onresult tunnistin) #(do
                                      (js/console.log "Tuli tulos!")
                                      (kasittele-tulos % komento-fn)))
      (set! (.-onerror tunnistin) #(js/console.log "Tunnistusvirhe!"))
      (set! (.-onend tunnistin) #(do (js/console.log "Tunnistus päättyi, aloitan uudelleen")
                                     (.start tunnistin)))
      (.start tunnistin)
      (puhu "Puheentunnistus valmis")
      tunnistin)
    (js/console.log "Ei puheentunnistustukea")))

