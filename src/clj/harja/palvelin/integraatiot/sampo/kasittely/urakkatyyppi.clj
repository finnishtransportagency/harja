(ns harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi
  (:require [clojure.string :as str]))

(defn paattele-yllapidon-tyyppi [tyypit]
  (if (< 2 (count tyypit))
    (let [tunniste (str/upper-case (subs tyypit 2 3))]
      (case tunniste
        "V" "valaistus"
        "P" "paallystys"
        "T" "tiemerkinta"
        "S" "siltakorjaus"
        "hoito"))
    "hoito"))

(defn paattele-urakkatyyppi [tyypit]
  ;; Ensimmäinen kirjain kertoo yläkategorian (tie, rata, vesi)
  ;; Toinen kirjain määrittää kuuluuko urakka hoitoon vai ylläpitoon
  ;; Kolmas kirjain määrittää lopulta palautettavan urakkatyypin (hoito, päällystys, tiemerkintä...)
  (if (< 1 (count tyypit))
    (let [tunniste (str/upper-case (subs tyypit 1 2))]
      (case tunniste
        "H" "hoito"
        "Y" (paattele-yllapidon-tyyppi tyypit)
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





