(ns harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn paattele-alityyppi [tyypit default-fn]
  (if (< 2 (count tyypit))
    (let [tunniste (str/upper-case (subs tyypit 2 3))]
      (case tunniste
        "V" "valaistus"
        "P" "paallystys"
        "T" "tiemerkinta"
        "S" "siltakorjaus"
        "L" "tekniset-laitteet"
        (default-fn)))
    (default-fn)))

(defn paattele-urakkatyyppi [tyypit]
  ;; Ensimmäinen kirjain kertoo yläkategorian (tie, rata, vesi)
  ;; Toinen kirjain määrittää kuuluuko urakka hoitoon vai ylläpitoon
  ;; Kolmas kirjain määrittää lopulta palautettavan urakkatyypin (hoito, päällystys, tiemerkintä...)
  (if (< 1 (count tyypit))
    (let [tunniste (str/upper-case (subs tyypit 1 2))
          virhe (format "Samposta luettiin sisään ylläpidon urakka tuntemattomalla alityypillä. Tyypit: %s." tyypit)]
      (case tunniste
        "H" (paattele-alityyppi tyypit #(str "hoito"))
        "Y" (paattele-alityyppi tyypit #(do (log/error virhe) "paallystys"))
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

(defn rakenna-sampon-tyyppi [urakkatyyppi]
  (case urakkatyyppi "valaistus" "TYV"
                     "paallystys" "TYP"
                     "tiemerkinta" "TYT"
                     "siltakorjaus" "TYS"
                     "tekniset-laitteet" "TYL"
                     "hoito" "TH"
                     "vesivayla-hoito" "VH"
                     "vesivayla-ruoppaus" "VYR"
                     "vesivayla-turvalaitteiden-korjaus" "VYT"
                     "vesivayla-kanavien-hoito" "VHK"
                     "vesivayla-kanavien-korjaus" "VYK"
                     (throw (new RuntimeException (str "Tuntematon urakkatyyppi: " urakkatyyppi)))))





