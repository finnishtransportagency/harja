(ns harja.validaatio-test
  "Validoidaan backendille ja frontille yhteisiä asioita."
  (:require [clojure.test :refer [deftest is]]
            [harja.validointi :as validointi]))

(deftest varmista-koordinaatti-validaatio
  (let [sopiva-x validointi/max-x-koordinaatti
        sopiva-y validointi/max-y-koordinaatti
        liian-suuri-x (inc validointi/max-x-koordinaatti)
        liian-suuri-y (inc validointi/max-y-koordinaatti)
        liian-pieni-x (dec validointi/min-x-koordinaatti)
        liian-pieni-y (dec validointi/min-y-koordinaatti)]
    (is (= true (validointi/onko-koordinaatit-suomen-alueella? sopiva-x sopiva-y))
      "Sopiva x ja y koordinaatti on suomen alueella")
    (is (not (validointi/onko-koordinaatit-suomen-alueella? sopiva-x liian-suuri-y)))
    (is (not (validointi/onko-koordinaatit-suomen-alueella? liian-suuri-x liian-suuri-y))
      "Liian suuri x ja y koordinaatti ei ole suomen alueella")
    (is (not (validointi/onko-koordinaatit-suomen-alueella? liian-pieni-x liian-pieni-y))
      "Liian pieni x ja y koordinaatti ei ole suomen alueella")))
