(ns harja.palvelin.palvelut.urakat-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.domain.urakka :as u]
            [harja.domain.sopimus :as sop]
            [harja.domain.organisaatio :as o]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clojure.spec.alpha :as s])
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:import (harja.domain.roolit EiOikeutta)))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :tallenna-urakan-sopimustyyppi (component/using
                                           (->Urakat)
                                           [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest tallenna-urakan-sopimustyyppi-testi
  (let [urakanvalvoja (oulun-2005-urakan-tilaajan-urakanvalvoja)
        uusi-sopimustyyppi
        (kutsu-palvelua (:http-palvelin jarjestelma)
          :tallenna-urakan-sopimustyyppi urakanvalvoja
          {:urakka-id     @oulun-alueurakan-2005-2010-id
           :sopimustyyppi :kokonaisurakka})]
    (is (= uusi-sopimustyyppi :kokonaisurakka))
    (u (str "UPDATE urakka SET sopimustyyppi = NULL WHERE id = " @oulun-alueurakan-2005-2010-id))))


(deftest hae-urakka-testi
  (let [urakanvalvoja (oulun-2005-urakan-tilaajan-urakanvalvoja)
        haettu-urakka
        (kutsu-palvelua (:http-palvelin jarjestelma)
          :hae-urakka urakanvalvoja @oulun-alueurakan-2005-2010-id)
        sopimukset (:sopimukset haettu-urakka)
        [eka-sopimuksen-id eka-sopimuksen-sampoid] (first sopimukset)
        [toka-sopimuksen-id toka-sopimuksen-sampoid] (second sopimukset)]
    (is (= (:id haettu-urakka) @oulun-alueurakan-2005-2010-id) "haetun urakan id")
    (is (= (count sopimukset) 2) "haetun urakan sopimusten määrä")
    (is (= eka-sopimuksen-sampoid "8H05228/01") "haetun urakan sopimustesti")
    (is (= toka-sopimuksen-sampoid "THII-12-28555") "haetun urakan sopimustesti")
    (is (= (:alkupvm haettu-urakka) (java.sql.Date. 105 9 1)) "haetun urakan alkupvm")
    (is (= (:loppupvm haettu-urakka) (pvm/aikana (pvm/->pvm "30.9.2012") 23 59 59 999)) "haetun urakan loppupvm")))

(deftest urakan-kesa-ajan-tallennus
  (let [kesa-ajan-alku "01.05"
        kesa-ajan-loppu "30.09"
        urakanvalvoja (oulun-2005-urakan-tilaajan-urakanvalvoja)
        vastaus
        (kutsu-palvelua (:http-palvelin jarjestelma)
          :paivita-kesa-aika urakanvalvoja {:urakka-id @oulun-alueurakan-2005-2010-id
                                            :tiedot {:alkupvm kesa-ajan-alku :loppupvm kesa-ajan-loppu}})]
    (is (not (nil? vastaus)))
    (is (not (nil? (:kesakausi-alkupvm (first vastaus)))))
    (is (not (nil? (:kesakausi-loppupvm (first vastaus)))))

    (let [alku-localdate (.toLocalDate (:kesakausi-alkupvm (first vastaus)))
          loppu-localdate (.toLocalDate (:kesakausi-loppupvm (first vastaus)))]
      (is (= (.getYear (java.time.LocalDate/now)) (.getYear alku-localdate)))
      (is (= 1 (.getDayOfMonth alku-localdate)))
      (is (= java.time.Month/MAY (.getMonth alku-localdate)))
      (is (= (.getYear (java.time.LocalDate/now)) (.getYear loppu-localdate)))
      (is (= 30 (.getDayOfMonth loppu-localdate)))
      (is (= java.time.Month/SEPTEMBER (.getMonth loppu-localdate))))))

(deftest urakan-kesa-ajan-tallennus-loppu-ennen-alkua
  (let [kesa-ajan-alku "01.05"
        kesa-ajan-loppu "30.04"
        urakanvalvoja (oulun-2005-urakan-tilaajan-urakanvalvoja)
        vastaus (try (kutsu-palvelua (:http-palvelin jarjestelma)
                       :paivita-kesa-aika urakanvalvoja {:urakka-id @oulun-alueurakan-2005-2010-id
                                                         :tiedot {:alkupvm kesa-ajan-alku :loppupvm kesa-ajan-loppu}})
                  (catch Exception e e))]
    (is (= IllegalArgumentException (type vastaus)))
    (is (= (.getMessage vastaus) "Kesäajan alku oltava ennen loppuaikaa."))))

(deftest urakan-kesa-ajan-tallennus-karkauspaiva
  (let [kesa-ajan-alku "29.02"
        kesa-ajan-loppu "30.09"
        urakanvalvoja (oulun-2005-urakan-tilaajan-urakanvalvoja)
        vastaus (try (kutsu-palvelua (:http-palvelin jarjestelma)
                       :paivita-kesa-aika urakanvalvoja {:urakka-id @oulun-alueurakan-2005-2010-id
                                                         :tiedot {:alkupvm kesa-ajan-alku :loppupvm kesa-ajan-loppu}})
                  (catch Exception e e))]
    (is (= IllegalArgumentException (type vastaus)))
    (is (= (.getMessage vastaus) "Karkauspäivä ei ole sallittu alkamis- tai loppupäivä."))))

(deftest urakan-kesa-ajan-tallennus-virheellinen-formaatti
  (let [kesa-ajan-alku "diipadaa"
        kesa-ajan-loppu "30.09"
        urakanvalvoja (oulun-2005-urakan-tilaajan-urakanvalvoja)
        vastaus (try (kutsu-palvelua (:http-palvelin jarjestelma)
                       :paivita-kesa-aika urakanvalvoja {:urakka-id @oulun-alueurakan-2005-2010-id
                                                         :tiedot {:alkupvm kesa-ajan-alku :loppupvm kesa-ajan-loppu}})
                  (catch Exception e e))]
    (is (= IllegalArgumentException (type vastaus)))
    (is (= (.getMessage vastaus) (format "Päivämäärä %s ei ole oikean muotoinen päivämäärä." kesa-ajan-alku)))))

(deftest urakan-kesa-ajan-tallennus-ei-oikeutta
  (try+
    (let [kesa-ajan-alku "01.05"
          kesa-ajan-loppu "30.09"
          _ (kutsu-palvelua (:http-palvelin jarjestelma)
              :paivita-kesa-aika +kayttaja-tero+ {:urakka-id @oulun-alueurakan-2005-2010-id
                                                  :tiedot {:alkupvm kesa-ajan-alku :loppupvm kesa-ajan-loppu}})])
    (catch EiOikeutta e
      (is e))))

(deftest urakan-kesa-ajan-tallennus-ei-tilaajan-kayttaja
  (try+
    (let [kesa-ajan-alku "01.05"
          kesa-ajan-loppu "30.09"
          vastaus (try (kutsu-palvelua (:http-palvelin jarjestelma)
                         :paivita-kesa-aika +kayttaja-vastuuhlo-muhos+ {:urakka-id @oulun-alueurakan-2005-2010-id
                                                                        :tiedot {:alkupvm kesa-ajan-alku :loppupvm kesa-ajan-loppu}})
                    (catch SecurityException e e))]
      (is (= SecurityException (type vastaus)))
      (is (= (.getMessage vastaus) "Vain tilaaja voi asettaa urakan kesäajan")))))
