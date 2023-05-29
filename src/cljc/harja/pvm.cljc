(ns harja.pvm
  "Yleiset päivämääräkäsittelyn asiat.
  Frontin puolella käytetään yleisesti tyyppiä goog.date.DateTime.
  Backendissä käytetään yleisesti org.joda.time:n pvm-tyyppejä (muutamat java.util.Date-poikkeukset dokumentoitu erikseen)"
  (:require
    #?(:cljs [cljs-time.format :as df])
    #?(:cljs [cljs-time.core :as t])
    #?(:cljs [cljs-time.coerce :as tc])
    #?(:cljs [cljs-time.extend])
    #?(:clj [clj-time.format :as df])
    #?(:clj
       [clj-time.core :as t])
    #?(:clj
       [clj-time.coerce :as tc])
    #?(:clj
       [clj-time.local :as l])

    [taoensso.timbre :as log]
    [clojure.string :as str])

  #?(:cljs (:import (goog.date DateTime))
     :clj
           (:import (java.util Calendar Date)
                    (java.text SimpleDateFormat)
                    (org.joda.time DateTime DateTimeZone))))

(def +kuukaudet+ ["Tammi" "Helmi" "Maalis" "Huhti"
                  "Touko" "Kesä" "Heinä" "Elo"
                  "Syys" "Loka" "Marras" "Joulu"])

(def +kuukaudet-pitka-muoto+
  ["Tammikuu" "Helmikuu" "Maaliskuu" "Huhtikuu"
   "Toukokuu" "Kesäkuu" "Heinäkuu" "Elokuu"
   "Syyskuu" "Lokakuu" "Marraskuu" "Joulukuu"])

(defn kk-fmt [kk]
  (get +kuukaudet+ (dec kk)))

#?(:cljs
   (do
     (defrecord Aika [tunnit minuutit sekunnit])
     (defn- vertailuaika [{:keys [tunnit minuutit sekunnit]}]
       (+ (* 10000 (or tunnit 0))
          (* 100 (or minuutit 0))
          (or sekunnit 0)))
     (defn aika-jalkeen? [eka toka]
       (> (vertailuaika eka) (vertailuaika toka)))
     (defn aika-ennen? [eka toka]
       (< (vertailuaika eka) (vertailuaika toka)))))

#?(:cljs
   ;; Toteutetaan hash ja equiv, jotta voimme käyttää avaimena hashejä
   (extend-type DateTime
     IHash
     (-hash [o]
       (hash (tc/to-long o)))))

#?(:clj
   (defn- joda-time? [pvm]
     (or (instance? org.joda.time.DateTime pvm)
         (instance? org.joda.time.LocalDate pvm)
         (instance? org.joda.time.LocalDateTime pvm))))

#?(:clj
   (def suomen-aikavyohyke (DateTimeZone/forID "Europe/Helsinki")))

#?(:clj
   (defn suomen-aikavyohykkeessa
     "Palautteen uuden Joda-ajan suomen aikavyöhykkeellä niin, että aika on sama, mutta aikavyöhyke muuttuu."
     [joda-time]
     (t/from-time-zone joda-time suomen-aikavyohyke)))

#?(:clj
   (defn suomen-aikavyohykkeessa? [pvm]
     {:pre [(instance? DateTime pvm)]
      :post [(boolean? %)]}
     (= (str (.getZone (.getChronology pvm)))
        (str suomen-aikavyohyke))))

#?(:clj
   (defn suomen-aikavyohykkeeseen
     "Palauttaa uuden Joda-ajan Suomen aikavyöhykkeessä niin, että aika on absoluuttisesti paikallisessa Suomeen ajassa."
     [joda-time]
     {:pre [(instance? DateTime joda-time)]
      :post [(suomen-aikavyohykkeessa? %)]}
     (if (suomen-aikavyohykkeessa? joda-time)
       joda-time
       (t/to-time-zone joda-time suomen-aikavyohyke))))

(defn aikana [dt tunnit minuutit sekunnit millisekunnit]
  #?(:cljs
     (when dt
       (goog.date.DateTime.
         (.getYear dt)
         (.getMonth dt)
         (.getDate dt)
         tunnit
         minuutit
         sekunnit
         millisekunnit))

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

#?(:clj
   (defn dateksi [dt]
     (if (instance? java.util.Date dt)
       dt
       (tc/to-date dt))))

(defn nyt
  "Frontissa palauttaa goog.date.Datetimen (käyttäjän laitteen aika)
  Backendissä palauttaa java.util.Daten"
  []
  #?(:cljs (DateTime.)
     :clj  (Date.)))

#?(:clj
   (defn eilinen
     "Palauttaa eilisen Datena"
     []
     (dateksi (t/minus (t/now) (t/days 1)))))

#?(:clj
   (defn nyt-suomessa []
     (suomen-aikavyohykkeeseen (tc/from-date (nyt)))))

#?(:clj
   (defn suomen-aika->iso8601-basic
     [pvm]
     {:pre [(instance? DateTime pvm)]
      :post [(string? %)]}
     (l/format-local-time pvm :basic-date-time)))

