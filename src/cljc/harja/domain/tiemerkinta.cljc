(ns harja.domain.tiemerkinta
  "Tiemerkinnän asiat"
  (:require
    [harja.pvm :as pvm]
    #?(:cljs [cljs-time.core :as t])
    #?(:cljs [cljs-time.extend])
    #?(:clj
       [clj-time.core :as t])
    [clojure.string :as str]))

(def tiemerkinnan-suoritusaika-paivina (t/days 14))

(defn tiemerkinta-oltava-valmis [tiemerkintapvm]
  (when (some? tiemerkintapvm)
    (t/plus tiemerkintapvm tiemerkinnan-suoritusaika-paivina)))

;; Tiemerkinnän takarajan laskennassa täytyy huomioida arkipyhät
;; Käytännössä kiinnostava aika on sulan maan aika, joten tässä kovakoodataan
;; lähivuosien helatorstait, vapunpäivät sekä juhannusaatot ja -päivät.

;; https://fi.wikipedia.org/wiki/Helatorstai
(def helatorstait-2022-2032
  #{(pvm/->pvm "26.5.2022")
    (pvm/->pvm "18.5.2023")
    (pvm/->pvm "9.5.2024")
    (pvm/->pvm "29.5.2025")
    (pvm/->pvm "14.5.2026")
    (pvm/->pvm "6.5.2027")
    (pvm/->pvm "25.5.2028")
    (pvm/->pvm "10.5.2029")
    (pvm/->pvm "30.5.2030")
    (pvm/->pvm "22.5.2031")
    (pvm/->pvm "6.5.2032")})

;; Juhannusaatot eivät ole virallisia pyhäpäiviä
(def juhannusaatot-ja-paivat-2022-2032
  ;;         JUHANNUSAATOT          JUHANNUSPÄIVÄT
  #{(pvm/->pvm "24.6.2022") (pvm/->pvm "25.6.2022")
    (pvm/->pvm "23.6.2023") (pvm/->pvm "24.6.2023")
    (pvm/->pvm "21.6.2024") (pvm/->pvm "22.6.2024")
    (pvm/->pvm "20.6.2025") (pvm/->pvm "21.6.2025")
    (pvm/->pvm "19.6.2026") (pvm/->pvm "20.6.2026")
    (pvm/->pvm "25.6.2027") (pvm/->pvm "26.6.2027")
    (pvm/->pvm "23.6.2028") (pvm/->pvm "24.6.2028")
    (pvm/->pvm "22.6.2029") (pvm/->pvm "23.6.2029")
    (pvm/->pvm "21.6.2030") (pvm/->pvm "22.6.2030")
    (pvm/->pvm "21.6.2031") (pvm/->pvm "22.6.2031")
    (pvm/->pvm "19.6.2032") (pvm/->pvm "20.6.2032")})

(def vappupaivat-2022-2032
  (set
    (for [vuosi (range 2022 2032)]
      (pvm/->pvm (str "1.5." vuosi)))))

;; Ne arkipyhät, joiden osalta tiemerkinnän 14/21 vrk:n takarajan
;; laskenta tarvitsee erikoiskäsittelyä
(def tiemerkinnan-vapaapaivat-2022-2032
  (clojure.set/union helatorstait-2022-2032
                     juhannusaatot-ja-paivat-2022-2032
                     vappupaivat-2022-2032))

(def merkinta-vaihtoehdot
  ["massa" "maali" "muu"])

(def jyrsinta-vaihtoehdot
  ["ei jyrsintää" "keski" "reuna" "keski- ja reuna"])

(defn merkinta-ja-jyrsinta-fmt
  ([arvo]
   (merkinta-ja-jyrsinta-fmt arvo nil))
  ([arvo arvon-puuttuessa]
   (if arvo
     (str/capitalize arvo)
     (or arvon-puuttuessa ""))))