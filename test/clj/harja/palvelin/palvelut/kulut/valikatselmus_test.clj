(ns harja.palvelin.palvelut.kulut.valikatselmus-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.urakka :as urakka]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.kulut.valikatselmukset :as valikatselmukset]
            [harja.pvm :as pvm]
            [harja.testi :refer :all])
  (:import (clojure.lang ExceptionInfo)
           (harja.domain.roolit EiOikeutta)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :db-replica (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :valikatselmus (component/using
                                         (valikatselmukset/->Valikatselmukset)
                                         [:http-palvelin :db :db-replica])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn filtteroi-oikaisut-selitteella [oikaisut selite]
  (filter #(= selite (::valikatselmus/selite %))
          oikaisut))

;;Oikaisut
(deftest tavoitehinnan-oikaisu-onnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        ;; With-redefsillä laitetaan (pvm/nyt) palauttamaan tietty ajankohta. Tämä sen takia, että
        ;; rajapinta antaa virheen, mikäli kutsuhetkellä ei saa tehdä tavoitehinnan oikaisuja.
        ;; Tätä tulee käyttää varoen, koska tämä ylirjoittaa kaikki (pvm/nyt) kutsut blokin sisällä, joita saattaa
        ;; tapahtua pinnan alla.
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  +kayttaja-jvh+
                                  {::urakka/id urakka-id
                                   ::valikatselmus/otsikko "Oikaisu"
                                   ::valikatselmus/summa 9001
                                   ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))]
    (is (some? vastaus))
    (is (= (::valikatselmus/summa vastaus) 9001M))))

(deftest oikaisun-teko-epaonnistuu-alkuvuodesta
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        virheellinen-vastaus (try
                               (with-redefs [pvm/nyt #(pvm/luo-pvm 2020 5 20)]
                                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :tallenna-tavoitehinnan-oikaisu
                                                 +kayttaja-jvh+
                                                 {::urakka/id urakka-id
                                                  ::valikatselmus/otsikko "Oikaisu"
                                                  ::valikatselmus/summa 1000
                                                  ::valikatselmus/selite "Juhannusmenot hidasti"}))
                               (catch Exception e e))]
    (is (= ExceptionInfo (type virheellinen-vastaus)))
    (is (= "Tavoitehinnan oikaisuja saa tehdä, muokata tai poistaa ainoastaan aikavälillä 1.9. - 31.12." (-> virheellinen-vastaus ex-data :virheet :viesti)))))

(deftest virheellisen-oikaisun-teko-epaonnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id]
    (is (thrown? Exception (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                             (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-tavoitehinnan-oikaisu
                                             +kayttaja-jvh+
                                             {::urakka/id urakka-id
                                              ::valikatselmus/otsikko "Oikaisu"
                                              ::valikatselmus/summa "Kolmesataa"
                                              ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))))))

(deftest muokkaa-tavoitehinnan-oikaisua
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        oikaisut (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-tavoitehintojen-oikaisut
                                 +kayttaja-jvh+
                                 {::urakka/id urakka-id})
        muokattava-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut "Muokattava testioikaisu"))
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  +kayttaja-jvh+
                                  (assoc muokattava-oikaisu ::valikatselmus/summa 50000)))]
    (is (= 1 vastaus) "Summan muokkaus ei onnistunut")

    (let [oikaisut-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-tavoitehintojen-oikaisut
                                           +kayttaja-jvh+
                                           {::urakka/id urakka-id})
          muokattu-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut-jalkeen "Muokattava testioikaisu"))]
      (is (= 50000M (::valikatselmus/summa muokattu-oikaisu))))))

(deftest tavoitehinnan-oikaisun-muokkaus-ei-onnistu-tammikuussa
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        oikaisut (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-tavoitehintojen-oikaisut
                                 +kayttaja-jvh+
                                 {::urakka/id urakka-id})
        muokattava-oikaisu (first (filtteroi-oikaisut-selitteella oikaisut "Muokattava testioikaisu"))
        vastaus (try (with-redefs [pvm/nyt #(pvm/luo-pvm 2021 0 15)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-tavoitehinnan-oikaisu
                                       +kayttaja-jvh+
                                       (assoc muokattava-oikaisu ::valikatselmus/summa 1)))
                     (catch Exception e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= "Tavoitehinnan oikaisuja saa tehdä, muokata tai poistaa ainoastaan aikavälillä 1.9. - 31.12." (-> vastaus ex-data :virheet :viesti)))))

