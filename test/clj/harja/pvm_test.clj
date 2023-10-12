(ns harja.pvm-test
  "Harjan pvm-namespacen backendin testit"
  (:require
    [clojure.test :refer [deftest is use-fixtures testing]]
    [harja.pvm :as pvm]
    [clj-time.core :as t])
  (:import (org.joda.time LocalDateTime)))

(def nyt (let [tama-paiva (t/local-date 2005 10 10)]
           (t/date-time (t/year tama-paiva)
                        (t/month tama-paiva)
                        (t/day tama-paiva)
                        12)))

(deftest luominen
  (is (pvm/sama-pvm? (pvm/->pvm-date-timeksi "1.1.2020") (t/local-date 2020 1 1)))
  (is (pvm/sama-pvm? (t/local-date-time 2020 12 1 13 30 59) (pvm/->pvm-date-timeksi "1.12.2020"))))

(deftest ennen?
  (is (false? (pvm/ennen? nil nil)))
  (is (false? (pvm/ennen? nyt nil)))
  (is (false? (pvm/ennen? nil nyt)))
  (is (false? (pvm/ennen? nyt nyt)))
  (is (false? (pvm/ennen? (t/date-time 2005 10 10)
                          (pvm/->pvm-date-timeksi "30.12.2004"))))
  (is (false? (pvm/ennen? (t/plus nyt (t/hours 4))
                          nyt)))
  (is (true? (pvm/ennen? nyt
                         (t/plus nyt (t/hours 4))))))

(deftest sama-tai-ennen?
  (is (false? (pvm/sama-tai-ennen? nil nil)))
  (is (false? (pvm/sama-tai-ennen? nyt nil)))
  (is (false? (pvm/sama-tai-ennen? nil nyt)))
  (is (false? (pvm/sama-tai-ennen? (t/local-date-time 2005 10 10) (pvm/->pvm-date-timeksi "25.12.2004"))))
  (is (false? (pvm/sama-tai-ennen? (t/plus nyt (t/hours 4))
                                   nyt
                                   false)))
  (is (true? (pvm/sama-tai-ennen? (t/local-date 2005 10 10)
                                  (t/local-date 2005 10 10))))
  (is (true? (pvm/sama-tai-ennen? (t/local-date-time 2005 10 10 11 11 11)
                                  (t/local-date-time 2005 10 10 11 11 11)
                                  false)))
  (is (true? (pvm/sama-tai-ennen? (t/plus nyt (t/hours 4))
                                  nyt)))
  (is (true? (pvm/sama-tai-ennen? nyt
                                  nyt)))
  (is (true? (pvm/sama-tai-ennen? nyt
                                  (t/plus nyt (t/hours 4))))))

(deftest jalkeen?
  (is (false? (pvm/jalkeen? nil nil)))
  (is (false? (pvm/jalkeen? nyt nil)))
  (is (false? (pvm/jalkeen? nil nyt)))
  (is (true? (pvm/jalkeen? (t/date-time 2005 10 10)
                            (pvm/joda-timeksi (pvm/->pvm "30.12.2004")))))
  (is (false? (pvm/jalkeen? nyt nyt)))
  (is (false? (pvm/jalkeen? nyt
                            (t/plus nyt (t/hours 4)))))
  (is (true? (pvm/jalkeen? (t/plus nyt (t/hours 4))
                           nyt))))

(deftest sama-tai-jalkeen?
  (is (false? (pvm/sama-tai-jalkeen? nil nil)))
  (is (false? (pvm/sama-tai-jalkeen? nyt nil)))
  (is (false? (pvm/sama-tai-jalkeen? nil nyt)))
  (is (false? (pvm/sama-tai-jalkeen? nyt
                                     (t/plus nyt (t/hours 4))
                                     false)))
  (is (true? (pvm/sama-tai-jalkeen? (t/local-date 2005 10 10)
                                    (t/local-date 2005 10 10))))
  (is (true? (pvm/sama-tai-jalkeen? (pvm/->pvm-date-timeksi "20.10.2005")
                                    (t/local-date 2005 10 20))))
  (is (true? (pvm/sama-tai-jalkeen? (t/local-date-time 2005 10 10 11 11 11)
                                    (t/local-date-time 2005 10 10 11 11 11)
                                    false)))
  (is (true? (pvm/sama-tai-jalkeen? nyt
                                    (t/plus nyt (t/hours 4))
                                    true)))
  (is (true? (pvm/sama-tai-jalkeen? nyt
                                    nyt)))
  (is (true? (pvm/sama-tai-jalkeen? (t/plus nyt (t/hours 4))
                                    nyt))))

