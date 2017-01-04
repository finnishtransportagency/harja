(ns harja-laadunseuranta.tiedot.fmt
  (:require [ol.proj :as ol-proj]
            [ol.extent :as ol-extent]
            [cljs-time.format :as time-fmt]
            [clojure.string :as str]))

(defn string->numero [arvo]
  (js/parseFloat (str/replace arvo "," ".")))

(defn n-desimaalia [arvo n]
  (string->numero (.toFixed arvo n)))

(def pvm-formatter (time-fmt/formatter "dd.MM.yyyy"))
(def klo-formatter (time-fmt/formatter "HH:mm"))

(defn pvm [aikaleima]
  (time-fmt/unparse pvm-formatter aikaleima))

(defn klo [aikaleima]
  (time-fmt/unparse klo-formatter aikaleima))

(defn pvm-klo [aikaleima]
  (str (pvm aikaleima) " " (klo aikaleima)))

;; FIXME KOPIOITU BASE-PROJEKTISTA, KORVAA KUN YHTEINEN KOODIPESÄ TEHTY
(defn tierekisteriosoite-tekstina
  "Näyttää tierekisteriosoitteen muodossa tie / aosa / aet / losa / let
   Vähintään tie, aosa ja aet tulee löytyä osoitteesta, jotta se näytetään

   Optiot on mappi, jossa voi olla arvot:
   teksti-ei-tr-osoitetta?        Näyttää tekstin jos TR-osoite puuttuu. Oletus true.
   teksti-tie?                    Näyttää sanan 'Tie' osoitteen edessä. Oletus true."
  ([tr] (tierekisteriosoite-tekstina tr {}))
  ([tr optiot]
   (let [tie-sana (let [sana "Tie "]
                    (if (nil? (:teksti-tie? optiot))
                      sana
                      (when (:teksti-tie? optiot) sana)))
         tie (or (:numero tr) (:tr-numero tr) (:tie tr))
         alkuosa (or (:alkuosa tr) (:tr-alkuosa tr) (:aosa tr))
         alkuetaisyys (or (:alkuetaisyys tr) (:tr-alkuetaisyys tr) (:aet tr))
         loppuosa (or (:loppuosa tr) (:tr-loppuosa tr) (:losa tr))
         loppuetaisyys (or (:loppuetaisyys tr) (:tr-loppuetaisyys tr) (:let tr))
         ei-tierekisteriosoitetta (if (or (nil? (:teksti-ei-tr-osoitetta? optiot))
                                          (boolean (:teksti-ei-tr-osoitetta? optiot)))
                                    "Ei tierekisteriosoitetta"
                                    "")]
     ;; Muodosta teksti
     (str (if tie
            (str tie-sana
                 tie
                 (when (and alkuosa alkuetaisyys)
                   (str " / " alkuosa " / " alkuetaisyys))
                 (when (and alkuosa alkuetaisyys loppuosa loppuetaisyys)
                   (str " / " loppuosa " / " loppuetaisyys)))
            ei-tierekisteriosoitetta)))))