#?(:clj
   (defn iso8601-basic->suomen-aika
     [iso8601-basic]
     {:pre [(string? iso8601-basic)]
      :post [(instance? DateTime %)]}
     (l/to-local-date-time iso8601-basic)))

(defn pvm?
  [pvm]
  #?(:cljs (instance? DateTime pvm)
     :clj  (joda-time? pvm)))

(defn luo-pvm
  "Frontissa palauttaa goog.date.Datetimen
  Backendissä palauttaa java.util.Daten

  Vuosi 1-index, kuukausi on 0-index ja pv on 1-index"
  [vuosi kk pv]
  #?(:cljs (DateTime. vuosi kk pv 0 0 0 0)
     :clj  (Date. (- vuosi 1900) kk pv)))

(defn luo-pvm-dec-kk
  "Vaihtoehtoinen apuri luo-pvm:lle joka dekrementoi kuukauden automaattisesti.
  Alkuperäisen luo-pvm funktion käytössä helposti unohtuu (dec kk) ja se on turhaa toistoa."
  [vuosi kk pv]
  (luo-pvm vuosi (dec kk) pv))

(defn sama-pvm? [eka toka]
  (if-not (and eka toka)
    false
    (and (= (t/year eka) (t/year toka))
         (= (t/month eka) (t/month toka))
         (= (t/day eka) (t/day toka)))))

#?(:clj
   (defn sama-tyyppiriippumaton-pvm? [eka toka]
     (sama-pvm? (joda-timeksi eka) (joda-timeksi toka))))


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

#?(:cljs
   (defn jalkeen? [eka toka]
     (if (and eka toka)
       (t/after? eka toka)
       false))

   :clj
   (defn jalkeen? [eka toka]
     (if (and eka toka)
       (cond
         (or (instance? java.util.Date eka)
             (instance? java.util.Date toka))
         (.after eka toka)
         (or (joda-time? eka)
             (joda-time? toka))
         (t/after? eka toka))
       false)))

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

(defn tiukin-aikavali
  [[alkupvm loppupvm] [v-alkupvm v-loppupvm]]
  (let [alkupvm (if (jalkeen? alkupvm v-alkupvm)
                  alkupvm
                  v-alkupvm)
        loppupvm (if (ennen? loppupvm v-loppupvm)
                   loppupvm
                   v-loppupvm)]
    [alkupvm loppupvm]))

#?(:clj
   (defn tanaan? [pvm]
     (sama-pvm? (suomen-aikavyohykkeeseen (joda-timeksi pvm)) (nyt-suomessa))))

(defn tanaan-aikavali []
  [(aikana (t/today) 0 0 0 0)
   (aikana (t/today) 23 59 59 999)])

(defn- luo-format [str]
  #?(:cljs (df/formatter str)
     :clj  (SimpleDateFormat. str)))
(defn- formatoi [format date]
  #?(:cljs (df/unparse format date)
     :clj  (.format format date)))
(defn parsi [format teksti]
  #?(:cljs (df/parse-local format teksti)
     :clj  (.parse format teksti)))

(def fi-pvm
  "Päivämäärän formatointi suomalaisessa muodossa"
  (luo-format "dd.MM.yyyy"))

(def fi-pvm-ilman-vuotta
  "Päivämäärän formatointi suomalaisessa muodossa ilman vuotta"
  (luo-format "dd.MM."))

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

(def iso8601-format
  (luo-format "yyyy-MM-dd"))

(def yha-aikaleimalla
  (luo-format "yyyy-MM-dd'T'HH:mm:ss.SZ"))

(def iso8601-aikavyohykkeella-format
  (luo-format "yyyy-MM-dd'T'HH:mm:ssX"))

(defn jsondate
  "Luodaan (t/now) tyyppisestä ajasta json date formaatti -> 2022-08-10T12:00:00Z"
  [pvm]
  (formatoi (luo-format "yyyy-MM-dd'T'HH:mm:ss'Z'") pvm))

(defn aika-iso8601-ilman-millisekunteja
  [pvm]
  (formatoi (luo-format "yyyy-MM-dd'T'HH:mm:ss") pvm))

(defn aika-iso8601-aikavyohykkeen-kanssa
  [pvm]
  (formatoi (luo-format "yyyy-MM-dd'T'HH:mm:ssZ") pvm))

(def kuukausi-ja-vuosi-fmt-valilyonnilla
  (luo-format "MM / yy"))

(def kuukausi-ja-vuosi-fmt
  (luo-format "MM/yy"))

(def kokovuosi-ja-kuukausi-fmt
  (luo-format "yyyy/MM"))

(defn fmt-kuukausi-ja-vuosi-lyhyt [aika]
  (formatoi (luo-format "d.M.") aika))

(defn fmt-p-k-v-lyhyt [aika]
  (formatoi (luo-format "d.M.yyyy") aika))

