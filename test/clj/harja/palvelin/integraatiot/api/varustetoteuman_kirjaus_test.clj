(ns harja.palvelin.integraatiot.api.varustetoteuman-kirjaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.api.tyokalut :as tyokalut]
            [org.httpkit.fake :refer [with-fake-http]]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.varustetoteuma :as api-varustetoteuma]
            [harja.palvelin.integraatiot.tierekisteri.tietolajit :as tietolajit]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [harja.palvelin.integraatiot.tierekisteri.tierekisteri-komponentti :as tierekisteri])
  (:import (org.postgis PGgeometry)))

(def kayttaja "destia")
(def +testi-tierekisteri-url+ "harja.testi.tierekisteri")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :tierekisteri (component/using (tierekisteri/->Tierekisteri +testi-tierekisteri-url+ nil) [:db :integraatioloki :pois-kytketyt-ominaisuudet])
    :api-varusteoteuma (component/using
                         (api-varustetoteuma/->Varustetoteuma)
                         [:http-palvelin :db :integraatioloki :tierekisteri :pois-kytketyt-ominaisuudet])))

(use-fixtures :each jarjestelma-fixture)

(deftest tallenna-varustetoteuma
  (let [hae-tietolaji-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        lisaa-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        paivita-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        poista-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        varustetoteumat-ennen-pyyntoa (ffirst (q
                                                (str "SELECT count(*)
                                                       FROM varustetoteuma")))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        payload (-> "test/resurssit/api/varustetoteuma.json"
                    slurp
                    (.replace "__ID__" (str ulkoinen-id)))
        varustetoteuma-api-url ["/api/urakat/" urakka "/toteumat/varuste/"]]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-xml
       (str +testi-tierekisteri-url+ "/lisaatietue") lisaa-tietue-xml
       (str +testi-tierekisteri-url+ "/paivitatietue") paivita-tietue-xml
       (str +testi-tierekisteri-url+ "/poistatietue") poista-tietue-xml
       #"http?://localhost" :allow]
      (let [vastaus-lisays (api-tyokalut/post-kutsu varustetoteuma-api-url kayttaja portti payload)]
        (is (= 200 (:status vastaus-lisays)))
        (let [varustetoteumat-pyynnon-jalkeen (ffirst (q
                                                        (str "SELECT count(*)
                                                       FROM varustetoteuma")))
              toteuma-kannassa (first (q (str "SELECT ulkoinen_id, suorittajan_ytunnus, suorittajan_nimi "
                                              "FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              toteuma-geometria (ffirst (q (str "SELECT reitti FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              tunnisteet-kannassa (flatten (q (str "SELECT tunniste FROM varustetoteuma WHERE toteuma = " toteuma-id)))
              varustetoteuma-geometriat-kannassa (q (str "SELECT toimenpide, sijainti FROM varustetoteuma WHERE toteuma = " toteuma-id))
              varuste-arvot-kannassa (q (str "SELECT toimenpide, arvot FROM varustetoteuma WHERE toteuma = " toteuma-id))
              lahetystiedot-kannassa (flatten (q (str "SELECT lahetetty_tierekisteriin FROM varustetoteuma WHERE toteuma = " toteuma-id ";")))
              toimenpidetyypit-kannassa (flatten (q (str "SELECT toimenpide FROM varustetoteuma WHERE toteuma = " toteuma-id)))]

          (is (= (+ varustetoteumat-ennen-pyyntoa 4) varustetoteumat-pyynnon-jalkeen) "Lisättiin 4 varustetoteumaa")

          ;; Tiedot tallennettu oikein
          (is (= toteuma-kannassa [ulkoinen-id "8765432-1" "Tehotekijät Oy"]))
          (is (every? #(= true %) lahetystiedot-kannassa))
          (is (every? #(if (= (first %) "poistettu")
                        (= (nil? (second %))) ;; Poisto-toimenpiteille ei lisätty arvoja, muille lisättiin
                        (= (string? (second %))))
                      varuste-arvot-kannassa))
          (is (every? string? tunnisteet-kannassa))
          (is (= (count (filter #(= % "lisatty") toimenpidetyypit-kannassa)) 1))
          (is (= (count (filter #(= % "paivitetty") toimenpidetyypit-kannassa)) 1))
          (is (= (count (filter #(= % "poistettu") toimenpidetyypit-kannassa)) 1))
          (is (not (.isEmpty (.getGeometry toteuma-geometria))))
          (is (every? #(if (= (first %) "poistettu")
                        (= (nil? (second %))) ;; Poisto-toimenpiteellä ei ole geometriaa, muilla on
                        (instance? PGgeometry (second %)))
                      varustetoteuma-geometriat-kannassa))
          (is (= (count (filter #(= % "tarkastus") toimenpidetyypit-kannassa)) 1))

          ;; Vastauksessa palautuu uusi id lisäystoimenpiteelle
          (let [tunnisteet (-> (:body vastaus-lisays) (json/read-str) (get "tunnisteet"))]
            (is (vector? tunnisteet))
            (is (= (count tunnisteet) 1))
            (is (str/starts-with? (first tunnisteet) "HAR"))))))

    ;; Lähetetään sama pyyntö uudelleen. Varustetoteumien määrä ei saa lisääntyä eikä niitä saa lähettää
    ;; uudelleen tierekisteriin. Käytännössä mitään ei saa tapahtua.

    (with-fake-http
      [#"http?://localhost" :allow]
      (let [varustetoteumat-ennen-uutta-pyyntoa (ffirst (q
                                                          (str "SELECT count(*)
                                                                FROM varustetoteuma")))
            vastaus-lisays (api-tyokalut/post-kutsu varustetoteuma-api-url kayttaja portti
                                                    payload)]
        (is (= 200 (:status vastaus-lisays)))
        (let [varustetoteumat-uuden-pyynnon-jalkeen (ffirst (q
                                                              (str "SELECT count(*)
                                                                    FROM varustetoteuma")))]
          (is (= varustetoteumat-ennen-uutta-pyyntoa
                 varustetoteumat-uuden-pyynnon-jalkeen))
          ;; Edelleen järjestelmä palauttaa lisäystoimenpiteiden uudet tunnisteet
          (let [tunnisteet (-> (:body vastaus-lisays) (json/read-str) (get "tunnisteet"))]
            (is (vector? tunnisteet))
            (is (= (count tunnisteet) 1))
            (is (str/starts-with? (first tunnisteet) "HAR"))))))))


(deftest tallenna-varustetoteuma-huonolla-kayttajalla
  (let [hae-tietolaji-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        lisaa-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        paivita-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        poista-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        varustetoteumat-ennen-pyyntoa (ffirst (q
                                                (str "SELECT count(*)
                                                       FROM varustetoteuma")))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        payload (-> "test/resurssit/api/varustetoteuma.json"
                    slurp
                    (.replace "__ID__" (str ulkoinen-id)))
        varustetoteuma-api-url ["/api/urakat/" urakka "/toteumat/varuste/"]]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-xml
       (str +testi-tierekisteri-url+ "/lisaatietue") lisaa-tietue-xml
       (str +testi-tierekisteri-url+ "/paivitatietue") paivita-tietue-xml
       (str +testi-tierekisteri-url+ "/poistatietue") poista-tietue-xml
       #"http?://localhost" :allow]
      (let [vastaus-lisays (api-tyokalut/post-kutsu varustetoteuma-api-url "skanska" portti payload)]
        (is (>= 400 (:status vastaus-lisays)))
        (is (str/includes? (-> (:body vastaus-lisays)
                               (json/read-str)
                               (get "virheet")
                               first
                               (get "virhe")
                               (get "koodi"))
                           "kayttajalla-puutteelliset-oikeudet"))))))

(deftest tarkista-virheellisen-varustetoteuman-kirjaaminen
  (let [hae-tietolaji-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        lisaa-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        paivita-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        poista-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
        varustetoteumat-ennen-pyyntoa (ffirst (q
                                                (str "SELECT count(*)
                                                       FROM varustetoteuma")))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        payload (-> "test/resurssit/api/varustetoteuma_virheelliset_arvot.json"
                    slurp
                    (.replace "__ID__" (str ulkoinen-id)))
        varustetoteuma-api-url ["/api/urakat/" urakka "/toteumat/varuste/"]]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-xml
       (str +testi-tierekisteri-url+ "/lisaatietue") lisaa-tietue-xml
       (str +testi-tierekisteri-url+ "/paivitatietue") paivita-tietue-xml
       (str +testi-tierekisteri-url+ "/poistatietue") poista-tietue-xml
       #"http?://localhost" :allow]
      (let [vastaus-lisays (api-tyokalut/post-kutsu varustetoteuma-api-url kayttaja portti
                                                    payload)
            varustetoteumat-pyynnon-jalkeen (ffirst (q
                                                      (str "SELECT count(*)
                                                       FROM varustetoteuma")))]
        (is (= 400 (:status vastaus-lisays)))
        (is (str/includes? (-> (:body vastaus-lisays)
                               (json/read-str)
                               (get "virheet")
                               first
                               (get "virhe")
                               (get "viesti"))
                           "Tietolajin arvoissa on ylimääräisiä kenttiä"))
        (is (= varustetoteumat-ennen-pyyntoa varustetoteumat-pyynnon-jalkeen))))))

(deftest testaa-varustetoteuman-tallennus-kun-tietolajien-haku-epaonnistuu
  (tietolajit/tyhjenna-tietolajien-kuvaukset-cache)
  (let [hae-tietolaji-xml-virhe (slurp (io/resource "xsd/tierekisteri/esimerkit/virhe-vastaus-tietolajia-ei-loydy-response.xml"))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        payload (-> "test/resurssit/api/varustetoteuma.json"
                    slurp
                    (.replace "__ID__" (str ulkoinen-id)))
        varustetoteuma-api-url ["/api/urakat/" urakka "/toteumat/varuste/"]]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-xml-virhe
       #"http?://localhost" :allow]
      (let [varustetoteumat-ennen-pyyntoa (ffirst (q
                                                    (str "SELECT count(*)
                                                                FROM varustetoteuma")))
            vastaus-lisays (api-tyokalut/post-kutsu varustetoteuma-api-url kayttaja portti
                                                    payload)]

        ;; Oletus on, ettei yksikään toimenpide tallennu Harjaan eikä niitä lähetetä tierekisteriin, koska
        ;; arvoja ei voitu käsitellä
        (is (= 500 (:status vastaus-lisays)))
        (let [varustetoteumat-pyynnon-jalkeen (ffirst (q
                                                        (str "SELECT count(*)
                                                       FROM varustetoteuma")))]
          (is (= varustetoteumat-ennen-pyyntoa varustetoteumat-pyynnon-jalkeen)))))))

(deftest testaa-varustetoteuman-tallennus-kun-tierekisteriin-lahetykset-epaonnistuvat
  (let [hae-tietolaji-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
        lisaa-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/virhe-tietueen-lisays-epaonnistui-response.xml"))
        paivita-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/virhe-tietueen-lisays-epaonnistui-response.xml"))
        poista-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/virhe-tietueen-lisays-epaonnistui-response.xml"))
        ulkoinen-id (tyokalut/hae-vapaa-toteuma-ulkoinen-id)
        payload (-> "test/resurssit/api/varustetoteuma.json"
                    slurp
                    (.replace "__ID__" (str ulkoinen-id)))
        varustetoteuma-api-url ["/api/urakat/" urakka "/toteumat/varuste/"]]
    (with-fake-http
      [(str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-xml
       (str +testi-tierekisteri-url+ "/lisaatietue") lisaa-tietue-xml
       (str +testi-tierekisteri-url+ "/paivitatietue") paivita-tietue-xml
       (str +testi-tierekisteri-url+ "/poistatietue") poista-tietue-xml
       #"http?://localhost" :allow]
      (let [varustetoteumat-ennen-pyyntoa (ffirst (q
                                                    (str "SELECT count(*)
                                                                FROM varustetoteuma")))
            vastaus-lisays (api-tyokalut/post-kutsu varustetoteuma-api-url kayttaja portti
                                                    payload)]

        ;; Oletus on, että toimenpiteet kirjataan Harjaan, mutta niitä ei merkitä lähetetyiksi
        (is (= 500 (:status vastaus-lisays)))
        (let [varustetoteumat-pyynnon-jalkeen (ffirst (q
                                                        (str "SELECT count(*)
                                                       FROM varustetoteuma")))
              toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
              lahetystiedot-kannassa (flatten (q (str "SELECT lahetetty_tierekisteriin FROM varustetoteuma WHERE toteuma = " toteuma-id ";")))]
          (is (= (+ varustetoteumat-ennen-pyyntoa 4) varustetoteumat-pyynnon-jalkeen))
          (is (every? #(= false %) lahetystiedot-kannassa)))))

    ;; Tierekisteri toimii nyt, lähetetään viesti uudelleen, jolloin aiemmin sisään otetut
    ;; toteumat merkitään lähetetyiksi

    (let [hae-tietolaji-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml"))
          lisaa-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
          paivita-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))
          poista-tietue-xml (slurp (io/resource "xsd/tierekisteri/esimerkit/ok-vastaus-response.xml"))]
      (with-fake-http
        [(str +testi-tierekisteri-url+ "/haetietolaji") hae-tietolaji-xml
         (str +testi-tierekisteri-url+ "/lisaatietue") lisaa-tietue-xml
         (str +testi-tierekisteri-url+ "/paivitatietue") paivita-tietue-xml
         (str +testi-tierekisteri-url+ "/poistatietue") poista-tietue-xml
         #"http?://localhost" :allow]
        (let [varustetoteumat-ennen-pyyntoa (ffirst (q
                                                      (str "SELECT count(*)
                                                                FROM varustetoteuma")))
              vastaus-lisays (api-tyokalut/post-kutsu varustetoteuma-api-url kayttaja portti
                                                      payload)]

          (is (= 200 (:status vastaus-lisays)))
          (let [varustetoteumat-pyynnon-jalkeen (ffirst (q
                                                          (str "SELECT count(*)
                                                       FROM varustetoteuma")))
                toteuma-id (ffirst (q (str "SELECT id FROM toteuma WHERE ulkoinen_id = " ulkoinen-id)))
                lahetystiedot-kannassa (flatten (q (str "SELECT lahetetty_tierekisteriin FROM varustetoteuma WHERE toteuma = " toteuma-id ";")))]
            (is (= varustetoteumat-ennen-pyyntoa varustetoteumat-pyynnon-jalkeen))
            (is (every? #(= true %) lahetystiedot-kannassa))))))))
