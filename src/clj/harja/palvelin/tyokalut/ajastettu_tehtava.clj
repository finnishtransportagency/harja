(ns harja.palvelin.tyokalut.ajastettu-tehtava
  "Apufunktioita tehtävien ajastamiseen"

  (:require
    [chime :refer [chime-at]]
    [taoensso.timbre :as log]
    [clj-time.core :as t]
    [clj-time.periodic :refer [periodic-seq]])
  (:import (org.joda.time DateTimeZone)))

(def virhekasittely
  {:error-handler #(log/error "Käsittelemätön poikkeus ajastetussa tehtävässä:" %)})

(defn aika-sekuntien-kuluttua [sekunnit]
  ;; palauttaa ajanhetken 20-40sek kuluttua
  (t/from-now (t/seconds sekunnit)))

(defn ajasta-paivittain [[tunti minuutti sekuntti] tehtava]
  (when (and tunti minuutti sekuntti)
    (chime-at (periodic-seq
               (.. (t/now)
                   (withZone (DateTimeZone/forID "Europe/Helsinki"))
                   (withTime tunti minuutti sekuntti 0))
               (t/days 1))
              tehtava
              virhekasittely)))

(defn ajasta-minuutin-valein [minuutit aloitusviive-sekunteina tehtava]
  ;; Kutsujien tulee antaa aloitusviive, jotta kaikki esim 60 minuutin välein tehtävät eivät
  ;; käynnisty yhtä aikaa, sekä siksi että "nyt" alkavien kanssa on satunnaista, käynnistyykö
  ;; tehtävä heti Harjan käynnistyksessä vai käynnistyykö se ensimmäistä kertaa vasta n
  ;; minuutin kuluttua Harjan käynnistyksestä.
  (when minuutit
    (chime-at (periodic-seq
               (.. (aika-sekuntien-kuluttua aloitusviive-sekunteina)
                   (withZone (DateTimeZone/forID "Europe/Helsinki")))
               (t/minutes minuutit))
              tehtava
              virhekasittely)))
