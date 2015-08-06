(ns harja.pvm
  "Yleiset päivämääräkäsittelyn asiat asiakaspuolella."
  (:require [cljs-time.format :as df]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [harja.loki :refer [log]]
            [clojure.string :as str])
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

  IComparable
  (-compare [x y]
    (if (instance? DateTime y)
      (compare (tc/to-long x) (tc/to-long y))
      (throw (js/Error. (str "Ei voi verrata " x " (goog.date.DateTime) ja " y " (" (type y) ")")))))
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

(defn ennen? [eka toka]
  (if-not (or (nil? eka) (nil? toka))
    (t/before? eka toka)
    false))

(defn sama-tai-ennen? [eka toka]
  (if-not (or (nil? eka) (nil? toka))
    (or (t/before? eka toka) (= (millisekunteina eka) (millisekunteina toka)))
    false))

(defn jalkeen? [eka toka]
  (if-not (or (nil? eka) (nil? toka))
    (t/after? eka toka)
    false))

(defn sama-tai-jalkeen? [eka toka]
  (if-not (or (nil? eka) (nil? toka))
    (or (t/after? eka toka) (= (millisekunteina eka) (millisekunteina toka)))
    false))

(defn sama-kuukausi?
  "Tarkistaa onko ensimmäinen ja toinen päivämäärä saman vuoden samassa kuukaudessa."
  [eka toka]
  (and (= (t/year eka) (t/year toka))
       (= (t/month eka) (t/month toka))))

(defn valissa?
  "Tarkistaa onko annettu pvm alkupvm:n ja loppupvm:n välissä."
  [pvm alkupvm loppupvm]
  (and (sama-tai-jalkeen? pvm alkupvm) (sama-tai-ennen? pvm loppupvm)))

(def fi-pvm
  "Päivämäärän formatointi suomalaisessa muodossa"
  (df/formatter "dd.MM.yyyy"))

(def fi-pvm-parse
  "Parsintamuoto päivämäärästä, sekä nolla etuliite ja ilman kelpaa."
  (df/formatter "d.M.yyyy"))

(def fi-aika
  "Ajan formatointi suomalaisessa muodossa"
  (df/formatter "HH:mm:ss"))

(def fi-pvm-aika
  "Päivämäärän ja ajan formatointi suomalaisessa muodossa"
  (df/formatter "d.M.yyyy H:mm"))

(def fi-pvm-aika-sek
  "Päivämäärän ja ajan formatointi suomalaisessa muodossa"
  (df/formatter "dd.MM.yyyy HH:mm:ss"))

(defn pvm-aika
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa"
  [pvm]
  (df/unparse fi-pvm-aika pvm))

(defn pvm-aika-sek
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa sekuntitarkkuudella"
  [pvm]
  (df/unparse fi-pvm-aika-sek pvm))

(defn pvm
  "Formatoi päivämäärän suomalaisessa muodossa"
  [pvm]
  (df/unparse fi-pvm pvm))

(defn aika
  "Formatoi ajan suomalaisessa muodossa"
  [pvm]
  (df/unparse fi-aika pvm))

(defn ->pvm-aika [teksti]
  "Jäsentää tekstistä d.M.yyyy H:mm muodossa olevan päivämäärän ja ajan. Jos teksti ei ole oikeaa muotoa, palauta nil."
  (try
    (df/parse fi-pvm-aika (str/trim teksti))
    (catch js/Error e
      nil)))

(defn ->pvm-aika-sek [teksti]
  "Jäsentää tekstistä dd.MM.yyyy HH:mm:ss muodossa olevan päivämäärän ja ajan. Tämä on koneellisesti formatoitua päivämäärää varten, älä käytä ihmisen syöttämän tekstin jäsentämiseen!"
  (df/parse fi-pvm-aika-sek teksti))

(defn ->pvm [teksti]
  "Jäsentää tekstistä dd.MM.yyyy muodossa olevan päivämäärän. Jos teksti ei ole oikeaa muotoa, palauta nil."
  (try
    (df/parse fi-pvm-parse teksti)
    (catch js/Error e
      #_(.log js/console "E: " e)
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

(defn kuukauden-lyhyt-nimi [kk]
  (case kk
    1 "tammi"
    2 "helmi"
    3 "maalis"
    4 "huhti"
    5 "touko"
    6 "kesä"
    7 "heinä"
    8 "elo"
    9 "syys"
    10 "loka"
    11 "marras"
    12 "joulu"
    "tuntematon"))
    
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

(defn kuukausi
  "Palauttaa annetun DateTime kuukauden."
  [pvm]
  (t/month pvm))

(defn hoitokauden-edellinen-vuosi-kk [vuosi-kk]
  (let [vuosi (first vuosi-kk)
        kk (second vuosi-kk)
        ed-vuosi (if (= 1 kk)
                   (dec vuosi)
                   vuosi)
        ed-kk (if (= 1 kk)
                12
                (dec kk))
        ]
    [ed-vuosi ed-kk]))

(defn aikana [dt tunnit minuutit sekunnit millisekunnit]
  (doto (goog.date.DateTime.)
    (.setYear (.getYear dt))
    (.setMonth (.getMonth dt))
    (.setDate (.getDate dt))
    (.setHours tunnit)
    (.setMinutes minuutit)
    (.setSeconds sekunnit)
    (.setMilliseconds millisekunnit)))
  
(defn kuukauden-aikavali
  "Palauttaa kuukauden aikavälin vektorina [alku loppu], jossa alku on kuukauden ensimmäinen päivä
kello 00:00:00.000 ja loppu on kuukauden viimeinen päivä kello 23:59:59.999 ."
  [dt]
  (let [alku (aikana (t/first-day-of-the-month dt)
                     0 0 0 0)
        loppu (aikana (t/last-day-of-the-month dt)
                      23 59 59 999)]
    ;; aseta aika
    [alku loppu]))
        

(defn hoitokauden-kuukausivalit
  "Palauttaa vektorin kuukauden aikavälejä (ks. kuukauden-aikavali funktio) annetun hoitokauden
  jokaiselle kuukaudelle."
  [[alkupvm loppupvm]]
  (let [alku (t/first-day-of-the-month alkupvm)]
    (loop [kkt [(kuukauden-aikavali alkupvm)]
           kk (t/plus alku (t/months 1))]
      (if (t/after? kk loppupvm)
        kkt
        (recur (conj kkt
                     (kuukauden-aikavali kk))
               (t/plus kk (t/months 1)))))))