#?(:clj (def pgobject-format
          (luo-format "yyyy-MM-dd HH:mm:ss")))

(defn pvm-aika
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa"
  [pvm]
  (when pvm
    (formatoi fi-pvm-aika pvm)))

(defn pvm-aika-klo
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa: pp.kk.yyyy klo HH:mm"
  [pvm]
  (when pvm
    (str (formatoi fi-pvm pvm) " klo " (formatoi fi-aika pvm))))

(defn pvm-aika-klo-suluissa
  [pvm]
  (if pvm 
    (str (formatoi fi-pvm pvm) " (" (formatoi fi-aika pvm) ")")
    ""))

(defn pvm-aika-opt
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa tai tyhjä, jos nil."
  [p]
  (if p
    (pvm-aika p)
    ""))

(defn aikavali-kellonajan-kanssa
  "Palauttaa lähinnä käyttöliittymää varten aikavälin miellyttävässä formaatissa"
  [[alkuhetki loppuhetki]]
  (str (pvm-aika-opt alkuhetki)
       " - "
       (pvm-aika-opt loppuhetki)))

(defn pvm-aika-sek
  "Formatoi päivämäärän ja ajan suomalaisessa muodossa sekuntitarkkuudella"
  [pvm]
  (formatoi fi-pvm-aika-sek pvm))

(defn pvm
  "Formatoi päivämäärän suomalaisessa muodossa.

  Optiot:
  nayta-vuosi-fn   Funktio, joka kertoo, näytetäänkö vuosi. Jos ei annettu, näytetään vuosi."
  ([paivamaara] (pvm paivamaara {}))
  ([paivamaara {:keys [nayta-vuosi-fn]}]
   (let [nayta-vuosi-fn (or nayta-vuosi-fn (constantly true))]
     (if (nayta-vuosi-fn paivamaara)
       (formatoi fi-pvm paivamaara)
       (formatoi fi-pvm-ilman-vuotta paivamaara)))))

(defn pvm-opt
  "Formatoi päivämäärän suomalaisessa muodossa tai tyhjä, jos nil.

  Optiot:
  nayta-vuosi-fn   Funktio, joka kertoo, näytetäänkö vuosi. Jos ei annettu, näytetään vuosi."
  ([paivamaara] (pvm-opt paivamaara {}))
  ([paivamaara optiot]
   (if paivamaara
     (pvm paivamaara optiot)
     "")))

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

(defn iso8601
  "Palauttaa tekstimuodossa päivämäärän, joka sopii esim tietokantahakuihin. Päivämäärä
  palautetaan siis esimerkiksi muodossa 2030-01-15"
  [pvm]
  (formatoi iso8601-format pvm))

(defn aika-yha-format
  [pvm]
  (formatoi yha-aikaleimalla pvm))

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

(defn ->pvm-aika
  "Jäsentää tekstistä d.M.yyyy H:mm tai d.M.yyyy H muodossa olevan päivämäärän ja ajan.
  Jos teksti ei ole oikeaa muotoa, palauta nil."
  [teksti]
  (let [teksti-kellonaika-korjattu (if (not= -1 (.indexOf teksti ":"))
                                     teksti
                                     (str teksti ":00"))]
    (try
      (parsi fi-pvm-aika (str/trim teksti-kellonaika-korjattu))
      (catch #?(:cljs js/Error
                :clj  Exception) e
        nil))))

(defn ->pvm-aika-sek
  "Jäsentää tekstistä dd.MM.yyyy HH:mm:ss muodossa olevan päivämäärän ja ajan. Tämä on koneellisesti formatoitua päivämäärää varten, älä käytä ihmisen syöttämän tekstin jäsentämiseen!"
  [teksti]
  (parsi fi-pvm-aika-sek teksti))

(defn ->pvm
  "Jäsentää tekstistä dd.MM.yyyy muodossa olevan päivämäärän. Jos teksti ei ole oikeaa muotoa, palauta nil."
  [teksti]
  (try
    (parsi fi-pvm-parse teksti)
    (catch #?(:cljs js/Error
              :clj  Exception) e
      nil)))

(defn ->pvm-date-timeksi [teksti]
  #?(:clj
     (let [date-timeksi (fn [ldt]
                          (t/date-time (t/year ldt)
                                       (t/month ldt)
                                       (t/day ldt)
                                       (t/hour ldt)
                                       (t/minute ldt)
                                       (t/second ldt)))]
       (-> teksti ->pvm tc/from-date suomen-aikavyohykkeeseen date-timeksi))

     :cljs
     (->pvm teksti)))

(defn kuukauden-numero [kk-nimi]
  (case (str/lower-case kk-nimi)
    "tammikuu" 1
    "helmikuu" 2
    "maaliskuu" 3
    "huhtikuu" 4
    "toukokuu" 5
    "kesäkuu" 6
    "kesakuu" 6
    "heinäkuu" 7
    "heinakuu" 7
    "elokuu" 8
    "syyskuu" 9
    "lokakuu" 10
    "marraskuu" 11
    "joulukuu" 12
    nil))