(deftest tavoitehinnan-oikaisun-poisto-onnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        poistettava (first (filter #(= "Poistettava testioikaisu" (::valikatselmus/selite %))
                                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :hae-tavoitehintojen-oikaisut
                                                   +kayttaja-jvh+
                                                   {::urakka/id urakka-id})))
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :poista-tavoitehinnan-oikaisu
                                  +kayttaja-jvh+
                                  {::valikatselmus/oikaisun-id (::valikatselmus/oikaisun-id poistettava)}))]
    (is (= 1 vastaus))
    (is (empty? (filter #(= "Poistettava testioikaisu" (::valikatselmus/selite %))
                        (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-tavoitehintojen-oikaisut
                                        +kayttaja-jvh+
                                        {::urakka/id urakka-id}))))))

(deftest tavoitehinnan-oikaisu-epaonnistuu-alueurakalle
  (let [urakka-id @kemin-alueurakan-2019-2023-id
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-tavoitehinnan-oikaisu
                                       +kayttaja-jvh+
                                       {::urakka/id urakka-id
                                        ::valikatselmus/otsikko "Oikaisu"
                                        ::valikatselmus/summa 9001
                                        ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))
                     (catch Exception e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= "Tavoitehinnan oikaisuja saa tehdä ainoastaan teiden hoitourakoille" (-> vastaus ex-data :virheet :viesti)))))

(deftest tavoitehinnan-oikaisu-onnistuu-urakanvalvojalla
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  (assoc +kayttaja-tero+
                                    :urakkaroolit {urakka-id #{"ELY_Urakanvalvoja"}})
                                  {::urakka/id urakka-id
                                   ::valikatselmus/otsikko "Oikaisu"
                                   ::valikatselmus/summa 12345
                                   ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))]
    (is (= 12345M (::valikatselmus/summa vastaus)))))

(deftest tavoitehinnan-oikaisu-epaonnistuu-sepolla
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        vastaus (try (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                       (kutsu-palvelua (:http-palvelin jarjestelma)
                                       :tallenna-tavoitehinnan-oikaisu
                                       +kayttaja-seppo+
                                       {::urakka/id urakka-id
                                        ::valikatselmus/otsikko "Oikaisu"
                                        ::valikatselmus/summa 12345
                                        ::valikatselmus/selite "Maailmanloppu tuli, kesti vähän oletettua kauempaa"}))
                     (catch ExceptionInfo e e))]
    (is (= ExceptionInfo (type vastaus)))
    (is (= EiOikeutta (type (ex-data vastaus))))))

(deftest tavoitehinnan-miinusmerkkinen-oikaisu-onnistuu
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        vastaus (with-redefs [pvm/nyt #(pvm/hoitokauden-loppupvm 2020)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-tavoitehinnan-oikaisu
                                  +kayttaja-jvh+
                                  {::urakka/id urakka-id
                                   ::valikatselmus/otsikko "Oikaisu"
                                   ::valikatselmus/summa -2000
                                   ::valikatselmus/selite "Seppo kävi töissä, päällystykset valmistui odotettua nopeampaa"}))]
    (is (= -2000M (::valikatselmus/summa vastaus)))))

;; Päätökset
(deftest tee-paatos-tavoitehinnan-ylityksesta
  (let [urakka-id @oulun-maanteiden-hoitourakan-2019-2024-id
        vastaus (with-redefs [pvm/nyt (#(pvm/hoitokauden-loppupvm 2020))]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-urakan-paatos
                                  +kayttaja-jvh+
                                  {::urakka/id urakka-id
                                   ::valikatselmus/tyyppi ::valikatselmus/tavoitehinnan-ylitys
                                   ::valikatselmus/hinnan-erotus 10000
                                   ::valikatselmus/urakoitsijan-maksu 7000
                                   ::valikatselmus/urakoitsijan-maksu 3000}))]
    (is (= 7000 (::valikatselmus/urakoitsijan-maksu vastaus)))))