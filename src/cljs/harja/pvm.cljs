(ns harja.pvm
  "Yleiset päivämääräkäsittelyn asiat asiakaspuolella."
  (:require [cljs-time.format :as df]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [harja.loki :refer [log]])
  (:import (goog.date DateTime)))


;; Toteutetaan hash ja equiv, jotta voimme käyttää avaimena hashejä
(extend-type DateTime
  IHash
  (-hash [o]
    (hash (tc/to-long o)))

  IEquiv
  (-equiv [o other]
    (and (instance? DateTime other)
         (= (tc/to-long o) (tc/to-long other))))
  )

(defn millisekunteina [pvm]
  (tc/to-long pvm))

(defn nyt []
  (DateTime.))

(defn luo-pvm [vuosi kk pv]
  (DateTime. vuosi kk pv 0 0 0 0))

(defn sama-pvm? [eka toka]
  (and (= (t/year eka) (t/year toka))
       (= (t/month eka) (t/month toka))
       (= (t/day eka) (t/day toka))))
  

(def fi-pvm
  "Päivämäärän formatointi suomalaisessa muodossa"
  (df/formatter "dd.MM.yyyy"))

(def fi-pvm-parse
  "Parsintamuoto päivämäärästä, sekä nolla etuliite ja ilman kelpaa."
  (df/formatter "d.M.yyyy"))

(def fi-pvm-aika
  "Päivämäärän ja ajan formatointi suomalaisessa muodossa"
  (df/formatter "dd.MM.yyyy HH:mm:ss"))

(defn pvm-aika
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa"
  [pvm]
  (df/unparse fi-pvm-aika pvm))

(defn pvm
  "Formatoi päivämäärän suomalaisessa muodossa"
  [pvm]
  (df/unparse fi-pvm pvm))

(defn ->pvm-aika [teksti]
  "Jäsentää tekstistä dd.MM.yyyy HH:mm:ss muodossa olevan päivämäärän ja ajan. Jos teksti ei ole oikeaa muotoa, palauta nil."
  (try
    (df/parse fi-pvm-aika teksti)
    (catch js/Error e
      nil)))
                 
(defn ->pvm [teksti]
  "Jäsentää tekstistä dd.MM.yyyy muodossa olevan päivämäärän. Jos teksti ei ole oikeaa muotoa, palauta nil."
  (try
    (df/parse fi-pvm-parse teksti)
    (catch js/Error e
      (.log js/console "E: " e)
      nil)))

(defn kuukauden-nimi [kk]
  (case kk
    1 "tammikuu"
    2 "helmikuu"
    3 "maaliskuu"
    4 "huhtikuu"
    5 "toukokuu"
    6 "kesäkuu"
    7 "heinäkuu"
    8 "elokuu"
    9 "syyskuu"
    10 "lokakuu"
    11 "marraskuu"
    12 "joulukuu"
    "kk ei välillä 1-12"))

;; hoidon alueurakoiden päivämääräapurit
(defn vuoden-eka-pvm
  "Palauttaa vuoden ensimmäisen päivän 1.1.vuosi"
  [vuosi]
  (luo-pvm vuosi 0 1))

(defn vuoden-viim-pvm
  "Palauttaa vuoden viimeisen päivän 31.12.vuosi"
  [vuosi]
  (luo-pvm vuosi 11 31))

(defn hoitokauden-alkupvm
  "Palauttaa hoitokauden alkupvm:n 1.10.vuosi"
  [vuosi]
  (luo-pvm vuosi 9 1))

(defn hoitokauden-loppupvm
  "Palauttaa hoitokauden loppupvm:n 30.9.vuosi"
  [vuosi]
  (luo-pvm vuosi 8 30))

(defn vuosi
  "Palauttaa annetun DateTimen vuoden, esim 2015."
  [pvm]
  (t/year pvm))
  
