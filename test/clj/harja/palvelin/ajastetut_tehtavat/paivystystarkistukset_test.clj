(ns harja.palvelin.ajastetut-tehtavat.paivystystarkistukset-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.ajastetut-tehtavat.paivystystarkistukset :as paivystajatarkistukset]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.fim-test :as fim-test]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.palvelut.urakat :as urakat])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (pystyta-harja-tarkkailija!)
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :integraatioloki (component/using (integraatioloki/->Integraatioloki nil) [:db])
          :fim (component/using
                 (fim/->FIM fim-test/+testi-fim+)
                 [:db :integraatioloki])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once jarjestelma-fixture)

(def ilmoituksien-saajat
  [{:etunimi "Erkki"
    :kayttajatunnus "A000001"
    :organisaatio "ELY"
    :poistettu false
    :puhelin ""
    :roolinimet ["ELY_Urakanvalvoja"]
    :roolit ["ELY urakanvalvoja"]
    :sahkoposti "erkki.esimerkki@example.com"
    :sukunimi "Esimerkki"
    :tunniste nil}
   {:etunimi "Eero"
    :kayttajatunnus "A000002"
    :organisaatio "ELY"
    :poistettu false
    :puhelin "0400123456789"
    :roolinimet ["vastuuhenkilo"]
    :roolit ["Urakan vastuuhenkilö"]
    :sahkoposti "eero.esimerkki@example.com"
    :sukunimi "Esimerkki"
    :tunniste nil}])

(deftest urakoiden-paivystajien-haku-toimii
  (let [testitietokanta (:db jarjestelma)
        paivystykset (paivystajatarkistukset/hae-voimassa-olevien-urakoiden-paivystykset
                                 testitietokanta
                                 (t/local-date 2016 10 1))]
    ;; Oulun alueurakka 2014-2019 löytyy 3 päivystystä
    (is (= (count (filter
                    #(= (:urakka-nimi %) "Oulun alueurakka 2014-2019")
                    paivystykset)))
        3)
    ;; Muhoksen urakassa on yksi päivystys
    (is (= (count (filter
                    #(= (:urakka-nimi %) "Muhoksen päällystysurakka")
                    paivystykset)))
        1)))

(deftest hae-kaynnissa-olevat-urakat-paivystystarkistukseen-toimii
  (let [testitietokanta (:db jarjestelma)
        urakat (paivystajatarkistukset/hae-urakat-paivystystarkistukseen testitietokanta (t/local-date 2016 1 1))]
    (is (= (count urakat) 18))
    urakat))

(deftest hae-kaynnissa-olevat-urakat-paivystystarkistukseen-toimii
  (let [testitietokanta (:db jarjestelma)
        urakat (paivystajatarkistukset/hae-urakat-paivystystarkistukseen testitietokanta (t/local-date 2007 1 1))]
    (is (= (count urakat) 1))
    urakat))

(defn- hae-urakat-ilman-paivystysta [pvm]
  (let [testitietokanta (:db jarjestelma)
        urakat (paivystajatarkistukset/hae-urakat-paivystystarkistukseen testitietokanta pvm)
        paivystykset (paivystajatarkistukset/hae-voimassa-olevien-urakoiden-paivystykset
                       testitietokanta
                       pvm)
        urakat-ilman-paivystysta (paivystajatarkistukset/urakat-ilman-paivystysta
                                   paivystykset
                                   urakat
                                   pvm)]
    urakat-ilman-paivystysta))

(deftest muhoksen-urakan-paivystys-loytyy
  (let [pvm (t/local-date 2016 1 1)
        testitietokanta (:db jarjestelma)
        urakat (paivystajatarkistukset/hae-urakat-paivystystarkistukseen testitietokanta pvm)
        urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta pvm)]
    ;; Muhoksen urakalla päivitys kyseisenä aikana, eli ei sisälly joukkoon "urakat ilman päivystystä"
    (is (nil? (first (filter
                  #(= (:nimi %) "Muhoksen päällystysurakka")
                  urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat sisältyy
    (is (= (count urakat-ilman-paivystysta) (- (count urakat) 1)))))

(deftest oulun-urakan-paivystys-loytyy
  (let [pvm (t/local-date 2015 11 2)
        testitietokanta (:db jarjestelma)
        urakat (paivystajatarkistukset/hae-urakat-paivystystarkistukseen testitietokanta pvm)
        urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta pvm)]
    ;; Oulun 2014-2019 urakalla päivitys kyseisenä aikana, eli ei sisälly joukkoon "urakat ilman päivystystä"
    (is (nil? (first (filter
                       #(= (:nimi %) "Oulun alueurakka 2014-2019")
                       urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat sisältyy
    (is (= (count urakat-ilman-paivystysta) (- (count urakat) 1)))))

(deftest oulun-urakan-paivystys-loytyy-paivystyksen-alkupaivana
  (let [pvm (t/local-date 2015 11 1)
        testitietokanta (:db jarjestelma)
        urakat (paivystajatarkistukset/hae-urakat-paivystystarkistukseen testitietokanta pvm)
        urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta pvm)]
    ;; Oulun 2014-2019 urakalla päivitys alkaa samana päivänä, eli ei sisälly joukkoon "urakat ilman päivystystä"
    (is (nil? (first (filter
                       #(= (:nimi %) "Oulun alueurakka 2014-2019")
                       urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat sisältyy
    (is (= (count urakat-ilman-paivystysta) (- (count urakat) 1)))))

(deftest oulun-ja-muhoksen-paivystys-loytyy
  (let [pvm (t/local-date 2015 12 5)
        testitietokanta (:db jarjestelma)
        urakat (paivystajatarkistukset/hae-urakat-paivystystarkistukseen testitietokanta pvm)
        urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta pvm)]
    ;; Oulun 2014-2019 ja Muhoksen urakalla päivitys kyseisenä aikana
    (is (nil? (first (filter
                       #(= (:nimi %) "Oulun alueurakka 2014-2019")
                       urakat-ilman-paivystysta))))
    (is (nil? (first (filter
                       #(= (:nimi %) "Muhoksen päällystysurakka")
                       urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat ilman päivytystä
    (is (= (count urakat-ilman-paivystysta) (- (count urakat) 2)))))

(deftest kaikki-urakat-listataan-ilman-paivystysta
  (let [pvm (t/local-date 2060 1 1)
        testitietokanta (:db jarjestelma)
        urakat (paivystajatarkistukset/hae-urakat-paivystystarkistukseen testitietokanta pvm)
        urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta (t/local-date 2060 1 1))]
    ;; Ei urakoita käynnissä tänä aikana, mitään ei palaudu
    (is (= (count urakat-ilman-paivystysta) (count urakat)))))

(deftest ilmoituksien-saajien-haku-toimii
  (let [vastaus-xml (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-hoidon-urakan-kayttajat.xml"))]
    (with-fake-http
      [fim-test/+testi-fim+ vastaus-xml]
      (let [vastaus (paivystajatarkistukset/hae-ilmoituksen-saajat
                      (:fim jarjestelma)
                      "1242141-OULU2")]
        (is (= vastaus ilmoituksien-saajat))))))
