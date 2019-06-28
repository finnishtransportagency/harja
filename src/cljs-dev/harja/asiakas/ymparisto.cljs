(ns harja.asiakas.ymparisto
  "Dev ympäristön spesifisiä asioita."
  )

(def raportoi-selainvirheet? false)

(defn alusta
  "Alusta tämän ympäristön vaatimat asiat, figwheel reload."
  [options]
  (when (.-harja_testmode js/window)
    (.log js/console "E2E test mode")))
