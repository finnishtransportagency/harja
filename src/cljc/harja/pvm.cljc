(ns harja.pvm
  "Yleiset päivämääräkäsittelyn asiat.
  Frontin puolella käytetään yleisesti tyyppiä goog.date.DateTime.
  Backendissä käytetään yleisesti org.joda.time:n pvm-tyyppejä (muutamat java.util.Date-poikkeukset dokumentoitu erikseen)"
  (:require
    #?(:cljs [cljs-time.format :as df])
    #?(:cljs [cljs-time.core :as t])
    #?(:cljs [cljs-time.coerce :as tc])
    #?(:cljs [harja.loki :refer [log]])
    #?(:cljs [cljs-time.extend])
    #?(:clj [clj-time.format :as df])
    #?(:clj
            [clj-time.core :as t])
    #?(:clj
            [clj-time.coerce :as tc])
    #?(:clj
            [clj-time.local :as l])
    #?(:clj
            [taoensso.timbre :as log])
            [clojure.string :as str])

  #?(:cljs (:import (goog.date DateTime))
     :clj
     (:import (java.util Calendar Date)
              (java.text SimpleDateFormat)
              (org.joda.time DateTimeZone))))


#?(:cljs
   (defrecord Aika [tunnit minuutit sekunnit]))

#?(:cljs
   ;; Toteutetaan hash ja equiv, jotta voimme käyttää avaimena hashejä
   (extend-type DateTime
     IHash
     (-hash [o]
       (hash (tc/to-long o)))))

#?(:clj
   (defn joda-time? [pvm]
     (or (instance? org.joda.time.DateTime pvm)
         (instance? org.joda.time.LocalDate pvm)
         (instance? org.joda.time.LocalDateTime pvm))))

#?(:clj
   (def suomen-aikavyohyke (DateTimeZone/forID "Europe/Helsinki")))

#?(:clj
   (defn suomen-aikavyohykkeessa
     "Antaa joda daten suomen aikavyöhykkeellä"
     [joda-time]
     (t/from-time-zone joda-time suomen-aikavyohyke)))

#?(:clj
   (defn suomen-aikavyohykkeeseen
     "Antaa joda daten suomen aikavyöhykkeellä"
     [joda-time]
     (t/to-time-zone joda-time suomen-aikavyohyke)))

(defn aikana [dt tunnit minuutit sekunnit millisekunnit]
  #?(:cljs
     (goog.date.DateTime.
       (.getYear dt)
       (.getMonth dt)
       (.getDate dt)
       tunnit
       minuutit
       sekunnit
       millisekunnit)

     :clj
     (cond (instance? java.util.Date dt)
           (.getTime (doto (Calendar/getInstance)
                       (.setTime dt)
                       (.set Calendar/HOUR_OF_DAY tunnit)
                       (.set Calendar/MINUTE minuutit)
                       (.set Calendar/SECOND sekunnit)
                       (.set Calendar/MILLISECOND millisekunnit)))
           (joda-time? dt)
           (t/local-date-time
             (t/year dt)
             (t/month dt)
             (t/day dt)
             tunnit
             minuutit
             sekunnit
             millisekunnit))))

(defn paivan-alussa [dt]
  (aikana dt 0 0 0 0))

(defn paivan-alussa-opt [dt]
  (when dt (paivan-alussa dt)))

(defn paivan-lopussa [dt]
  (aikana dt 23 59 59 999))

(defn paivan-lopussa-opt [dt]
  (when dt (paivan-lopussa dt)))

(defn keskipaiva [dt]
  (aikana dt 12 0 0 0))

(defn keskipaiva-opt [dt]
  (when dt (keskipaiva dt)))

(defn millisekunteina [pvm]
  (tc/to-long pvm))

#?(:clj
   (defn joda-timeksi [dt]
     (cond
       (joda-time? dt)
       dt

       (instance? java.util.Date dt)
       (tc/from-date dt)

       (instance? java.sql.Date dt)
       (tc/from-sql-date dt)

       (instance? java.sql.Timestamp dt)
       (tc/from-sql-time dt))))

(defn nyt
  "Frontissa palauttaa goog.date.Datetimen (käyttäjän laitteen aika)
  Backendissä palauttaa java.util.Daten"
  []
  #?(:cljs (DateTime.)
     :clj (Date.)))

(defn pvm?
  [pvm]
  #?(:cljs (instance? DateTime pvm)
     :clj  (joda-time? pvm)))