(deftest sama-pvm?
  (is (true? (pvm/sama-pvm? (t/local-date 2005 2 3)
                            (t/local-date 2005 2 3))))
  (is (true? (pvm/sama-pvm? (pvm/->pvm-date-timeksi "25.11.2020")
                            (t/local-date 2020 11 25)))))

(deftest sama-kuukausi?
  (is (true? (pvm/sama-kuukausi? (t/local-date 2005 2 3)
                                 (t/local-date 2005 2 25))))
  (is (true? (pvm/sama-kuukausi? (pvm/->pvm-date-timeksi "3.4.2020")
                                 (t/local-date 2020 4 25)))))

(deftest kuukauden-numero-toimii-myos-umlautilla
  (is (= 6 (pvm/kuukauden-numero "kesäkuu")))
  (is (= 6 (pvm/kuukauden-numero "kesakuu")))
  (is (= 7 (pvm/kuukauden-numero "heinäkuu")))
  (is (= 7 (pvm/kuukauden-numero "heinakuu")))
  (is (nil? (pvm/kuukauden-numero "täyskuu"))))

(deftest valissa?
  (is (true? (pvm/valissa? nyt nyt nyt)))
  (is (true? (pvm/valissa? nyt
                           (t/minus nyt (t/minutes 5))
                           (t/plus nyt (t/minutes 5)))))
  (is (true? (pvm/valissa? (t/plus nyt (t/minutes 8))
                           (t/minus nyt (t/minutes 5))
                           (t/plus nyt (t/minutes 5)))))
  (is (true? (pvm/valissa? (t/plus nyt (t/minutes 15))
                           (t/minus nyt (t/minutes 5))
                           (t/plus nyt (t/minutes 5))
                           true)))
  (is (false? (pvm/valissa? (t/plus nyt (t/minutes 15))
                            (t/minus nyt (t/minutes 5))
                            (t/plus nyt (t/minutes 5))
                            false))))

(deftest paivia-valissa-toimii
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [nyt
                                                  (t/plus nyt (t/days 5))]
                                                 [(t/plus nyt (t/days 1))
                                                  (t/plus nyt (t/days 3))])
         2))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [nyt
                                                  (t/plus nyt (t/days 2))]
                                                 [(t/plus nyt (t/days 1))
                                                  (t/plus nyt (t/days 5))])
         1))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [(t/plus nyt (t/days 1))
                                                  (t/plus nyt (t/days 3))]
                                                 [nyt
                                                  (t/plus nyt (t/days 2))])
         1))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [(t/plus nyt (t/days 1))
                                                  (t/plus nyt (t/days 3))]
                                                 [nyt
                                                  (t/plus nyt (t/days 5))])
         2))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [nyt
                                                  (t/plus nyt (t/days 3))]
                                                 [(t/plus nyt (t/days 5))
                                                  (t/plus nyt (t/days 10))])
         0))
  (is (= (pvm/paivia-aikavalien-leikkauskohdassa [nyt
                                                  (t/plus nyt (t/days 3))]
                                                 [(t/minus nyt (t/days 3))
                                                  (t/minus nyt (t/days 2))])
         0)))

