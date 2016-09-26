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

(deftest urakat-ilman-paivystysta-toimii
  ;; Testidatassa ei yhdelläkään urakalla päivystystä annettuna aikana, eli palautuu sama data takaisin
  (let [testitietokanta (tietokanta/luo-tietokanta testitietokanta)
        urakat (urakat/hae-voimassa-olevat-urakat testitietokanta (t/local-date 2016 1 1))
        paivystykset (paivystajatarkistukset/hae-voimassa-olevien-urakoiden-paivystykset
                                 testitietokanta
                                 (t/local-date 2016 1 1))
        urakat-ilman-paivystysta (paivystajatarkistukset/urakat-ilman-paivystysta
                                   paivystykset
                                   urakat
                                   (t/local-date 2016 1 1))]
    ;; Muhoksen urakalla päivitys kyseisenä aikana, eli ei sisälly joukkoon "urakat ilman päivystystä"
    (is (nil? (first (filter
                  #(= (:nimi %) "Muhoksen päällystysurakka")
                  urakat-ilman-paivystysta))))

    ;; Kaikki muut urakat sisältyy
    (is (= (count urakat-ilman-paivystysta) 17))))

(deftest ilmoituksien-saajien-haku-toimii
  (let [vastaus-xml (slurp (io/resource "xsd/fim/esimerkit/hae-urakan-kayttajat.xml"))]
    (with-fake-http
      [+testi-fim-+ vastaus-xml]
      (let [vastaus (paivystajatarkistukset/hae-ilmoituksen-saajat
                      (:fim jarjestelma)
                      "1242141-OULU2")]
        (is (= vastaus ilmoituksien-saajat))))))