(defn luo-pvm
  "Frontissa palauttaa goog.date.Datetimen
  Backendissä palauttaa java.util.Daten"
  [vuosi kk pv]
  #?(:cljs (DateTime. vuosi kk pv 0 0 0 0)
     :clj (Date. (- vuosi 1900) kk pv)))

(defn sama-pvm? [eka toka]
  (if-not (and eka toka)
    false
    (and (= (t/year eka) (t/year toka))
         (= (t/month eka) (t/month toka))
         (= (t/day eka) (t/day toka)))))


#?(:cljs
   (defn ennen? [eka toka]
     (if (and eka toka)
       (t/before? eka toka)
       false))

   :clj
   (defn ennen? [eka toka]
     (if (and eka toka)
       (cond
         (or (instance? java.util.Date eka)
             (instance? java.util.Date toka))
         (.before eka toka)
         (or (joda-time? eka)
             (joda-time? toka))
         (t/before? eka toka))
       false)))


(defn sama-tai-ennen?
  "Tarkistaa, onko ensimmäisenä annettu pvm sama tai ennen toista annettua pvm:ää.
  Mahdollisuus verrata ilman kellonaikaa, joka on oletuksena true."
  ([eka toka] (sama-tai-ennen? eka toka true))
  ([eka toka ilman-kellonaikaa?]
   (if (and eka toka)
     (let [eka (if ilman-kellonaikaa? (paivan-alussa eka) eka)
           toka (if ilman-kellonaikaa? (paivan-alussa toka) toka)]
       (or (ennen? eka toka)
           (= (millisekunteina eka) (millisekunteina toka))))
     false)))

(defn jalkeen? [eka toka]
  (if (and eka toka)
    (t/after? eka toka)
    false))

(defn sama-tai-jalkeen?
  "Tarkistaa, onko ensimmäisenä annettu pvm sama tai toisena annettun pvm:n jälkeen.
  Mahdollisuus verrata ilman kellonaikaa, joka on oletuksena true."
  ([eka toka] (sama-tai-jalkeen? eka toka true))
  ([eka toka ilman-kellonaikaa?]
   (if (and eka toka)
     (let [eka (if ilman-kellonaikaa? (paivan-alussa eka) eka)
           toka (if ilman-kellonaikaa? (paivan-alussa toka) toka)]
       (or (jalkeen? eka toka)
           (= (millisekunteina eka) (millisekunteina toka))))
     false)))

(defn sama-kuukausi?
  "Tarkistaa onko ensimmäinen ja toinen päivämäärä saman vuoden samassa kuukaudessa."
  [eka toka]
  (if-not (and eka toka)
    false
    (and (= (t/year eka) (t/year toka))
         (= (t/month eka) (t/month toka)))))

(defn valissa?
  "Tarkistaa, onko annettu pvm alkupvm:n ja loppupvm:n välissä.
  Palauttaa true myös silloin jos pvm on sama kuin alku- tai loppupvm.
  Mahdollisuus verrata ilman kellonaikaa, joka on oletuksena true."
  ([pvm alkupvm loppupvm] (valissa? pvm alkupvm loppupvm true))
  ([pvm alkupvm loppupvm ilman-kellonaikaa?]
   (and (sama-tai-jalkeen? pvm alkupvm ilman-kellonaikaa?)
        (sama-tai-ennen? pvm loppupvm ilman-kellonaikaa?))))

(defn- luo-format [str]
  #?(:cljs (df/formatter str)
     :clj (SimpleDateFormat. str)))
(defn- formatoi [format date]
  #?(:cljs (df/unparse format date)
     :clj (.format format date)))
(defn parsi [format teksti]
  #?(:cljs (df/parse-local format teksti)
     :clj (.parse format teksti)))

(def fi-pvm
  "Päivämäärän formatointi suomalaisessa muodossa"
  (luo-format "dd.MM.yyyy"))

(def fi-pvm-parse
  "Parsintamuoto päivämäärästä, sekä nolla etuliite ja ilman kelpaa."
  (luo-format "d.M.yyyy"))

(def fi-aika
  "Ajan formatointi suomalaisessa muodossa"
  (luo-format "HH:mm"))

(def fi-aika-sek
  "Ajan formatointi suomalaisessa muodossa"
  (luo-format "HH:mm:ss"))

(def fi-pvm-aika
  "Päivämäärän ja ajan formatointi suomalaisessa muodossa"
  (luo-format "d.M.yyyy H:mm"))

