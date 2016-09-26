(ns harja.palvelin.ajastetut-tehtavat.paivystystarkistukset-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.ajastetut-tehtavat.paivystystarkistukset :as paivystajatarkistukset]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clj-time.core :as t]
            [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.palvelut.urakat :as urakat])
  (:use org.httpkit.fake))

(def +testi-fim-+ "https://localhost:6666/FIMDEV/SimpleREST4FIM/1/Group.svc/getGroupUsersFromEntitity")

(defn jarjestelma-fixture [testit]
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :integraatioloki (component/using (integraatioloki/->Integraatioloki nil) [:db])
          :fim (component/using
                 (fim/->FIM +testi-fim-+)
                 [:db :integraatioloki])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(def ilmoituksien-saajat
  [{:tunniste nil,
    :kayttajatunnus "A000001",
    :etunimi "Erkki",
    :sukunimi "Esimerkki",
    :sahkoposti "erkki.esimerkki@example.com",
    :puhelin "",
    :roolit ["ELY urakanvalvoja"],
    :organisaatio "ELY"}
   {:tunniste nil,
    :kayttajatunnus "A000002",
    :etunimi "Eero",
    :sukunimi "Esimerkki",
    :sahkoposti "eero.esimerkki@example.com",
    :puhelin "0400123456789",
    :roolit ["Urakan vastuuhenkilö"],
    :organisaatio "ELY"}])

(deftest urakoiden-paivystajien-haku-toimii
  (let [testitietokanta (tietokanta/luo-tietokanta testitietokanta)
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

(defn- hae-urakat-ilman-paivystysta [pvm]
  (let [testitietokanta (tietokanta/luo-tietokanta testitietokanta)
        urakat (urakat/hae-voimassa-olevat-urakat testitietokanta pvm)
        paivystykset (paivystajatarkistukset/hae-voimassa-olevien-urakoiden-paivystykset
                       testitietokanta
                       pvm)
        urakat-ilman-paivystysta (paivystajatarkistukset/urakat-ilman-paivystysta
                                   paivystykset
                                   urakat
                                   pvm)]
    urakat-ilman-paivystysta))

(deftest muhoksen-urakan-paivystys-loytyy
  (let [urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta (t/local-date 2016 1 1))]
    ;; Muhoksen urakalla päivitys kyseisenä aikana, eli ei sisälly joukkoon "urakat ilman päivystystä"
    (is (nil? (first (filter
                  #(= (:nimi %) "Muhoksen päällystysurakka")
                  urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat sisältyy
    (is (= (count urakat-ilman-paivystysta) 17))))

(deftest oulun-urakan-paivystys-loytyy
  (let [urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta (t/local-date 2015 11 2))]
    ;; Oulun 2014-2019 urakalla päivitys kyseisenä aikana, eli ei sisälly joukkoon "urakat ilman päivystystä"
    (is (nil? (first (filter
                       #(= (:nimi %) "Oulun alueurakka 2014-2019")
                       urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat sisältyy
    (is (= (count urakat-ilman-paivystysta) 17))))

(deftest oulun-ja-muhoksen-paivystys-loytyy
  (let [urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta (t/local-date 2015 12 2))]
    ;; Oulun 2014-2019 ja Muhoksen urakalla päivitys kyseisenä aikana
    (is (nil? (first (filter
                       #(or (= (:nimi %) "Oulun alueurakka 2014-2019")
                            (= (:nimi %) "Muhoksen päällystysurakka"))
                       urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat sisältyy
    (is (= (count urakat-ilman-paivystysta) 16))))

(deftest oulun-ja-muhoksen-paivystys-loytyy-2
  (let [urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta (t/local-date 2015 12 1))]
    ;; Oulun 2014-2019 ja Muhoksen urakalla päivitys kyseisenä aikana
    (is (nil? (first (filter
                       #(or (= (:nimi %) "Oulun alueurakka 2014-2019")
                            (= (:nimi %) "Muhoksen päällystysurakka"))
                       urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat sisältyy
    (is (= (count urakat-ilman-paivystysta) 16))))

(deftest oulun-ja-muhoksen-paivystys-loytyy-3
  (let [urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta (t/local-date 2015 12 6))]
    ;; Oulun 2014-2019 urakalla päivitys päättyy juuri tänä ajanhetkenä, eli ei sisälly joukkoon
    (is (nil? (first (filter
                       #(= (:nimi %) "Muhoksen päällystysurakka")
                       urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat sisältyy
    (is (= (count urakat-ilman-paivystysta) 17))))

(deftest kaikki-urakat-listataan-ilman-paivystysta
  (let [urakat-ilman-paivystysta (hae-urakat-ilman-paivystysta (t/local-date 2060 1 1))]
    ;; Ei urakoita käynnissä tänä aikana, mitään ei palaudu
    (is (= (count urakat-ilman-paivystysta) 0))))

(deftest ilmoituksien-saajien-haku-toimii
  (let [vastaus-xml (slurp (io/resource "xsd/fim/esimerkit/hae-urakan-kayttajat.xml"))]
    (with-fake-http
      [+testi-fim-+ vastaus-xml]
      (let [vastaus (paivystajatarkistukset/hae-ilmoituksen-saajat
                      (:fim jarjestelma)
                      "1242141-OULU2")]
        (is (= vastaus ilmoituksien-saajat))))))