(ns harja.palvelin.integraatiot.api.tyokalut.sijainnit
  (:require [harja.kyselyt.tieverkko :as tieverkko]))

(defn hae-sijainti [db alkusijainti loppusijainti]
  (let [alku-x (:x alkusijainti)
        alku-y (:y alkusijainti)
        loppu-x (:x loppusijainti)
        loppu-y (:y loppusijainti)
        threshold 250]
    (if (and alku-x alku-y loppu-x loppu-y)
      (first (tieverkko/hae-tr-osoite-valille db alku-x alku-y loppu-x loppu-y threshold))
      (when (and alku-x alku-y)
        ;; todo: hae geometria tierekisteriosoitteelle_piste
        (first (tieverkko/hae-tr-osoite db alku-x alku-y threshold))))))