(defn kuukauden-nimi
  ([kk] (kuukauden-nimi kk false))
  ([kk isolla-kirjaimella?]
   (let [nimi (case kk
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
                "kk ei välillä 1-12")]
     (if isolla-kirjaimella? (str/capitalize nimi) nimi))))

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

(defn hoitokauden-alkupvm-str
  "Palauttaa hoitokauden alkupvm:n tekstimuodossa esim: 1.10.<annettu vuosi>"
  [vuosi]
  (formatoi fi-pvm (hoitokauden-alkupvm vuosi)))

(defn hoitokauden-loppupvm-str
  "Palauttaa hoitokauden alkupvm:n tekstimuodossa esim: 30.09.<annettu vuosi>"
  [vuosi]
  (formatoi fi-pvm (hoitokauden-loppupvm vuosi)))

(defn hoitokausi-str-alkuvuodesta
  "Ottaa sisään hoitokauden alkuvuodesta, palauttaa formatoidun hoitokauden esim. alasvetovalintaa varten muodossa 1.10.2021-30.09.2022"
  [hk-alkuvuosi]
  (when hk-alkuvuosi
    (str
      (hoitokauden-alkupvm-str hk-alkuvuosi)
      "-"
      (hoitokauden-loppupvm-str (inc hk-alkuvuosi)))))

(defn hoitokausi-str-alkuvuodesta-vuodet
  "Ottaa sisään hoitokauden alkuvuodesta, palauttaa formatoidun hoitokauden muodossa 2021-2022"
  [hk-alkuvuosi]
  (when hk-alkuvuosi
    (str
      hk-alkuvuosi
      "-"
      (inc hk-alkuvuosi))))

(defn vesivaylien-hoitokauden-alkupvm
  "Vesiväylien hoitokauden alkupvm vuodelle: 1.8.vuosi"
  [vuosi]
  (luo-pvm vuosi 7 1))

(defn vesivaylien-hoitokauden-loppupvm
  "Vesiväylien hoitokauden loppupvm vuodelle: 31.7.vuosi"
  [vuosi]
  (luo-pvm vuosi 6 31))

(defn- d [x]
  #?(:cljs x
     :clj  (if (instance? Date x)
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

(defn koko-kuukausi-ja-vuosi
  "Formatoi pvm:n muotoon: MMMM yyyy. Esim. Touko 2017."
  ([pvm]
   (koko-kuukausi-ja-vuosi pvm false))
  ([pvm pitka-muoto?]
   (str (nth (if pitka-muoto?
               +kuukaudet-pitka-muoto+
               +kuukaudet+)
             (dec (kuukausi pvm))) " " (vuosi pvm))))

(defn kuukausi-isolla
  "Palauttaa annetun kuukauden nimen isolla alkukirjaimella"
  [kuukausi]
  (str/capitalize
    (kuukauden-nimi kuukausi)))

(defn paiva
  "Palauttaa annetun DateTime päivän."
  [pvm]
  (t/day (d pvm)))

(defn tunti
  "Palauttaa annetun DateTime kuukauden"
  [pvm]
  (t/hour (d pvm)))

(defn minuutti
  "Palauttaa annetun DateTime kuukauden"
  [pvm]
  (t/minute (d pvm)))

(defn sekuntti
  "Palauttaa annetun DateTime kuukauden"
  [pvm]
  (t/second (d pvm)))

(defn paivamaaran-hoitokausi
  "Palauttaa hoitokauden [alku loppu], johon annettu pvm kuuluu"
  [pvm]
  (let [vuosi (vuosi pvm)]
    (if (ennen? pvm (hoitokauden-alkupvm vuosi))
      [(hoitokauden-alkupvm (dec vuosi))
       (hoitokauden-loppupvm vuosi)]
      [(hoitokauden-alkupvm vuosi)
       (hoitokauden-loppupvm (inc vuosi))])))

(defn paivamaara->mhu-hoitovuosi-nro
  "Palauttaa MHU hoitovuoden järjestysnumeron annetulle päivämäärälle."
  [urakan-alkupvm pvm]
  (let [urakan-alkuvuosi (vuosi urakan-alkupvm)
        [kauden-alkupvm _] (paivamaaran-hoitokausi pvm)
        kauden-alkuvuosi (vuosi kauden-alkupvm)]
    (max (- (inc kauden-alkuvuosi) urakan-alkuvuosi) 1)))

(defn paivamaaran-hoitokausi-str
  [pvm]
  (let [paivamaaran-hoitokausi (paivamaaran-hoitokausi pvm)]
    (str (formatoi fi-pvm (first paivamaaran-hoitokausi))
      " - "
      (formatoi fi-pvm (second paivamaaran-hoitokausi)))))

#?(:clj
  (defn hoitokauden-alkuvuosi
    ([^org.joda.time.DateTime pvm]
     (let [vuosi (.getYear pvm)
           kuukausi (.getMonthOfYear pvm)]
       (hoitokauden-alkuvuosi vuosi kuukausi)))
    ([vuosi kuukausi]
     (if (<= 10 kuukausi)
       vuosi
       (dec vuosi)))))

#?(:cljs
  (defn hoitokauden-alkuvuosi
    ([pvm]
     (let [aika (parsi (luo-format "yyyy-MM-dd'T'HH:mm:ss'Z'") pvm)
           vuosi (t/year aika)
           kuukausi (t/month aika)]
       (hoitokauden-alkuvuosi vuosi kuukausi)))
    ([vuosi kuukausi]
     (if (<= 10 kuukausi)
       vuosi
       (dec vuosi)))))