(deftest aikavalit
  (testing "Kuukauden aikaväli"
    (let [tulos (pvm/kuukauden-aikavali nyt)]
      (is (= 2 (count tulos)))
      (is (= 0 (t/hour (first tulos))))
      (is (= 0 (t/minute (first tulos))))
      (is (= 23 (t/hour (second tulos))))
      (is (= 59 (t/minute (second tulos))))))

  (testing "Edellinen kuukausi aikavälina"
    (let [tulos (pvm/ed-kk-aikavalina nyt)
          alku (LocalDateTime/fromDateFields (first tulos))
          loppu (LocalDateTime/fromDateFields (second tulos))]
      (is (= 2 (count tulos)))
      (is (= (t/month alku)
             (t/month loppu)
             (t/month (t/minus nyt (t/months 1)))))
      (is (= 0 (t/hour alku)))
      (is (= 0 (t/minute alku)))
      (is (= 23 (t/hour loppu)))
      (is (= 59 (t/minute loppu)))))

  (testing "Edellinen kuukausi date vektorina tammikuu"
    (let [tulos (pvm/ed-kk-date-vektorina (pvm/joda-timeksi  (pvm/->pvm "5.1.2022")))]
      (is (= 2 (count tulos)))
      (is (= (pvm/dateksi (first tulos)) #inst "2021-12-01T00:00:00.000-00:00"))
      (is (= (pvm/dateksi (second tulos)) #inst "2021-12-31T00:00:00.000-00:00"))))

  (testing "Edellinen kuukausi date vektorina syyskuu"
    (let [tulos (pvm/ed-kk-date-vektorina (pvm/joda-timeksi  (pvm/->pvm "25.9.2022")))]
      (is (= 2 (count tulos)))
      (is (= (pvm/dateksi (first tulos)) #inst "2022-08-01T00:00:00.000-00:00"))
      (is (= (pvm/dateksi (second tulos)) #inst "2022-08-31T00:00:00.000-00:00")))))

(deftest vesivaylaurakan-hoitokausi
  (is (= (pvm/->pvm "1.8.2017") (pvm/vesivaylien-hoitokauden-alkupvm 2017)))
  (is (= (pvm/->pvm "31.7.2018") (pvm/vesivaylien-hoitokauden-loppupvm 2018))))


(deftest aikavalit-leikkaavat
  (is (false? (pvm/aikavalit-leikkaavat? (t/date-time 2017 1 1)
                                         (t/date-time 2017 12 31)
                                         (t/date-time 2018 1 1)
                                         (t/date-time 2018 12 31)))
      "Toisiaan ei leikkaavat välit tunnistetaan oikein")

  (is (true? (pvm/aikavalit-leikkaavat? (t/date-time 2017 1 1)
                                        (t/date-time 2017 12 31)
                                        (t/date-time 2017 12 24)
                                        (t/date-time 2018 12 31)))
      "Toisiaan leikkaavat välit tunnisteaan oikein")

  (is (false? (pvm/aikavalit-leikkaavat? (t/date-time 2017 1 1 0 0)
                                         (t/date-time 2017 12 31 0 0)
                                         (t/date-time 2017 12 31 1 1)
                                         (t/date-time 2018 12 31 0 0)))
      "Kellonaika osataan huomioida oikein limittyvillä väleillä")

  (is (true? (pvm/aikavalit-leikkaavat? (t/date-time 2017 1 1 0 0)
                                        (t/date-time 2017 12 31 0 0)
                                        (t/date-time 2017 12 31 0 0)
                                        (t/date-time 2018 12 31 0 0)))
      "Kellonaika osataan huomioida oikein leikkaavilla väleilleä")

  (is (false? (pvm/aikavalit-leikkaavat? (t/date-time 2017 1 1 0 0)
                                         (t/date-time 2017 12 31 0 0)
                                         (t/date-time 2018 1 1 1 1)
                                         nil))
      "Toimii oikein kun toiselta päivämäärältä puuttuu loppupäivämäärä")

  (is (true? (pvm/aikavalit-leikkaavat? (t/date-time 2017 1 1 0 0)
                                        (t/date-time 2017 12 31 0 0)
                                        (t/date-time 2017 12 24 1 1)
                                        nil))
      "Toimii oikein kun toiselta päivämäärältä puuttuu loppupäivämäärä")

  (is (false? (pvm/aikavalit-leikkaavat? (t/date-time 2017 1 1 0 0)
                                         (t/date-time 2017 12 31 0 0)
                                         nil
                                         (t/date-time 2018 1 1 1 1)))
      "Toimii oikein kun toiselta päivämäärältä puuttuu alkupäivämäärä")

  (is (true? (pvm/aikavalit-leikkaavat? (t/date-time 2017 1 1 0 0)
                                        (t/date-time 2017 12 31 0 0)
                                        nil
                                        (t/date-time 2017 12 24 1 1)))
      "Toimii oikein kun toiselta päivämäärältä puuttuu alkupäivämäärä"))

(deftest paivat-valissa
  (let [alku (t/date-time 2018 1 1)
        loppu (t/date-time 2018 1 1)
        paivat (pvm/paivat-aikavalissa alku loppu)]
    (is (= 1 (count paivat))
        "Jos alku- ja loppupäivämäärä on sama, palautetaan vain 1 päivä")
    (is (t/equal? alku (first paivat))
        "Jos alku- ja loppupäivämäärä on sama, palautetaan alkupvm"))

  (let [alku (t/date-time 2018 1 2)
        loppu (t/date-time 2018 1 1)
        paivat (pvm/paivat-aikavalissa alku loppu)]
    (is (= 1 (count paivat))
        "Jos loppupäivämäärä on aiemmin kuin alku, palautetaan vain 1 päivä")
    (is (t/equal? alku (first paivat))
        "Jos loppupäivämäärä on aiemmin kuin alku, palautetaan alkupvm"))

  (let [alku (t/date-time 2018 1 1)
        loppu (t/date-time 2018 1 4)
        paivat (pvm/paivat-aikavalissa alku loppu)]
    (is (= 4 (count paivat)) "Välissä on 3 päivää")
    (is (t/equal? alku (first paivat)) "Alkupäivämäärä on ensimmäinen")
    (is (t/equal? (t/date-time 2018 1 2) (second paivat)) "Välissä oleva päivä on oikein")
    (is (t/equal? (t/date-time 2018 1 3) (nth paivat 2)) "Välissä oleva päivä on oikein")
    (is (t/equal? loppu (last paivat)) "Loppupäivämäärä on viimeinen")))

(deftest aikavali-paivina-eri-input-tyyppeja
  (is (= 1 (pvm/aikavali-paivina (pvm/->pvm "31.12.2019") (pvm/->pvm "1.1.2020")))))

(deftest montako-paivaa-valissa-test
  (let [alkupvm (pvm/->pvm "01.01.2020")
        loppupvm (pvm/->pvm "02.01.2020")]
    (is (= (pvm/montako-paivaa-valissa alkupvm loppupvm) 1))
    (is (= (pvm/montako-paivaa-valissa loppupvm alkupvm) -1))))

(deftest hoitokauden-stringin-luonti-alkuvuodesta
  (let [alkuvuosi 2021]
    (is (= "01.10.2021-30.09.2022" (pvm/hoitokausi-str-alkuvuodesta alkuvuosi)) "Hoitokauden formatointi onnistuu")
    (is (= "01.10.2022-30.09.2023" (pvm/hoitokausi-str-alkuvuodesta (inc alkuvuosi))) "Hoitokauden formatointi onnistuu")
    (is (nil? (pvm/hoitokausi-str-alkuvuodesta nil)) "Nil ei aiheuta poikkeutta")))

(deftest rajapinta-str-aika->sql-timestamp-test-toimii
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-14T09:07:20.162457Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-14T09:07:20.12345Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-14T09:07:20.1234Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-14T09:07:20.123Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-14T09:07:20.12Z") #inst "2023-04-14T09:07:20.000-00:00"))
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-14T09:07:20.1Z") #inst "2023-04-14T09:07:20.000-00:00")))

(deftest rajapinta-str-aika->sql-timestamp-test-toimii2
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-20T07:21:17+01") #inst "2023-04-20T06:21:17.000-00:00"))
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-20T08:21:17+03") #inst "2023-04-20T05:21:17.000-00:00"))
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-14T09:07:20.162457Z") #inst "2023-04-14T09:07:20.000-00:00")))

(deftest rajapinta-str-aika->sql-timestamp-test-epaonnistuu
  (is (= (pvm/rajapinta-str-aika->sql-timestamp "2023-04-14T09:07:2") nil)))

(deftest suomen-lomapaivat-vuodelle
  ;; https://www.timeanddate.com/holidays/finland/2033 (tarkasta täältä)
  (is (= (pvm/lomapaivat-vuodelle 2033)
        '({:nimi "Uudenvuodenpäivä", :pvm "2033-01-01"}
          {:nimi "Loppiainen", :pvm "2033-01-06"}
          {:nimi "Vappu", :pvm "2033-05-01"}
          {:nimi "Itsenäisyyspäivä", :pvm "2033-12-06"}
          {:nimi "Jouluaatto", :pvm "2033-12-24"}
          {:nimi "Joulupäivä", :pvm "2033-12-25"}
          {:nimi "Tapaninpäivä", :pvm "2033-12-26"}
          {:nimi "Pitkäperjantai", :pvm "2033-04-15"}
          {:nimi "Pääsiäispäivä", :pvm "2033-04-17"}
          {:nimi "2. Pääsiäispäivä", :pvm "2033-04-18"}
          {:nimi "Helatorstai", :pvm "2033-05-26"}
          {:nimi "Helluntaipäivä", :pvm "2033-06-05"}
          {:nimi "Juhannusaatto", :pvm "2033-06-24"}
          {:nimi "Juhannuspäivä", :pvm "2033-06-25"}
          {:nimi "Pyhäinpäivä", :pvm "2033-11-05"})))

  (is (= (pvm/lomapaivat-vuodelle 2023)
        '({:nimi "Uudenvuodenpäivä", :pvm "2023-01-01"}
          {:nimi "Loppiainen", :pvm "2023-01-06"}
          {:nimi "Vappu", :pvm "2023-05-01"}
          {:nimi "Itsenäisyyspäivä", :pvm "2023-12-06"}
          {:nimi "Jouluaatto", :pvm "2023-12-24"}
          {:nimi "Joulupäivä", :pvm "2023-12-25"}
          {:nimi "Tapaninpäivä", :pvm "2023-12-26"}
          {:nimi "Pitkäperjantai", :pvm "2023-04-07"}
          {:nimi "Pääsiäispäivä", :pvm "2023-04-09"}
          {:nimi "2. Pääsiäispäivä", :pvm "2023-04-10"}
          {:nimi "Helatorstai", :pvm "2023-05-18"}
          {:nimi "Helluntaipäivä", :pvm "2023-05-28"}
          {:nimi "Juhannusaatto", :pvm "2023-06-23"}
          {:nimi "Juhannuspäivä", :pvm "2023-06-24"}
          {:nimi "Pyhäinpäivä", :pvm "2023-11-04"})))

  (is (= (pvm/lomapaivat-vuodelle 2022)
        '({:nimi "Uudenvuodenpäivä", :pvm "2022-01-01"}
          {:nimi "Loppiainen", :pvm "2022-01-06"}
          {:nimi "Vappu", :pvm "2022-05-01"}
          {:nimi "Itsenäisyyspäivä", :pvm "2022-12-06"}
          {:nimi "Jouluaatto", :pvm "2022-12-24"}
          {:nimi "Joulupäivä", :pvm "2022-12-25"}
          {:nimi "Tapaninpäivä", :pvm "2022-12-26"}
          {:nimi "Pitkäperjantai", :pvm "2022-04-15"}
          {:nimi "Pääsiäispäivä", :pvm "2022-04-17"}
          {:nimi "2. Pääsiäispäivä", :pvm "2022-04-18"}
          {:nimi "Helatorstai", :pvm "2022-05-26"}
          {:nimi "Helluntaipäivä", :pvm "2022-06-05"}
          {:nimi "Juhannusaatto", :pvm "2022-06-24"}
          {:nimi "Juhannuspäivä", :pvm "2022-06-25"}
          {:nimi "Pyhäinpäivä", :pvm "2022-11-05"}))))

(deftest seuraava-arkipaiva
  ;; Testataan seuraava arkipäivä viikolla (10.1. = arkipäivä (tiistai))
  ;; --> Seuraava arkipäivä = 11.1. (keskiviikko)
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 1 10))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 1 11))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm)))

  ;; Testataan tavallinen viikonloppu (13.1. = arkipäivä (perjantai))
  ;; --> Seuraava arkipäivä = 16.1. (maanantai)
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 1 13))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 1 16))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm)))

  ;; Testataan uusivuosi (31.12.2020 = arkipäivä (torstai), 1.1.2021. = Uudenvuodenpäivä (perjantai))
  ;; --> Seuraava arkipäivä = 4.1.2021 (maanantai)
  ;; Tässä on valittu testivuosi 2020 siten, että arkipyhiä sattuu sopivasti viikolle.
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2020 12 31))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2021 1 4))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm)))

  ;; Testataan loppiainen (5.1. = arkipäivä (torstai), 6.1.2021. = Loppiainen (perjantai))
  ;; --> Seuraava arkipäivä = 9.1. (maanantai)
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 1 5))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 1 9))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm)))

  ;; Testataan pääsiäinen (6.4. = arkipäivä, 7.4. = pitkäperjantai, 10.4. = 2. pääsiäispäivä)
  ;; --> Seuraava arkipäivä = 11.4 (tiistai)
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 4 6))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 4 11))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm)))

  ;; Testataan vappu (28.4. = arkiperjantai, 1.5 = vappu)
  ;; --> Seuraava arkipäivä = 2.5 (tiistai)
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 4 28))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 5 2))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm)))

  ;; Testataan helatorstai (17.5. = arkipäivä, 18.5. = helatorstai)
  ;; --> Seuraava arkipäivä = 19.5 (perjantai)
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 5 17))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 5 19))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm)))

  ;; Testataan juhannus (22.6. = arkipäivä, 23.6. = juhannusaatto, 26.5. = juhannuspäivä)
  ;; --> Seuraava arkipäivä = 26.6 (maanantai)
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 6 22))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 6 26))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm)))

  ;; Testataan itsenäisyyspäivä (5.12. = arkipäivä, 6.12. = itsenäisyyspäivä)
  ;; --> Seuraava arkipäivä = 7.12. (torstai)
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 12 5))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 12 7))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm)))

  ;; Testataan joulunaika (22.12. = arkipäivä, 24.12. = jouluaatto, 25.12. = joulupäivä, 26.12. = Tapaninpäivä)
  ;; --> Seuraava arkipäivä = 27.12. (torstai)
  (let [testipvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 12 22))
        seuraava-pvm (pvm/joda-timeksi (pvm/luo-pvm-dec-kk 2023 12 27))]
    (is (= (pvm/seuraava-arkipaiva testipvm) seuraava-pvm))))
