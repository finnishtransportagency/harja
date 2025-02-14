(ns harja.palvelin.integraatiot.api.tyokalut.sijainnit
  (:require [harja.kyselyt.tieverkko :as tieverkko]
            [taoensso.timbre :as log]
            [harja.geo :as geo])
  (:import (org.postgresql.util PSQLException)
           (net.postgis.jdbc.geometry Point LineString)))

(defn hae-tierekisteriosoite [db {alku-x :x alku-y :y} {loppu-x :x loppu-y :y}]
  (let [threshold 250]
    (try
      (if (and alku-x alku-y loppu-x loppu-y
            ;; jos alkupiste ja loppupiste ovat samat, käsiteltävä pistemäisenä
            (not (and (= alku-x loppu-x) (= alku-y loppu-y))))
        (let [tr-osoite (tieverkko/hae-tr-osoite-valille-ehka db alku-x alku-y loppu-x loppu-y threshold)]
          ;; hanskataan tässä harvinainen tapaus, jossa x ja y ovat ihan hivenen erisuuret, mutta käytännössä kyse
          ;; on samasta pisteestä, eli geometriaksi palautuu pistemäinen TR-osoite, mutta viiva-funktio ei saa geometriaa
          ;; laskettua, niin käytetään pistemäisen geometrian laskentaa
          (if (and
                (= (:aosa tr-osoite) (:losa tr-osoite))
                (= (:aet tr-osoite) (:let tr-osoite)))
            (tieverkko/hae-tr-osoite-ehka db alku-x alku-y threshold)
            tr-osoite))
        (when (and alku-x alku-y)
          (tieverkko/hae-tr-osoite-ehka db alku-x alku-y threshold)))
      (catch PSQLException e
        (log/error e "Sijainnin hakemisessa tapahtui poikkeus.")
        nil))))

(defn tee-geometria [{alku-x :x alku-y :y} {loppu-x :x loppu-y :y}]
  (if (and alku-x alku-y loppu-x loppu-y)
    (geo/geometry (LineString. (into-array [(Point. alku-x alku-y) (Point. loppu-x loppu-y)])))
    (when (and alku-x alku-y)
      (geo/geometry (Point. alku-x alku-y)))))

(defn hae-tierekisteriosoite-geometrialle [db geometria]
  (let [eka-piste (geo/ensimmaisen-pisteen-koordinaatit geometria)
        viimeinen-piste (geo/viimeisen-pisteen-koordinaatit geometria)]
    (hae-tierekisteriosoite db
      {:x (first eka-piste) :y (second eka-piste)} {:x (first viimeinen-piste) :y (second viimeinen-piste)})))