(defn hoitokauden-alkuvuosi-nykyhetkesta [nyt]
  (hoitokauden-alkuvuosi (vuosi nyt) (kuukausi nyt)))

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

(defn mhu-hoitovuoden-nro->hoitokauden-aikavali
  "Palauttaa annetulle MHU hoitovuoden järjestysnumerolle kyseisen hoitovuoden alku- ja loppu päivämäärän."
  [urakan-alkuvuosi hoitovuoden-nro]

  (when-not (and (int? hoitovuoden-nro) (<= 1 hoitovuoden-nro 5))
    (throw (ex-info
             (str "hoitovuoden-nro on järjestysluku välillä 1-5. Saatiin: " hoitovuoden-nro)
             {:hoitovuoden-nro hoitovuoden-nro})))

  (when-not (int? urakan-alkuvuosi)
    (throw (ex-info
             "urakan-alkuvuosi ei ole int."
             {:urakan-alkuvuosi urakan-alkuvuosi})))

  (let [hoitokauden-alkuvuosi (+ urakan-alkuvuosi (dec hoitovuoden-nro))
        hoitokauden-loppuvuosi (+ urakan-alkuvuosi hoitovuoden-nro)
        hoitokauden-alkupvm (hoitokauden-alkupvm hoitokauden-alkuvuosi)
        hoitokauden-loppupvm (hoitokauden-loppupvm hoitokauden-loppuvuosi)]

    {:alkupvm hoitokauden-alkupvm
     :loppupvm hoitokauden-loppupvm}))

(defn tulevat-hoitovuodet
  "Palauttaa nykyvuosi ja loppupv välistä urakan hoitovuodet vectorissa tyyliin: [2020 2021 2022 2023 2024].
  Jos tuleville voisille ei kopioida mitään, palauttaa vectorissa vain nykyvuoden tyyliin: [2022]"
  [nykyvuosi kopioidaan-tuleville-vuosille? urakka]
  (let [urakan-loppuvuosi (vuosi (:loppupvm urakka))
        hoitovuodet (if kopioidaan-tuleville-vuosille?
                      (range nykyvuosi urakan-loppuvuosi)
                      [nykyvuosi])]
    hoitovuodet))

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

(defn onko-hoitokausi?
  "On tilanteita, joissa voi olla vaikea tietää, käsitelläänkö täydellistä hoitokautta. Tarkistus fn sellaiseen, missä
  on syytä epäillä, että hoitokausi ei ole ihan kokonaan valittu."
  [^java.util.Date alkupvm ^java.util.Date loppupvm]
  (let [alkupvm-vuosi (vuosi alkupvm)
        loppupvm-vuosi (vuosi loppupvm)
        alkupvm-kuukausi (kuukausi alkupvm)
        loppupvm-kuukausi (kuukausi loppupvm)
        alkupvm-paiva (paiva alkupvm)
        loppupvm-paiva (paiva loppupvm)]
    (if (and
          (= (+ 1 alkupvm-vuosi) loppupvm-vuosi) ;; Alkupvm:n vuosi on edellinen vuosi
          (and (= alkupvm-kuukausi 10) (= loppupvm-kuukausi 9)) ;; Tarkistetaan lokakuu ja syyskuu
          (and (= alkupvm-paiva 1) (= loppupvm-paiva 30))) ;; Tarkistetaan päivät
      true
      false)))

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


(defn aikavalin-kuukausivalit
  "Palauttaa vektorin kuukauden aikavälejä (ks. kuukauden-aikavali funktio) annetun aikavälin jokaiselle kuukaudelle."
  [[alkupvm loppupvm]]
  (let [alku (t/first-day-of-the-month alkupvm)]
    (loop [kkt [(kuukauden-aikavali alkupvm)]
           kk (t/plus alku (t/months 1))]
      (if (t/after? kk loppupvm)
        kkt
        (recur (conj kkt
                     (kuukauden-aikavali kk))
               (t/plus kk (t/months 1)))))))

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
               (t/plus kk (t/months 1)))))))

(defn ed-kk-aikavalina
  [p]
  (let [pvm-ed-kkna (t/minus p (t/months 1))]
    [(aikana (t/first-day-of-the-month pvm-ed-kkna) 0 0 0 0)
     (aikana (t/last-day-of-the-month pvm-ed-kkna) 23 59 59 999)]))

