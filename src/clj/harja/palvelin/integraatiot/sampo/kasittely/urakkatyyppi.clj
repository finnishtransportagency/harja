(ns harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn paattele-alityyppi [tyypit]
  (when (< 2 (count tyypit))
    (let [tunniste (str/upper-case (subs tyypit 2 3))]
      (case tunniste
        "V" "valaistus"
        "P" "paallystys"
        "T" "tiemerkinta"
        "S" "siltakorjaus"
        nil))))

(defn paattele-yllapidon-alityyppi [tyypit]
  (if-let [alityyppi (paattele-alityyppi tyypit)]
    alityyppi
    (do (log/error "Samposta luettiin sisään ylläpidon urakka tuntemattomalla alityypillä")
        "paallystys")))

(defn paattele-hoidon-alityyppi [tyypit]
  (if-let [alityyppi (paattele-alityyppi tyypit)]
    alityyppi
    "hoito"))

(defn paattele-urakkatyyppi [tyypit]
  ;; Ensimmäinen kirjain kertoo yläkategorian (tie, rata, vesi)
  ;; Toinen kirjain määrittää kuuluuko urakka hoitoon vai ylläpitoon
  ;; Kolmas kirjain määrittää lopulta palautettavan urakkatyypin (hoito, päällystys, tiemerkintä...)
  (if (< 1 (count tyypit))
    (let [tunniste (str/upper-case (subs tyypit 1 2))]
      (case tunniste
        "H" (paattele-hoidon-alityyppi tyypit)
        "Y" (paattele-yllapidon-alityyppi tyypit)
        "hoito"))
    "hoito"))

(defn paattele-liikennemuoto [tyypit]
  (if (empty? tyypit)
    "t"
    (let [tunniste (str/upper-case (subs tyypit 0 1))]
      (case tunniste
        "T" "t"
        "R" "r"
        "V" "v"
        "t"))))





