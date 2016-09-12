(ns harja.palvelin.komponentit.vienti
  "Yleiset tiedostoviennin apurit"
  (:require [harja.transit :as t]
            [ring.util.codec :as codec])
  (:import (java.io ByteArrayInputStream)))

;; Jostain syystä wrap-params ei lue meidän POSTattua formia
;; Luetaan se ja otetaan "parametrit" niminen muuttuja ja
;; muunnetaan se transit+json muodosta Clojure dataksi

(defn lue-get-parametrit
  "Lukee transit objektin GET parametrista."
  [request]
  (some-> request
          :params
          (get "parametrit")
          .getBytes
          (ByteArrayInputStream.)
          t/lue-transit))

(defn lue-body-parametrit
  "Lukee transit objektin request bodyn, joka on form enkoodattu, parametrit kentästä."
  [body]
  (-> body
      .bytes
      (String.)
      codec/form-decode
      (get "parametrit")
      .getBytes
      (ByteArrayInputStream.)
      t/lue-transit))
