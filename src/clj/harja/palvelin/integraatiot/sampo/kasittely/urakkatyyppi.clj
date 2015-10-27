(ns harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi)

(defn paattele-yllapidon-tyyppi [tyypit]
  (if (< 2 (count tyypit))
    (let [tunniste (subs tyypit 2 3)]
      (case tunniste
        "V" "valaistus"
        "P" "paallystys"
        "T" "tiemerkinta"
        "S" "siltakorjaus"
        "hoito"))
    "hoito"))

(defn paattele-urakkatyyppi [tyypit]
  (if (< 1 (count tyypit))
    (let [tunniste (subs tyypit 1 2)]
      (case tunniste
        "H" "hoito"
        "Y" (paattele-yllapidon-tyyppi tyypit)
        "hoito"))
    "hoito"))

(defn paattele-liikennemuoto [tyypit]
  (if (empty? tyypit)
    "t"
    (let [tunniste (subs tyypit 0 1)]
      (case tunniste
        "T" "t"
        "R" "r"
        "V" "v"
        "t"))))