(def fi-pvm-aika-sek
  "Päivämäärän ja ajan formatointi suomalaisessa muodossa"
  (luo-format "dd.MM.yyyy HH:mm:ss"))

(def iso8601-aikaleimalla
  (luo-format "yyyy-MM-dd'T'HH:mm:ss.S"))

(def kuukausi-ja-vuosi-fmt-valilyonnilla
  (luo-format "MM / yy"))

(def kuukausi-ja-vuosi-fmt
  (luo-format "MM/yy"))

(def kokovuosi-ja-kuukausi-fmt
  (luo-format "yyyy/MM"))

#?(:clj (def pgobject-format
          (luo-format "yyyy-MM-dd HH:mm:ss")))

(defn pvm-aika
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa"
  [pvm]
  (formatoi fi-pvm-aika pvm))

(defn pvm-aika-opt
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa tai tyhjä, jos nil."
  [p]
  (if p
    (pvm-aika p)
    ""))

(defn pvm-aika-sek
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa sekuntitarkkuudella"
  [pvm]
  (formatoi fi-pvm-aika-sek pvm))

(defn pvm
  "Formatoi päivämäärän suomalaisessa muodossa"
  [pvm]
  (formatoi fi-pvm pvm))

(defn pvm-opt
  "Formatoi päivämäärän suomalaisessa muodossa tai tyhjä, jos nil."
  [p]
  (if p
    (pvm p)
    ""))

(defn aika
  "Formatoi ajan suomalaisessa muodossa"
  [pvm]
  (formatoi fi-aika pvm))

(defn aika-sek
  [pvm]
  (formatoi fi-aika-sek pvm))

(defn aika-iso8601
  [pvm]
  (formatoi iso8601-aikaleimalla pvm))

(defn kuukausi-ja-vuosi-valilyonnilla
  "Formatoi pvm:n muotoon: MM / yy"
  [pvm]
  (formatoi kuukausi-ja-vuosi-fmt-valilyonnilla pvm))

(defn kuukausi-ja-vuosi
  "Formatoi pvm:n muotoon: MM/yy"
  [pvm]
  (formatoi kuukausi-ja-vuosi-fmt pvm))

(defn kokovuosi-ja-kuukausi
  "Formatoi pvm:n muotoon: yyyy/mm"
  [pvm]
  (formatoi kokovuosi-ja-kuukausi-fmt pvm))

(defn ->pvm-aika [teksti]
  "Jäsentää tekstistä d.M.yyyy H:mm tai d.M.yyyy H muodossa olevan päivämäärän ja ajan.
  Jos teksti ei ole oikeaa muotoa, palauta nil."
  (let [teksti-kellonaika-korjattu (if (not= -1 (.indexOf teksti ":"))
                                     teksti
                                     (str teksti ":00"))]
    (try
      (parsi fi-pvm-aika (str/trim teksti-kellonaika-korjattu))
      (catch #?(:cljs js/Error
                :clj Exception) e
        nil))))

(defn ->pvm-aika-sek [teksti]
  "Jäsentää tekstistä dd.MM.yyyy HH:mm:ss muodossa olevan päivämäärän ja ajan. Tämä on koneellisesti formatoitua päivämäärää varten, älä käytä ihmisen syöttämän tekstin jäsentämiseen!"
  (parsi fi-pvm-aika-sek teksti))

