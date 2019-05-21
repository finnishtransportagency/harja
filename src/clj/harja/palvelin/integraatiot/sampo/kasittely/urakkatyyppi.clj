(ns harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]))

(defn vaylamuoto [tyypit]
  (if (empty? tyypit)
    "t"
    (let [vaylamuoto (str/upper-case (subs tyypit 0 1))]
      (case vaylamuoto
        "T" "t"
        "R" "r"
        "V" "v"
        "t"))))

(defn- urakan-alityyppi [tyypit vaihtoehdot oletus-fn]
  (if (< 2 (count tyypit))
    (let [alityyppiavain (keyword (str/upper-case (subs tyypit 2 3)))
          alityyppi (alityyppiavain vaihtoehdot)]
      (if alityyppi
        alityyppi
        (oletus-fn)))
    (oletus-fn)))

;; teiden-hoito on syksyllä 2019 alkava uusi urakkatyyppi
(defn- tievaylaurakan-alityyppi [tyypit oletus-fn]
  (let [vaihtoehdot {:V "valaistus"
                     :P "paallystys"
                     :T "tiemerkinta"
                     :S "siltakorjaus"
                     :L "tekniset-laitteet"
                     :J "teiden-hoito"}]
    (urakan-alityyppi tyypit vaihtoehdot oletus-fn)))

(defn- tievaylaurakan-tyyppi [tyypit urakkatyyppi]
  (let [virhe (format "Samposta luettiin sisään ylläpitourakka tuntemattomalla alityypillä. Tyypit: %s." tyypit)]
    (case urakkatyyppi
      "H" (tievaylaurakan-alityyppi tyypit #(str "hoito"))
      "Y" (tievaylaurakan-alityyppi tyypit #(do (log/error virhe) "paallystys"))
      "hoito")))

(defn vesivaylahoitourakan-alityyppi [tyypit oletus-fn]
  (let [vaihtoehdot {:H "vesivayla-hoito"
                     :K "vesivayla-kanavien-hoito"}]
    (urakan-alityyppi tyypit vaihtoehdot oletus-fn)))

(defn vesivaylayllapitourakan-alityyppi [tyypit oletus-fn]
  (let [vaihtoehdot {:R "vesivayla-ruoppaus"
                     :T "vesivayla-turvalaitteiden-korjaus"
                     :K "vesivayla-kanavien-korjaus"}]
    (urakan-alityyppi tyypit vaihtoehdot oletus-fn)))

(defn- vesivaylaurakkan-tyyppi [tyypit tunniste]
  (let [virhe (format "Samposta luettiin sisään vesiväyläurakka tuntemattomalla alityypillä. Tyypit: %s." tyypit)]
    (case tunniste
      "H" (vesivaylahoitourakan-alityyppi tyypit #(do (log/error virhe) "vesivayla-hoito"))
      "Y" (vesivaylayllapitourakan-alityyppi tyypit #(do (log/error virhe) "vesivayla-kanavien-korjaus"))
      "vesivaylahoito")))

(defn urakkatyyppi [tyypit]
  ;; Ensimmäinen kirjain kertoo yläkategorian (tie, rata, vesi)
  ;; Toinen kirjain määrittää kuuluuko urakka hoitoon vai ylläpitoon
  ;; Kolmas kirjain määrittää lopulta palautettavan urakkatyypin (hoito, päällystys, tiemerkintä...)
  (if (< 1 (count tyypit))
    (let [vaylamuoto (vaylamuoto tyypit)
          urakkatyyppi (str/upper-case (subs tyypit 1 2))]
      (case vaylamuoto
        "t" (tievaylaurakan-tyyppi tyypit urakkatyyppi)
        "v" (vesivaylaurakkan-tyyppi tyypit urakkatyyppi)))
    "hoito"))

(defn rakenna-sampon-tyyppi [urakkatyyppi]
  (case urakkatyyppi "valaistus" "TYV"
                     "paallystys" "TYP"
                     "tiemerkinta" "TYT"
                     "siltakorjaus" "TYS"
                     "tekniset-laitteet" "TYL"
                     "hoito" "TH"
                     "teiden-hoito" "THJ"
                     "vesivayla-hoito" "VH"
                     "vesivayla-ruoppaus" "VYR"
                     "vesivayla-turvalaitteiden-korjaus" "VYT"
                     "vesivayla-kanavien-hoito" "VHK"
                     "vesivayla-kanavien-korjaus" "VYK"
                     (throw (new RuntimeException (str "Tuntematon urakkatyyppi: " urakkatyyppi)))))
