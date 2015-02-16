(ns harja.asiakas.ymparisto
  "Dev ympäristön spesifisiä asioita."
  (:require lively))

(defn alusta
  "Alusta tämän ympäristön vaatimat asiat, Lively reload."
  [options]
  (.log js/console "Alustetaan koodin uudelleenlataus")
  (lively/start "/js/harja.js"
                {:polling-rate 1000
                 :on-reload (fn []
                              (.log js/console "Koodia ladattu uudelleen.")
                              (when-let [on-reload (:on-reload options)]
                                (on-reload)))})

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