(defn ->pvm [teksti]
  "Jäsentää tekstistä dd.MM.yyyy muodossa olevan päivämäärän. Jos teksti ei ole oikeaa muotoa, palauta nil."
  (try
    (parsi fi-pvm-parse teksti)
    (catch #?(:cljs js/Error
              :clj Exception) e
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

(defn vuoden-aikavali [vuosi]
  [(paivan-alussa (vuoden-eka-pvm vuosi))
   (paivan-lopussa (vuoden-viim-pvm vuosi))])

(defn hoitokauden-alkupvm
  "Palauttaa hoitokauden alkupvm:n 1.10.vuosi"
  [vuosi]
  (luo-pvm vuosi 9 1))

(defn hoitokauden-loppupvm
  "Palauttaa hoitokauden loppupvm:n 30.9.vuosi"
  [vuosi]
  (luo-pvm vuosi 8 30))

(defn- d [x]
  #?(:cljs x
     :clj (if (instance? Date x)
            (suomen-aikavyohykkeeseen (tc/from-date x))
            x)))

(defn vuosi
  "Palauttaa annetun DateTimen vuoden, esim 2015."
  [pvm]
  (t/year (d pvm)))

(defn kuukausi
  "Palauttaa annetun DateTime kuukauden."
  [pvm]
  ;; PENDING: tämä ei clj puolella toimi, jos ollaan kk alussa
  ;; esim 2015-09-30T21:00:00.000-00:00 (joka olisi keskiyöllä meidän aikavyöhykkeellä)
  ;; pitäisi joda date timeihin vaihtaa koko backend puolella
  (t/month (d pvm)))

(defn paiva
  "Palauttaa annetun DateTime päivän."
  [pvm]
  (t/day (d pvm)))

(defn paivamaaran-hoitokausi
  "Palauttaa hoitokauden [alku loppu], johon annettu pvm kuuluu"
  [pvm]
  (let [vuosi (vuosi pvm)]
    (if (ennen? pvm (hoitokauden-alkupvm vuosi))
      [(hoitokauden-alkupvm (dec vuosi))
       (hoitokauden-loppupvm vuosi)]
      [(hoitokauden-alkupvm vuosi)
       (hoitokauden-loppupvm (inc vuosi))])))

(defn paiva-kuukausi
  "Palauttaa päivän ja kuukauden suomalaisessa muodossa pp.kk."
  [pvm]
  (str (paiva pvm) "." (kuukausi pvm) "."))

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

(defn
  kuukauden-aikavali
  "Palauttaa kuukauden aikavälin vektorina [alku loppu], jossa alku on kuukauden ensimmäinen päivä
kello 00:00:00.000 ja loppu on kuukauden viimeinen päivä kello 23:59:59.999 ."
  [dt]
  (let [alku (aikana (t/first-day-of-the-month dt)
                     0 0 0 0)
        loppu (aikana (t/last-day-of-the-month dt)
                      23 59 59 999)]
    ;; aseta aika
    [alku loppu]))


(defn
  kuukauden-aikavali-opt
  [dt]
  (when dt
    (kuukauden-aikavali dt)))

(defn- siirtyma [dt valittu yksikko-fn n]
  (if (= valittu :alku)
    [(paivan-alussa dt) (paivan-lopussa (t/plus dt (yksikko-fn n)))]
    [(paivan-alussa (t/minus dt (yksikko-fn n))) (paivan-lopussa dt)]))

(defn- pakota-paivalla [dt n valittu]
  (siirtyma dt valittu t/days n))

(defn- samalle-kuukaudelle [dt valittu]
  (if (= valittu :alku)
    (let [[_ b] (kuukauden-aikavali dt)]
      [(paivan-alussa dt) b])

    (let [[a _] (kuukauden-aikavali dt)]
      [a (paivan-lopussa dt)])))

(defn- pakota-kuukaudella [dt n valittu]
  ;; Kuukaudella pakottamisessa on erikoistapaus: jos halutaan yhden kuukauden
  ;; aikaväli, niin palautetaan sellainen aikaväli, jossa molemmat päivät osuvat
  ;; samalle kuukaudelle, eli arvo jota EI muokattu on aina joko kuukauden viimeinen tai
  ;; ensimmäinen päivä
  (if (= n 1)
    (samalle-kuukaudelle dt valittu)

    (siirtyma dt valittu t/months n)))

(defn- pakota-vuodella [dt n valittu]
  (siirtyma dt valittu t/years n))

(defn- pakota-aikavali [alku loppu [n yksikko] valittu]
  (assert (not (= n 0)) "pvm/pakota-aikavali: n ei saa olla nolla")
  (let [pakotus-fn (case yksikko
                     :paiva
                     pakota-paivalla
                     :kuukausi
                     pakota-kuukaudella
                     :vuosi
                     pakota-vuodella)]
    (pakotus-fn (if (= valittu :alku) alku loppu) n valittu)))

(defn- assertoi-aikavalin-yksikko [[_ yksikko]]
  (assert (#{:paiva :kuukausi :vuosi} yksikko)
          "pvm/varmista-aikavali: yksikön pitää olla :paiva :kuukausi tai :vuosi"))

(defn liian-suuri-aikavali? [alku loppu [n yksikko :as maksimi]]
  (assertoi-aikavalin-yksikko maksimi)
  (if (sama-tai-ennen?
        (t/plus alku (condp = yksikko
                       :paiva
                       (t/days n)
                       :kuukausi
                       (t/months n)
                       :vuosi
                       (t/years n)))
        loppu
        true)
    true
    false))

(defn varmista-aikavali
  "Funktiolla voi varmistaa, että annettu aikaväli on maksimissaan tietyn mittainen.
  Jos ei ole, palauttaa aikavälistä sääntöä muokatun version. Palautettava versio riippuu
  siitä, kumpi arvoista oli muokattu.

  - Aikaväli: [alku loppu]
  - Maksimi: [montako mitä], mitä on :paiva, :kuukausi tai :vuosi. montako on kokonaisluku.
    jos haluttu aikaväli on esim yksi kuukausi, niin montako on 1.
  - Valittu: Keyword joka kertoo, kumpi aikavälin arvoista valittiin, eli kumpi pysyy vakiona,
    jos palautettavaa aikaväliä joudutaan muokkaamaan."
  ([[alku loppu] [n yksikko :as maksimi] valittu]
   (assertoi-aikavalin-yksikko maksimi)
   (assert (#{:alku :loppu} valittu)
           "pvm/varmista-aikavali: valittu pitää olla keyword :alku tai :loppu")
   (cond
     (jalkeen? alku loppu)
     (if (= valittu :alku)
       [alku (paivan-lopussa alku)]
       [(paivan-alussa loppu) loppu])

     (liian-suuri-aikavali? alku loppu maksimi)
     (pakota-aikavali alku loppu maksimi valittu)

     :else [alku loppu]))
  ([aikavali] (varmista-aikavali aikavali :alku))
  ([[alku loppu] valittu]
   (assert (#{:alku :loppu} valittu)
           "pvm/varmista-aikavali: valittu pitää olla keyword :alku tai :loppu")
   (if (jalkeen? alku loppu)
     (samalle-kuukaudelle (if (= valittu :alku) alku loppu) valittu)
     [(paivan-alussa alku) (paivan-lopussa loppu)])))

(defn varmista-aikavali-opt [[alku loppu] & args]
  (if (and alku loppu)
    (apply varmista-aikavali [alku loppu] args)
    [alku loppu]))


#?(:cljs
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
                  (t/plus kk (t/months 1))))))))

