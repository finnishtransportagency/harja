(ns harja.domain.tiemerkinta
  "Tiemerkinnän asiat"
  (:require
    [harja.pvm :as pvm]
    #?(:cljs [cljs-time.core :as t])
    #?(:cljs [cljs-time.predicates :as time-predicates])
    #?(:cljs [cljs-time.extend])
    #?(:clj [clj-time.core :as t])
    #?(:clj [clj-time.predicates :as time-predicates])
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


(def tiemerkinnan-kesto-lyhyt 14)
(def tiemerkinnan-kesto-pitka 21)

;; Jos kyseessä on maalivaatimustie (merkintä = maali), välitavoite aina 21vrk
;; Jos kohteelle tehdään jyrsintää (jyrsintä != ei jyrsintää), välitavoite aina 21vrk
;; Jos massavaatimustie (merkintä = massa), mutta ei jyrsintää, välitavoite on 14vrk
;; Jos merkintä on muu kuin maali tai massa, välitavoite pvm:n voi määrittää käsin. 14 vrk default
(defn tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan
  "Laskee tiemerkinnän tavoiteajan merkintä- ja jyrsintätietojen perusteella."
  [kohde]
  (cond
    (or
      (= (:merkinta kohde) "maali")
      (and
        (some? (:jyrsinta kohde))
        (not= (:jyrsinta kohde) "ei jyrsintää")))
    tiemerkinnan-kesto-pitka

    (and (= (:merkinta kohde) "massa")
         (or (nil? (:jyrsinta kohde))
             (= (:jyrsinta kohde) "ei jyrsintää")))
    tiemerkinnan-kesto-lyhyt

    :else
    tiemerkinnan-kesto-lyhyt))

;; Takarajan laskenta aloitetaan :valmis-tiemerkintaan pvm:ää seuraavasta arkipäivästä
;; Jos :valmis-tiemerkintaan ma-to tai su, päivien laskenta alkaa + 1vrk
;; Jos :valmis-tiemerkintaan pe, päivien laskenta alkaa + 3vrk
;; Jos :valmis-tiemerkintaan la, päivien laskenta alkaa + 2vrk
;; Kuitenkin siten, että huomioidaan arkipyhät (helatorstai, vappu, juhannus)
#?(:clj
   (defn tiemerkinnan-keston-alkupvm
     "Laskee tiemerkinnän keston laskennan alkupvm:n sääntöjen mukaan. Sisään date time"
     [kohde]
     (assert (:valmis-tiemerkintaan kohde) "Annettava tiemerkintä voidaan aloittaa -päivämäärä")
     (let [voidaan-aloittaa (t/plus (pvm/joda-timeksi (:valmis-tiemerkintaan kohde))
                                    (t/days 1)) ;; Lisätään yksi päivä, jotta alla tapahtuva viikonpäivän tunnistus toimii oikein (se sekoittuu aikavyöhykkeestä johtuvaan klo 21 tai 22 aikaan päivämäärärajalla)
           laskettu-alkupvm-datena (cond
                                     (time-predicates/friday? voidaan-aloittaa)
                                     (t/plus voidaan-aloittaa (t/days 3))

                                     (time-predicates/saturday? voidaan-aloittaa)
                                     (t/plus voidaan-aloittaa (t/days 2))

                                     ;; ma-to tai su
                                     :else
                                     (t/plus voidaan-aloittaa (t/days 1)))
           ;; symmetrisesti vähennetään yksi päivä *)
           pvm-korjattuna-datena (pvm/dateksi (t/minus laskettu-alkupvm-datena (t/days 1)))]

       pvm-korjattuna-datena)))

;; Tiemerkinnän takarajan laskennan logiikka:
;; https://miro.com/app/board/uXjVOU_CU4k=/?moveToWidget=3458764517229017754&cot=14
#?(:clj
   (defn laske-tiemerkinnan-takaraja
     [kohde]
     ;; Käsin annettu takaraja hyväksytään
     (if (and (:valmis-tiemerkintaan kohde)
              (not (:tiemerkinnan-takaraja-annettu-kasin? kohde)))
       (let [laskenta-alkaa-pvm (pvm/joda-timeksi (tiemerkinnan-keston-alkupvm kohde))
             sallittu-kesto-paivina (tiemerkinnan-kesto-merkinnan-ja-jyrsinnan-mukaan kohde)
             takaraja (loop [iter 0
                             laskettava-pvm laskenta-alkaa-pvm]
                        (if (< iter sallittu-kesto-paivina)
                          (recur (inc iter)
                                 (if-not (tiemerkinnan-vapaapaivat-2022-2032 laskettava-pvm)
                                   (t/plus laskettava-pvm (t/days 1))
                                   laskettava-pvm))
                          (pvm/dateksi laskettava-pvm)))]
         (assoc kohde :aikataulu-tiemerkinta-takaraja takaraja))
       kohde)))