(ns harja.asiakas.ymparisto
  "Dev ympäristön spesifisiä asioita."
  (:require lively))

(defn alusta
  "Alusta tämän ympäristön vaatimat asiat, Lively reload."
  []
  (lively/start "/js/harja.js"
                (fn [] (.log js/console "Koodia ladattu uudelleen."))))


