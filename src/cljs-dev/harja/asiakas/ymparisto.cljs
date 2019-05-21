(ns harja.asiakas.ymparisto
  "Dev ympäristön spesifisiä asioita."
  (:require [figwheel.client :as fw]
            [harja.ui.viesti :as viesti]))


(goog-define FIGWHEEL-WS? true)



(def raportoi-selainvirheet? false)

(.log js/console (str "FIGWHEEL-WS?: " FIGWHEEL-WS?))

(defn alusta
  "Alusta tämän ympäristön vaatimat asiat, figwheel reload."
  [options]
  (.log js/console "Alustetaan koodin uudelleenlataus")
  (when (.-harja_testmode js/window)
    (.log js/console "E2E test mode"))

  (when FIGWHEEL-WS?
    (fw/start {:websocket-url   "ws://localhost:3449/figwheel-ws"
               ;;:debug true
                                        ;:reload-dependents false
               :on-jsload (fn [] (.log js/console "Koodia ladattu uudelleen")
                            (when-let [on-reload (:on-reload options)]
                              (on-reload)))}))

  (.log js/console "Alustetaan less.js uudelleenlataus")
  (let [less (aget js/window "less")
        logger (aget less "logger")]
    (aset logger "info" (fn [& args])))
  (js/setInterval #(let [less (aget js/window "less")
                         refresh (aget less "refresh")]
                     (try
                       (refresh true)
                       (catch js/Object o
                         (.log js/console "Virhe Less päivityksessä: " o))))
                  5000))