#?(:clj
   (defn ed-kk-date-vektorina
     "Sisään Joda LocalDateTime"
     [p]
     (let [pvm-ed-kkna (t/minus p (t/months 1))]
       (map
         paivan-alussa
         [(t/first-day-of-the-month pvm-ed-kkna)
          (t/last-day-of-the-month pvm-ed-kkna)]))))

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
           loppu (l/to-local-date-time loppupvm)]
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

#?(:clj
   (defn aikavali-sekuntteina [alku loppu]
     (t/in-seconds (t/interval (joda-timeksi alku) (joda-timeksi loppu)))))

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

(defn paivia-valissa-opt
  "Palauttaa kokonaisluvun, joka kertoo montako päivää kahden päivämäärän välissä on.
   Annettujen päivämäärien ei tarvitse olla kronologisessa järjestyksessä.
   Jos toinen tai molemmat puuttuu, palauttaa nil."
  [eka toka]
  (when (and eka toka)
    (paivia-valissa eka toka)))

(defn iso-8601->pvm
  "Parsii annetun ISO-8601 (yyyy-MM-dd) formaatissa olevan merkkijonon joda-time päivämääräksi."
  [teksti]
  (df/parse (df/formatter "yyyy-MM-dd") teksti))

#?(:clj
   (defn pvm->iso-8601
     "Parsii annetun päivämäärän ISO-8601 (yyyy-MM-DD) muotoon."
     [pvm]
     (df/unparse (df/formatter "yyyy-MM-dd") pvm)))

#?(:clj
   (defn iso-8601->aika
     "Parsii annetun ISO-8601 (yyyy-MM-dd HH:mm:ss.SSSSSS) formaatissa olevan merkkijonon päivämääräksi."
     [teksti]
     (try
       (df/parse (df/formatter "yyyy-MM-dd HH:mm:ss.SSSSSS") teksti)
       (catch #?(:cljs js/Error
                 :clj  Exception) e
         nil))))

#?(:clj
   (defn psql-timestamp->aika
     "Parsii annetun postgresql timestamp (yyyy-MM-dd'T'HH:mm:ss.SSS) formaatissa olevan merkkijonon päivämääräksi."
     [teksti]
     (try
       (df/parse (df/formatter "yyyy-MM-dd'T'HH:mm:ss.SSS") teksti)
       (catch #?(:cljs js/Error
                 :clj  Exception) e
         nil))))