#?(:cljs
   (defn vuoden-kuukausivalit
     "Palauttaa vektorin kuukauden aikavälejä (ks. kuukauden-aikavali funktio) annetun vuoden jokaiselle kuukaudelle."
     [alkuvuosi]
     (let [alku (t/first-day-of-the-month (luo-pvm alkuvuosi 0 1))]
       (loop [kkt [(kuukauden-aikavali alku)]
              kk (t/plus alku (t/months 1))]
         (if (not= (vuosi kk) alkuvuosi)
           kkt
           (recur (conj kkt
                        (kuukauden-aikavali kk))
                  (t/plus kk (t/months 1))))))))

#?(:cljs
   (defn ed-kk-aikavalina
     [p]
     (let [pvm-ed-kkna (t/minus p (t/months 1))]
       [(t/first-day-of-the-month pvm-ed-kkna)
        (t/last-day-of-the-month pvm-ed-kkna)])))

#?(:clj
   (defn kyseessa-kk-vali?
     "Kertoo onko annettu pvm-väli täysi kuukausi. Käyttää aikavyöhykekonversiota mistä halutaan ehkä joskus eroon."
     [alkupvm loppupvm]
     (let [alku (l/to-local-date-time alkupvm)
           loppu (l/to-local-date-time loppupvm)
           paivia-kkssa (t/number-of-days-in-the-month alku)
           alku-pv (paiva alku)
           loppu-pv (paiva loppu)]
       (and (and (= (vuosi alku)
                    (vuosi loppu))
                 (= (kuukausi alku)
                    (kuukausi loppu)))
            (= 1 alku-pv)
            (= paivia-kkssa loppu-pv)))))

#?(:clj
   (defn kyseessa-hoitokausi-vali?
     "Kertoo onko annettu pvm-väli täysi hoitokausi. Käyttää aikavyöhykekonversiota mistä halutaan ehkä joskus eroon."
     [alkupvm loppupvm]
     (let [alku (l/to-local-date-time alkupvm)
           loppu (l/to-local-date-time loppupvm)]
       (and (= 1 (paiva alku))
            (= 10 (kuukausi alku))
            (= 30 (paiva loppu))
            (= 9 (kuukausi loppu))
            (= (inc (vuosi alku)) (vuosi loppu))))))

#?(:clj
   (defn kyseessa-vuosi-vali?
     "Kertoo onko annettu pvm-väli täysi vuosi. Käyttää aikavyöhykekonversiota mistä halutaan ehkä joskus eroon."
     [alkupvm loppupvm]
     (let [alku (l/to-local-date-time alkupvm)
           loppu (l/to-local-date-time loppupvm)
           _ (log/debug "pvm kyseessä hoitokausi väli?" "alku " alku " loppu " loppu)]
       (and (= 1 (paiva alku))
            (= 1 (kuukausi alku))
            (= 31 (paiva loppu))
            (= 12 (kuukausi loppu))
            (= (vuosi alku) (vuosi loppu))))))

#?(:clj
   (defn kuukautena-ja-vuonna
     "Palauttaa tekstiä esim tammikuussa 2016"
     [alkupvm]
     (str (kuukauden-nimi (kuukausi alkupvm)) "ssa "
          (vuosi alkupvm))))

(defn urakan-vuodet [alkupvm loppupvm]
  (let [ensimmainen-vuosi (vuosi alkupvm)
        viimeinen-vuosi (vuosi loppupvm)]
    (if (= ensimmainen-vuosi viimeinen-vuosi)
      [[alkupvm loppupvm]]

      (vec (concat [[alkupvm (vuoden-viim-pvm ensimmainen-vuosi)]]
                   (mapv (fn [vuosi]
                           [(vuoden-eka-pvm vuosi) (vuoden-viim-pvm vuosi)])
                         (range (inc ensimmainen-vuosi) viimeinen-vuosi))
                   [[(vuoden-eka-pvm viimeinen-vuosi) loppupvm]])))))


(def paivan-aikavali (juxt paivan-alussa paivan-lopussa))

#?(:clj
   (defn aikavali-paivina [alku loppu]
     (t/in-days (t/interval (joda-timeksi alku) (joda-timeksi loppu)))))

(defn paivia-aikavalien-leikkauskohdassa
  "Ottaa kaksi aikaväliä ja kertoo, kuinka monta toisen aikavälin päivää osuu ensimmäiselle aikavälille."
  [[alkupvm loppupvm] [vali-alkupvm vali-loppupvm]]
  (let [pvm-vector (sort t/before? [alkupvm loppupvm vali-alkupvm vali-loppupvm])]
    (if (or (and (t/before? vali-alkupvm alkupvm)
                 (t/before? vali-alkupvm loppupvm)
                 (t/before? vali-loppupvm alkupvm)
                 (t/before? vali-loppupvm loppupvm))
            (and (t/after? vali-alkupvm alkupvm)
                 (t/after? vali-alkupvm loppupvm)
                 (t/after? vali-loppupvm alkupvm)
                 (t/after? vali-loppupvm loppupvm)))
      0
      (t/in-days (t/interval (nth pvm-vector 1)
                             (nth pvm-vector 2))))))

(defn paivia-valissa
  "Palauttaa kokonaisluvun, joka kertoo montako päivää kahden päivämäärän välissä on.
   Annettujen päivämäärien ei tarvitse olla kronologisessa järjestyksessä."
  [eka toka]
  (if (t/before? eka toka)
    (t/in-days (t/interval eka toka))
    (t/in-days (t/interval toka eka))))

#?(:clj
   (defn iso-8601->pvm
     "Parsii annetun ISO-8601 (yyyy-MM-dd) formaatissa olevan merkkijonon päivämääräksi."
     [teksti]
     (df/parse (df/formatter "yyyy-MM-dd") teksti)))

#?(:clj
   (defn pvm->iso-8601
     "Parsii annetun päivämäärän ISO-8601 (yyyy-MM-DD) muotoon."
     [pvm]
     (df/unparse (df/formatter "yyyy-MM-dd") pvm)))

(defn edelliset-n-vuosivalia [n]
  (let [pvmt (take n (iterate #(t/minus % (t/years 1)) (t/now)))]
    (mapv t/year pvmt)))


#?(:cljs
   (defn paivaa-sitten [paivaa]
     (-> paivaa t/days t/ago)))

#?(:cljs
   (defn tuntia-sitten [tuntia]
     (t/minus (nyt) (t/hours tuntia))))

#?(:cljs
   (defn tunnin-paasta [tuntia]
     (t/plus (nyt) (t/hours tuntia))))

#?(:clj
   (defn tuntia-sitten [tuntia]
     (-> tuntia t/hours t/ago)))

(def kayttoonottto (t/local-date 2016 10 1))