#?(:clj
   (defn rajapinta-str-aika->sql-timestamp
     "Rajapintoihin voi tulla hakuparametreina aika useammassa formaatissa.
     Formatoi saatu tekstimuotoinen aika aina utc sql muotoon.

     Formatoidaan: '2023-04-20T08:21:17+03' muotoinen aika  #inst '2023-04-20T05:21:17.000-00:00' formaattiin eli utc ajaksi.
     Javan aika ei ymmärrä mikrosekunteja. Eli saatu aika muodossa: '2023-04-14T09:07:20.162457Z' ei toimi. Sen vuoksi pakotetaan saatu tekstimuotoinen aika olemaan
     19 merkkiä pitkä ja Z:lla varmistetaan sen UTC ajankohta."
     [teksti]
     (if (and (= 22 (count teksti)) (not (str/includes? teksti "Z")))
       (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") teksti)
       (when (< 18 (count teksti))
         (let [teksti (str (subs teksti 0 19) "Z")]
           (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ssX") teksti))))))

(defn edelliset-n-vuosivalia [n]
  (let [pvmt (take n (iterate #(t/minus % (t/years 1)) (t/now)))]
    (mapv t/year pvmt)))


(defn paivaa-sitten [paivaa]
  (-> paivaa d t/days t/ago))


#?(:cljs
   (defn tuntia-sitten [tuntia]
     (t/minus (nyt) (t/hours tuntia))))

#?(:cljs
   (defn tunnin-paasta [tuntia]
     (t/plus (nyt) (t/hours tuntia))))

#?(:clj
   (defn tuntia-sitten [tuntia]
     (-> tuntia t/hours t/ago)))

(defn sekunttia-sitten [sekunttia]
  #?(:cljs
     (t/minus (nyt) (t/seconds sekunttia))
     :clj
     (t/minus (joda-timeksi (nyt)) (t/seconds sekunttia))))

(def kayttoonottto (t/local-date 2016 10 1))

(defn- paivat-valissa* [alku loppu]
  (if (jalkeen? alku loppu)
    nil
    (lazy-seq
      (cons alku
            (paivat-valissa* (t/plus alku (t/days 1)) loppu)))))

(defn paivat-valissa
  "Palauttaa laiskan seqin päivistä org.joda.time.DateTime muodossa"
  [alku loppu]
  (paivat-valissa* (d alku) (d loppu)))

(defn vuodet-valissa [alku loppu]
  (range (vuosi alku) (inc (vuosi loppu))))

(defn aikavalit-leikkaavat? [ensimmainen-alku ensimmainen-loppu toinen-alku toinen-loppu]
  (boolean (or
             (and
               (not (nil? toinen-alku))
               (not (nil? ensimmainen-alku))
               (not (nil? ensimmainen-loppu))
               (valissa? toinen-alku ensimmainen-alku ensimmainen-loppu false))
             (and
               (not (nil? toinen-loppu))
               (not (nil? ensimmainen-alku))
               (not (nil? ensimmainen-loppu))
               (valissa? toinen-loppu ensimmainen-alku ensimmainen-loppu false))
             (and
               (not (nil? ensimmainen-alku))
               (not (nil? toinen-alku))
               (not (nil? toinen-loppu))
               (valissa? ensimmainen-alku toinen-alku toinen-loppu false))
             (and
               (not (nil? ensimmainen-loppu))
               (not (nil? toinen-alku))
               (not (nil? toinen-loppu))
               (valissa? ensimmainen-loppu toinen-alku toinen-loppu false)))))

(defn pvm-ilman-samaa-vuotta
  "Formatoi annetun pvm:n suomalaisessa muodossa. Jättää vuoden pois, mikäli se on sama kuin annettu.
   Tällä tavalla voidaan esim. UI:ssa säästää tilaa, kun vuotta ei piirretä silloin kun on ilmeistä, mistä
   vuodesta on kyse."
  [pvm sama-vuosi]
  (pvm-opt pvm {:nayta-vuosi-fn #(not= (vuosi %) sama-vuosi)}))

(defn paivat-aikavalissa [alku loppu]
  (if (or (t/equal? alku loppu) (t/after? alku loppu))
    [alku]
    (sort (into [alku loppu]
                (map #(t/plus alku (t/days %))
                     (range 1 (t/in-days (t/interval alku loppu))))))))

(defn aikavali-nyt-miinus [paivia]
  (let [nyt #?(:clj  (joda-timeksi (nyt))
               :cljs (nyt))]
    [(t/minus nyt (t/days paivia)) nyt]))

(defn montako-paivaa-valissa
  "Montako päivää on paiva1 ja paiva2 välissä."
  [paiva1 paiva2]
  (let [paiva1 #?(:clj  (joda-timeksi paiva1)
           :cljs paiva1)
        paiva2 #?(:clj  (joda-timeksi paiva2)
                  :cljs paiva2)]
    (if (t/before? paiva1 paiva2)
      (t/in-days (t/interval paiva1 paiva2))
      (- (t/in-days (t/interval paiva2 paiva1))))))

#?(:clj
(defn ajan-muokkaus
  "Tällä voi lisätä tai vähentää jonku tietyn ajan annetusta päivästä.
  Anna dt joda timena tai java.sql.Date"
  ([dt lisaa? maara] (ajan-muokkaus dt lisaa? maara :sekuntti))
  ([dt lisaa? maara aikamaare]
   (let [dt (joda-timeksi dt)

         muokkaus (if lisaa?
                    t/plus
                    t/minus)
         aikamaara (case aikamaare
                     :sekuntti (t/seconds maara)
                     :minuutti (t/minutes maara)
                     :tunti (t/hours maara)
                     :paiva (t/days maara)
                     :viikko (t/weeks maara)
                     :kuukausi (t/months maara)
                     :vuosi (t/years maara))]
     (muokkaus dt aikamaara)))))

(defn myohaisin
  "Palauttaa myöhäisimmän ajan annetuista ajoista"
  [& ajat]
  (when-not (empty? ajat)
    (reduce (fn [myohaisin-aika aika]
              (if (jalkeen? myohaisin-aika aika)
                myohaisin-aika
                aika))
            ajat)))

(defn hoitokauden-alkuvuosi-kk->pvm
  "Muuttaa hoitokauden alkuvuoden ja kuukauden päivämääräksi oikealla vuodella, kk ensimmäiselle päivälle."
  [hoitokauden-alkuvuosi kk]
  (luo-pvm-dec-kk
    (if (>= 9 kk)
      (inc hoitokauden-alkuvuosi)
      hoitokauden-alkuvuosi)
    kk
    1))

(defn urakan-kuukausi-str
  "Palauttaa kuukauden nimen ja vuoden kuukauden numeron ja hoitokauden alkuvuoden perusteella
  esim. (urakan-kuukausi-str 11 2019) => Marraskuu 2020"
  [kk hoitokauden-alkuvuosi]
  (str (kuukauden-nimi kk true) " "
    (cond-> hoitokauden-alkuvuosi
      (>= 9 kk) inc)))

#?(:clj
   (defn lisaa-n-kuukautta-ja-palauta-uuden-kuukauden-viimeinen-pvm [pvm kk-maara]
     (tc/to-date-time
       (t/last-day-of-the-month
         (t/plus (tc/to-date-time (aika-iso8601 pvm)) (t/months kk-maara))))))

#?(:clj
   (defn kuukauden-ensimmainen-paiva
     [pvm]
     (dateksi (t/first-day-of-the-month (vuosi pvm) (kuukausi pvm)))))

#?(:clj
   (defn kuukauden-viimeinen-paiva
     [pvm]
     (dateksi (t/last-day-of-the-month (vuosi pvm) (kuukausi pvm)))))


;; Suomen lomapäivät
(defn- kiinteat-lomapaivat-vuodelle [vuosi]
  [{:nimi "Uudenvuodenpäivä" :pvm (luo-pvm-dec-kk vuosi 1 1)}
   {:nimi "Loppiainen" :pvm (luo-pvm-dec-kk vuosi 1 6)}
   {:nimi "Vappu" :pvm (luo-pvm-dec-kk vuosi 5 1)}
   {:nimi "Itsenäisyyspäivä" :pvm (luo-pvm-dec-kk vuosi 12 6)}
   {:nimi "Jouluaatto" :pvm (luo-pvm-dec-kk vuosi 12 24)}
   {:nimi "Joulupäivä" :pvm (luo-pvm-dec-kk vuosi 12 25)}
   {:nimi "Tapaninpäivä" :pvm (luo-pvm-dec-kk vuosi 12 26)}])

(defn- paasiaspaiva-vuodelle
  "Laskee pääsiäissunnuntain (Pääsiäispäivän) annetulle vuodelle käyttäen Butcherin menetelmää.
  https://fi.wikipedia.org/wiki/P%C3%A4%C3%A4si%C3%A4isen_laskeminen"
  [vuosi]
  (let [a (mod vuosi 19)
        b (quot vuosi 100)
        c (mod vuosi 100)
        d (quot b 4)
        e (mod b 4)
        f (quot (+ b 8) 25)
        g (quot (+ (- b f) 1) 3)
        h (mod (+ (* 19 a) (- b d g) 15) 30)
        i (quot c 4)
        k (mod c 4)
        l (mod (- (+ 32 (* 2 e) (* 2 i)) h k) 7)
        m (quot (+ a (* 11 h) (* 22 l)) 451)
        ;; kk
        n (quot (+ h (- l (* 7 m)) 114) 31)
        ;; paiva
        p (mod (+ h (- l (* 7 m)) 114) 31)
        paiva (+ p 1)]
    (luo-pvm-dec-kk vuosi n paiva)))

(defn- paasiaiseen-liittyvat-lomapaivat-vuodelle [vuosi]
  (let [paasiaispaiva (paasiaspaiva-vuodelle vuosi)]
    [{:nimi "Pitkäperjantai" :pvm (ajan-muokkaus paasiaispaiva false 2 :paiva)}
     {:nimi "Pääsiäispäivä" :pvm paasiaispaiva}
     {:nimi "2. Pääsiäispäivä" :pvm (ajan-muokkaus paasiaispaiva true 1 :paiva)}
     {:nimi "Helatorstai" :pvm (ajan-muokkaus paasiaispaiva true 39 :paiva)}
     {:nimi "Helluntaipäivä" :pvm (ajan-muokkaus paasiaispaiva true 49 :paiva)}]))

(defn loyda-ensimmainen-viikonpaiva-alkaen [pvm viikonpaiva]
  (let [pvm (joda-timeksi pvm)
        ;; Day-of-week antaa väärän järjestysluvun, jos pvm ei konvertoida ensin suomen aikavyöhykkeeseen
        aloitus-viikonpaiva (t/day-of-week (suomen-aikavyohykkeeseen pvm))]

    (if (= aloitus-viikonpaiva viikonpaiva)
      pvm
      (let [siirtyma-paivia (mod (- viikonpaiva aloitus-viikonpaiva) 7)]
        (ajan-muokkaus pvm true siirtyma-paivia :paiva)))))
(defn- aikavaleista-poimittavat-lomapaivat-vuodelle
  "Laskee aikavälien perusteella pääteltävät lomapäivät annetulle vuodelle (Juhannus ja pyhäinpäivä)"
  [vuosi]
  (let [juhannusaatto (loyda-ensimmainen-viikonpaiva-alkaen (luo-pvm-dec-kk vuosi 6 19) 5)]
    [{:nimi "Juhannusaatto" :pvm juhannusaatto}
     {:nimi "Juhannuspäivä" :pvm (ajan-muokkaus juhannusaatto true 1 :paiva)}
     {:nimi "Pyhäinpäivä" :pvm (loyda-ensimmainen-viikonpaiva-alkaen (luo-pvm-dec-kk vuosi 10 31) 6)}]))

(defn lomapaivat-vuodelle
  "Palauttaa kaikki Suomen viralliset lomapäivät annetulle vuodelle ISO8601 päivämäärinä."
  [vuosi]
  (map
    (fn [{:keys [nimi pvm]}]
      {:nimi nimi :pvm (df/unparse
                         (df/formatter-local "yyyy-MM-dd")
                         (suomen-aikavyohykkeeseen (joda-timeksi pvm)))})
    (concat
      (kiinteat-lomapaivat-vuodelle vuosi)
      (paasiaiseen-liittyvat-lomapaivat-vuodelle vuosi)
      (aikavaleista-poimittavat-lomapaivat-vuodelle vuosi))))

