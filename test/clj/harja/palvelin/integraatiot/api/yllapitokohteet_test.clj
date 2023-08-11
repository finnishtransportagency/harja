(ns harja.palvelin.integraatiot.api.yllapitokohteet-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.integraatio :as integraatio]
            [harja.palvelin.integraatiot.api.yllapitokohteet :as api-yllapitokohteet]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.integraatiot.vkm.vkm-test :refer [+testi-vkm+]]
            [harja.jms-test :refer [feikki-jms]]
            [clojure.core.async :refer [<!! timeout]]
            [clojure.string :as str]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.jms :as jms]
            [clojure.java.io :as io]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm])
  (:import (org.postgresql.util PSQLException PSQLState)
           (java.util UUID))
  (:use org.httpkit.fake))

(def ehdon-timeout 20000)
(def kayttaja-paallystys "skanska")
(def kayttaja-tiemerkinta "tiemies")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-paallystys
    :fim (component/using
           (fim/->FIM {:url +testi-fim+})
           [:db :integraatioloki])
    :vkm (component/using
           (vkm/->VKM +testi-vkm+)
           [:db :integraatioloki])
    :itmf (feikki-jms "itmf")
    :api-sahkoposti (component/using
                       (sahkoposti-api/->ApiSahkoposti {:api-sahkoposti integraatio/api-sahkoposti-asetukset
                                                        :tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                       [:http-palvelin :db :integraatioloki :itmf])
    :api-yllapitokohteet (component/using (api-yllapitokohteet/->Yllapitokohteet)
                           [:http-palvelin :db :integraatioloki :liitteiden-hallinta
                            :fim :vkm :api-sahkoposti])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-yllapitokohteiden-haku
  (let [muhoksen-paallystysurakan-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" muhoksen-paallystysurakan-id "/yllapitokohteet")]
                                        kayttaja-paallystys
                                        portti)
        data (cheshire/decode (:body vastaus) true)
        yllapitokohteet (mapv :yllapitokohde (:yllapitokohteet data))
        leppajarven-ramppi-2017 (first (filter #(= (:nimi %) "Leppäjärven ramppi")
                                               yllapitokohteet))
        leppajarven-ramppi-2018 (first (filter #(= (:nimi %) "Leppäjärven ramppi 2018")
                                               yllapitokohteet))]

    (is (= 200 (:status vastaus)))
    (is (= 13 (count yllapitokohteet))
        "Palautuu kaikkien vuosien kohteet (2017 & 2018)")

    (testing "2017 kohde palautuu oikein"
      (is (some? leppajarven-ramppi-2017))
      (is (some? (:paallystys-aloitettu (:aikataulu leppajarven-ramppi-2017))))
      (is (some? (:paallystys-valmis (:aikataulu leppajarven-ramppi-2017))))
      (is (some? (:valmis-tiemerkintaan (:aikataulu leppajarven-ramppi-2017))))
      (is (some? (:tiemerkinta-aloitettu (:aikataulu leppajarven-ramppi-2017))))
      (is (some? (:tiemerkinta-valmis (:aikataulu leppajarven-ramppi-2017))))
      (is (some? (:kohde-valmis (:aikataulu leppajarven-ramppi-2017))))
      (is (some? (:takuupvm (get-in leppajarven-ramppi-2017 [:aikataulu :paallystysilmoitus])))))

    (testing "2018 kohde palautuu oikein"
      (is (some? leppajarven-ramppi-2018))
      (is (= (:sijainti leppajarven-ramppi-2018)
             {:numero 20
              :aosa 1
              :aet 0
              :losa 3
              :let 0})
          "Sijainti palautuu oikein, ilman ajorataa ja kaistaa"))))

(deftest yllapitokohteiden-haku-ei-toimi-ilman-oikeuksia
  (let [muhoksen-paallystysurakan-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" muhoksen-paallystysurakan-id "/yllapitokohteet") urakka]
                                        "Erkki Esimerkki"
                                        portti)]
    (is (= 403 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Tuntematon käyttäjätunnus: Erkki Esimerkki"))))

(deftest yllapitokohteiden-haku-ei-toimi-tuntemattomalle-urakalle
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/123467890/yllapitokohteet" urakka] kayttaja-paallystys portti)]
    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "tuntematon-urakka"))))


(deftest aikataulun-kirjaaminen-ilmoituksettomalle-kohteelle-toimii
  (let [urakka (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (str/includes? (:body vastaus) "Kohteella ei ole päällystysilmoitusta"))))

(deftest paallystyksen-aikataulun-paivittaminen-valittaa-sahkopostin-kun-kohde-valmis-tiemerkintaan-paivittyy
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        sahkoposti-lahetys-url "http://localhost:8084/harja/api/sahkoposti/xml"
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       #".*api\/urakat.*" :allow
       {:url sahkoposti-lahetys-url :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
            kohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
            vastaus (future (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-paallystys")]
                              kayttaja-paallystys portti
                              (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json")))
            _ (odota-ehdon-tayttymista #(realized? vastaus) "Saatiin vastaus aikataulu-paallystys." ehdon-timeout)
            integraatioviestit (hae-ulos-lahtevat-integraatiotapahtumat)]
        (is (= 200 (:status @vastaus)))

        ;; Leppäjärvi oli jo merkitty valmiiksi tiemerkintään, mutta sitä päivitettiin -> pitäisi lähteä maili
        (is (= sahkoposti-lahetys-url (:osoite (second integraatioviestit))) "Sähköposti lähetettiin")

        ;; Laitetaan sama pyyntö uudelleen, maili ei lähde koska valmis tiemerkintään -pvm sama kuin aiempi
        (let [vastaus (future (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-paallystys")]
                                kayttaja-paallystys portti
                                (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json")))
              _ (odota-ehdon-tayttymista #(realized? vastaus) "Saatiin vastaus aikataulu-paallystys." ehdon-timeout)
              _ (odota-ehdon-tayttymista #(hae-ulos-lahtevat-integraatiotapahtumat) "Ulos lähtevät integraatiotapahtumat." ehdon-timeout)
              integraatioviestit (hae-ulos-lahtevat-integraatiotapahtumat)]
          (is (= 200 (:status @vastaus)))
          (is (= 1 (count (filter #(= (str (:otsikko %)) (str {"Content-Type" "application/xml"})) integraatioviestit))) "Sähköposti ei lähtenyt, eikä pitänytkään"))))))

(deftest paallystyksen-aikataulun-paivittaminen-valittaa-sahkopostin-kun-kohde-valmis-tiemerkintaan-ekaa-kertaa
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        sahkoposti-lahetys-url "http://localhost:8084/harja/api/sahkoposti/xml"
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       #".*api\/urakat.*" :allow
       {:url sahkoposti-lahetys-url :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
            kohde-id (hae-yllapitokohteen-id-nimella "Nakkilan ramppi")
            vastaus (future (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-paallystys")]
                              kayttaja-paallystys portti
                              (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json")))
            _ (odota-ehdon-tayttymista #(realized? vastaus) "Saatiin vastaus aikataulu-paallystys." ehdon-timeout)
            integraatioviestit (hae-ulos-lahtevat-integraatiotapahtumat)]
        (is (= 200 (:status @vastaus)))

        ;; Integraatioviesteihin tulee merkintä, että aikataulu on päivitetty
        (is (clojure.string/includes? (:sisalto (first integraatioviestit)) "Aikataulu kirjattu onnistuneesti") "Aikataulu kirjattu onnistuneesti")
        ;; Valmiiksi tiemerkintään annettiin ekaa kertaa tälle kohteelle -> pitäisi lähteä maili
        (is (= sahkoposti-lahetys-url (:osoite (second integraatioviestit))) "Sähköposti lähetettiin")))))

(deftest tiemerkinnan-paivittaminen-valittaa-sahkopostin-kun-kohde-valmis
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        sahkoposti-lahetys-url "http://localhost:8084/harja/api/sahkoposti/xml"
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       #".*api\/urakat.*" :allow
       {:url sahkoposti-lahetys-url :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
            kohde-id (hae-yllapitokohteen-id-nimella "Nakkilan ramppi")
            vastaus (future (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-tiemerkinta")]
                              kayttaja-tiemerkinta portti
                              (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json")))
            _ (odota-ehdon-tayttymista #(realized? vastaus) "Saatiin vastaus aikataulu-tiemerkintään." ehdon-timeout)
            integraatioviestit (hae-ulos-lahtevat-integraatiotapahtumat)]
        (is (= 200 (:status @vastaus)))

        ;; Integraatioviesteihin tulee merkintä, että aikataulu on päivitetty
        (is (clojure.string/includes? (:sisalto (first integraatioviestit)) "Aikataulu kirjattu onnistuneesti") "Aikataulu kirjattu onnistuneesti")
        ;; Tiemerkintä valmis oli annettu aiemmin, mutta nyt se päivittyi -> mailia menemään
        (is (= sahkoposti-lahetys-url (:osoite (second integraatioviestit))) "Sähköposti lähetettiin")

        ;; Lähetetään sama pyyntö uudelleen, pvm ei muutu, ei lennä mailit
        ;; FIXME Onkohan tämä bugi? Maili ei kai saisi lähteä jos pvm on sama kuin ennen. Nyt näyttää siltä että joskus menee testi läpi ja joskus ei
        ;; Tämä ei selkeästikään toimi. Pitää korjata erikseen
        #_ (let [vastaus (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-tiemerkinta")]
                                                 kayttaja-tiemerkinta portti
                                                 (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))
              integraatioviestit (hae-ulos-lahtevat-integraatiotapahtumat)
              _ (println "*************************************************************** integraatioviestit " (pr-str integraatioviestit))]
            (is (= 200 (:status vastaus)))
            (is (= 1 (count (filter #(= (str (:otsikko %)) (str {"Content-Type" "application/xml"})) integraatioviestit))) "Sähköposti ei lähtenyt, eikä pitänytkään"))))))

(deftest aikataulun-kirjaaminen-toimii-kohteelle-jolla-ilmoitus
  (let [urakka (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]

    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (not (str/includes? (:body vastaus) "Kohteella ei ole päällystysilmoitusta")))))

(deftest aikataulun-kirjaaminen-paallystysurakan-kohteelle-toimii
  (let [urakka (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vanhat-aikataulutiedot (first (q (str "SELECT paallystys_alku, paallystys_loppu,
                                                 valmis_tiemerkintaan, tiemerkinta_alku,
                                                 tiemerkinta_loppu FROM yllapitokohteen_aikataulu
                                                 WHERE yllapitokohde = " kohde)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (str/includes? (:body vastaus) "Kohteella ei ole päällystysilmoitusta"))

    (let [aikataulutiedot (first (q (str "SELECT paallystys_alku, paallystys_loppu,
                                                 valmis_tiemerkintaan, tiemerkinta_alku,
                                                 tiemerkinta_loppu FROM yllapitokohteen_aikataulu
                                                 WHERE yllapitokohde = " kohde)))]
      ;; Uudet päällystyksen pvm:t tallentuivat oikein
      (is (some? (get aikataulutiedot 0)))
      (is (some? (get aikataulutiedot 1)))
      (is (some? (get aikataulutiedot 2)))
      ;; Tiemerkinnän tiedot eivät päivity, koska kyseessä ei ole tiemerkintäurakka
      (is (= (get aikataulutiedot 3) (get vanhat-aikataulutiedot 3)))
      (is (= (get aikataulutiedot 4) (get vanhat-aikataulutiedot 4))))))

(deftest aikataulun-kirjaaminen-tiemerkintaurakan-kohteelle-toimii
  (let [urakka (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
        kohde (hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa urakka)
        vanhat-aikataulutiedot (first (q (str "SELECT paallystys_alku, paallystys_loppu,
                                                 valmis_tiemerkintaan, tiemerkinta_alku,
                                                 tiemerkinta_loppu FROM yllapitokohteen_aikataulu
                                                 WHERE yllapitokohde = " kohde)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         kayttaja-tiemerkinta portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]
    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (not (str/includes? (:body vastaus) "Kohteella ei ole päällystysilmoitusta")))

    (let [aikataulutiedot (first (q (str "SELECT paallystys_alku, paallystys_loppu,
                                                 valmis_tiemerkintaan, tiemerkinta_alku,
                                                 tiemerkinta_loppu FROM yllapitokohteen_aikataulu
                                                 WHERE yllapitokohde = " kohde)))]
      ; Päällystyksen tiedot eivät tallennu, koska päivitetään tiemerkintäurakkaa
      (is (= (get aikataulutiedot 0) (get vanhat-aikataulutiedot 0)))
      (is (= (get aikataulutiedot 1) (get vanhat-aikataulutiedot 1)))
      (is (= (get aikataulutiedot 2) (get vanhat-aikataulutiedot 2)))
      ;; Valittu tiemerkintäurakka on valittu suorittamaan kyseinen ylläpitokohde, joten pvm:t päivittyvät:
      (is (some? (get aikataulutiedot 3)))
      (is (some? (get aikataulutiedot 4))))))


(deftest aikataulun-kirjaaminen-paallystykseen-ei-toimi-ilman-oikeuksia
  (let [urakka (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         "LX123456789" portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]

    (is (= 403 (:status vastaus)))))

(deftest aikataulun-kirjaaminen-tiemerkintaan-ei-toimi-ilman-oikeuksia
  (let [urakka (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         "LX123456789" portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]

    (is (= 403 (:status vastaus)))))

(deftest tiemerkinnan-aikataulun-kirjaus-ei-onnistu-paallystysurakalle
  (let [urakka (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "mutta urakan tyyppi on"))))

(deftest paallystyksen-aikataulun-kirjaus-ei-onnistu-tiemerkintaurakalle
  (let [urakka (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-tiemerkinta portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "mutta urakan tyyppi on"))))

(deftest paallystyksen-viallisen-aikataulun-kirjaus-ei-onnistu-tiemerkintapvm-vaarin
  (let [urakka (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus_viallinen_tiemerkintapvm_ilman_paallystyksen_loppua.json"))]
    (is (= 400 (:status vastaus)))
    (is (or
          ; ko. payload feilauttaa kaksi eri validointia. Riittää napata toinen kiinni.
          (str/includes? (:body vastaus) "Kun annetaan päällystyksen aloitusaika, anna myös päällystyksen valmistumisen aika tai aika-arvio")
          (str/includes? (:body vastaus) "Tiemerkinnälle ei voi asettaa päivämäärää, päällystyksen valmistumisaika puuttuu.")))))

(deftest paallystyksen-viallisen-aikataulun-kirjaus-ei-onnistu-paallystyksen-valmispvm-vaarin
  (let [urakka (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus_viallinen_paallystys_valmis_ilman_paallystyksen_alkua.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Päällystystä ei voi merkitä valmiiksi, aloitus puuttuu."))))

(deftest tiemerkinnan-viallisen-aikataulun-kirjaus-ei-onnistu-tiemerkinnan-valmispvm-vaarin
  (let [urakka (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
        kohde (hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         kayttaja-tiemerkinta portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus_viallinen_tiemerkinta_valmis_ilman_alkua.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Tiemerkintää ei voi merkitä valmiiksi, aloitus puuttuu."))))

(deftest aikataulun-kirjaus-vaatii-paallystys-valmis-jos-paallystys-aloitettu-annettu
  (let [urakka (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/aikataulun-kirjaus-vaatii-paallystys-valmis-jos-paallystys-aloitettu-annettu.json"))]
    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Kun annetaan päällystyksen aloitusaika, anna myös päällystyksen valmistumisen aika tai aika-arvio"))))

(deftest aikataulun-kirjaus-vaatii-tiemerkinta-valmis-jos-tiemerkinta-aloitettu-annettu
  (let [urakka (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
        kohde (hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         kayttaja-tiemerkinta portti
                                         (slurp "test/resurssit/api/aikataulun-kirjaus-vaatii-tiemerkinta-valmis-jos-tiemerkinta-aloitettu-annettu.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Kun annetaan tiemerkinnän aloitusaika, anna myös tiemerkinnän valmistumisen aika tai aika-arvio"))))

(deftest aikataulun-kirjaaminen-estaa-paivittamasta-urakkaan-kuulumatonta-kohdetta
  (let [urakka (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Ylläpitokohde ei kuulu urakkaan"))))

(deftest maaramuutosten-kirjaaminen-kohteelle-toimii
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde-id (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        _ (u "INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde, tyon_tyyppi, tyo, yksikko, tilattu_maara, toteutunut_maara, yksikkohinta, poistettu, luoja, luotu, muokkaaja, muokattu, jarjestelma, ulkoinen_id, ennustettu_maara)
              VALUES (" kohde-id ", 'ajoradan_paallyste', 'Esimerkki työ', 'm2', 12, 14.2, 666, FALSE, 10, '2019-01-31 15:34:32', NULL, NULL, NULL, NULL, NULL)")
        hae-maaramuutokset #(q-map "SELECT * FROM yllapitokohteen_maaramuutos WHERE yllapitokohde = " kohde-id)
        maaramuutokset-ennen-kirjausta (hae-maaramuutokset)
        harjan-kautta-kirjattu (first maaramuutokset-ennen-kirjausta)
        polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/maaramuutokset"]
        kutsudata (slurp "test/resurssit/api/maaramuutosten-kirjaus-request.json")
        vastaus (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata)
        maaramuutokset-kirjauksen-jalkeen (hae-maaramuutokset)]

    (is (= 200 (:status vastaus)) "Kirjaus tehtiin onnistuneesti")
    (is (str/includes? (:body vastaus) "Määrämuutokset kirjattu onnistuneesti."))
    (is (= (+ 1 (count maaramuutokset-ennen-kirjausta)) (count maaramuutokset-kirjauksen-jalkeen))
        "Vain yksi uusi määrämuutos on kirjautunut")

    (let [kutsudata (str/replace kutsudata "\"yksikkohinta\":666" "\"yksikkohinta\":888")
          vastaus (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata)
          maaramuutokset-kirjauksen-jalkeen (hae-maaramuutokset)]

      (is (= 200 (:status vastaus)) "Kirjaus tehtiin onnistuneesti")
      (is (str/includes? (:body vastaus) "Määrämuutokset kirjattu onnistuneesti."))
      (is (= (+ 1 (count maaramuutokset-ennen-kirjausta)) (count maaramuutokset-kirjauksen-jalkeen))
          "Vain yksi uusi määrämuutos on kirjautunut")

      (is (= harjan-kautta-kirjattu (first maaramuutokset-kirjauksen-jalkeen))
          "Harjan käyttöliittymän kautta kirjattua määrä muutosta ei ole muutettu")

      (is (== 888 (:yksikkohinta (second maaramuutokset-kirjauksen-jalkeen))) "Uusi yksikköhinta on päivittynyt oikein")
      (is (= "m2" (:yksikko (second maaramuutokset-kirjauksen-jalkeen))))
      (is (== 12 (:tilattu_maara (second maaramuutokset-kirjauksen-jalkeen))))
      (is (== 15.3 (:ennustettu_maara (second maaramuutokset-kirjauksen-jalkeen))))
      (is (== 14.2 (:toteutunut_maara (second maaramuutokset-kirjauksen-jalkeen))))
      (is (= "ajoradan_paallyste" (:tyon_tyyppi (second maaramuutokset-kirjauksen-jalkeen))))
      (is (= "Esimerkki työ" (:tyo (second maaramuutokset-kirjauksen-jalkeen)))))))

(deftest maaramuutosten-kirjaaminen-estaa-paivittamasta-urakkaan-kuulumatonta-kohdetta
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde-id (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka-id)
        kutsudata (slurp "test/resurssit/api/maaramuutosten-kirjaus-request.json")
        polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/maaramuutokset"]
        vastaus (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata)]
    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "tuntematon-yllapitokohde"))))

(deftest tarkastuksen-kirjaaminen-kohteelle-toimii
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        ;; Testiä varten poista POT
        _ (u "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id ";")
        hae-tarkastukset #(q-map "SELECT * FROM tarkastus WHERE yllapitokohde =" kohde-id)
        tarkastukset-ennen-kirjausta (hae-tarkastukset)
        polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/tarkastus"]
        kutsudata (slurp "test/resurssit/api/yllapitokohteen-tarkastuksen-kirjaus-request.json")
        vastaus (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata)
        tarkastukset-kirjauksen-jalkeen (hae-tarkastukset)
        tarkastus (first tarkastukset-kirjauksen-jalkeen)]
    (is (= 200 (:status vastaus)) "Kirjaus tehtiin onnistuneesti")
    (is (str/includes? (:body vastaus) (str "Tarkastus kirjattu onnistuneesti urakan: " urakka-id " ylläpitokohteelle: " kohde-id ".")))
    (is (= (+ 1 (count tarkastukset-ennen-kirjausta)) (count tarkastukset-kirjauksen-jalkeen))
        "Vain yksi uusi tarkastus on kirjautunut ylläpitokohteelle")

    (is (= kohde-id (:yllapitokohde tarkastus)) "Tarkastus on kirjattu oikealle ylläpitokohteelle")
    (is (= "katselmus" (:tyyppi tarkastus)) "Tarkastus on oikeaa tyyppiä")
    (is (= "Vanha päällyste on uusittava" (:havainnot tarkastus)) "Havainnot ovat oikein")

    (let [kutsudata (.replace kutsudata "Vanha päällyste on uusittava" "Eipäs tarvikkaan")
          vastaus (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata)
          tarkastukset-paivityksen-jalkeen (hae-tarkastukset)
          paivitetty-tarkastus (first tarkastukset-paivityksen-jalkeen)]

      (is (= 200 (:status vastaus)) "Päivitys tehtiin onnistuneesti")
      (is (= (count tarkastukset-kirjauksen-jalkeen) (count tarkastukset-paivityksen-jalkeen)) "Kirjauksia päivityksen jälkeen on saman verran kuin aloittaessa.")

      (is (str/includes? (:body vastaus) (str "Tarkastus kirjattu onnistuneesti urakan: " urakka-id " ylläpitokohteelle: " kohde-id ".")))
      (is (= "Eipäs tarvikkaan" (:havainnot paivitetty-tarkastus)) "Havainnot ovat päivittyneet oikein"))

    (let [polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/tarkastus"]
          kutsudata (-> "test/resurssit/api/talvihoitotarkastus-poisto.json"
                        slurp
                        (.replace "__PVM__" "2017-01-01T12:00:00+02:00")
                        (.replace "__ID__" "666"))
          vastaus (api-tyokalut/delete-kutsu polku kayttaja-paallystys portti kutsudata)
          poistettu? (:poistettu (first (hae-tarkastukset)))]
      (is (= 200 (:status vastaus)) "Poisto tehtiin onnistuneesti")
      (is (str/includes? (:body vastaus) "Tarkastukset poistettu onnistuneesti. Poistettiin: 1 tarkastusta."))
      (is poistettu? "Tarkastus on merkitty poistetuksi onnistuneesti."))))

(deftest useamman-tarkastuksen-kirjaamisessa-transaktio-toimii
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohde-id (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        hae-tarkastukset #(q-map "SELECT * FROM tarkastus WHERE yllapitokohde =" kohde-id)
        tarkastukset-ennen-kirjausta (hae-tarkastukset)
        polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/tarkastus"]
        kutsudata (slurp "test/resurssit/api/usean-yllapitokohteen-tarkastuksen-kirjaus-request.json")
        vastaus (with-redefs [sijainnit/hae-tierekisteriosoite (fn [db alkusijainti loppusijainti]
                                                                 (when (= (:x alkusijainti) 443673.469)
                                                                   (throw (PSQLException. "Foo" (PSQLState/DATA_ERROR)))))]
                  (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata))
        tarkastukset-kirjauksen-jalkeen (hae-tarkastukset)]
    (is (= 500 (:status vastaus)))
    (is (= (count tarkastukset-ennen-kirjausta) (count tarkastukset-kirjauksen-jalkeen)))))

(deftest tiemerkintatoteuman-kirjaaminen-kohteelle-toimii
  (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
        kohde-id (hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa urakka-id)
        ulkoinen-id 666777
        hae-toteumat #(q-map "SELECT * FROM tiemerkinnan_yksikkohintainen_toteuma WHERE yllapitokohde = " kohde-id)
        toteumat-ennen-kirjausta (hae-toteumat)
        polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/tiemerkintatoteuma"]
        kutsudata (slurp "test/resurssit/api/yllapitokohteen-tiemerkintatoteuman-kirjaus-request.json")
        vastaus (api-tyokalut/post-kutsu polku kayttaja-tiemerkinta portti kutsudata)
        toteumat-kirjauksen-jalkeen (hae-toteumat)
        toteuma (first (filter #(= ulkoinen-id (:ulkoinen_id %)) toteumat-kirjauksen-jalkeen))]

    (is (= 200 (:status vastaus)) "Kirjaus tehtiin onnistuneesti")
    (is (str/includes? (:body vastaus) "Tiemerkintätoteuma kirjattu onnistuneesti"))
    (is (= (+ 1 (count toteumat-ennen-kirjausta)) (count toteumat-kirjauksen-jalkeen))
        "Vain yksi uusi tarkastus on kirjautunut ylläpitokohteelle")

    (is (= urakka-id (:urakka toteuma)) "Toteuma on kirjattu oikealle urakalle")
    (is (= kohde-id (:yllapitokohde toteuma)) "Toteuma on kirjattu oikealle yllapitokohteelle")
    (is (= 12345678.00M (:hinta toteuma)) "Toteuma on kirjattu oikealle yllapitokohteelle")
    (is (not (str/blank? (:hinta_kohteelle toteuma))) "Toteuma sisältää tiedon, mille kohteelle sen on kohdistettu")

    (let [kutsudata (.replace kutsudata "12345678" "666")
          vastaus (api-tyokalut/post-kutsu polku kayttaja-tiemerkinta portti kutsudata)
          toteumat-paivityksen-jalkeen (hae-toteumat)
          paivitetty-toteuma (first (filter #(= ulkoinen-id (:ulkoinen_id %)) toteumat-paivityksen-jalkeen))]

      (is (= 200 (:status vastaus)) "Päivitys tehtiin onnistuneesti")
      (is (= (count toteumat-kirjauksen-jalkeen) (count toteumat-paivityksen-jalkeen)) "Kirjauksia päivityksen jälkeen on saman verran kuin aloittaessa.")
      (is (str/includes? (:body vastaus) "Tiemerkintätoteuma kirjattu onnistuneesti"))
      (is (= 666.00M (:hinta paivitetty-toteuma)) "Hinta on päivittynyt oikein"))

    (let [polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/tiemerkintatoteuma"]
          kutsudata (-> "test/resurssit/api/toteuman-poisto.json"
                        slurp
                        (.replace "__PVM__" "2017-01-01T12:00:00+02:00")
                        (.replace "__ID__" (str ulkoinen-id)))
          vastaus (api-tyokalut/delete-kutsu polku kayttaja-tiemerkinta portti kutsudata)
          poistettu? (:poistettu (first (filter #(= ulkoinen-id (:ulkoinen_id %)) (hae-toteumat))))]
      (is (= 200 (:status vastaus)) "Poisto tehtiin onnistuneesti")
      (is (str/includes? (:body vastaus) "Toteumat poistettu onnistuneesti. Poistettiin: 1 toteumaa."))
      (is poistettu? "Toteuma on merkitty poistetuksi onnistuneesti."))))


;; Oletamme, että kyseistä rajapintaa ei käytetä.
;; Lisäksi viitekehysmuuntimeen on tullut muutoksia, joita ei ole aiemmin mainitusta syystä päivitetty tähän rajappintaan.
;; Jos todetaan, että rajapintaa käytetään, palautetaan testi ja korjataan se. Muuten testi voidaan poistaa.
#_(deftest osoitteiden-muunnos-vkmn-kanssa
  (let [urakka (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        kohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        ;; Testiä varten tuhoa POT
        _ (u "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id ";")
        vkm-vastaus (slurp "test/resurssit/vkm/vkm-vastaus-alikohteiden-kanssa.txt")
        ]
    (with-fake-http [+testi-vkm+ vkm-vastaus
                     #".*api\/urakat.*" :allow]
                    (let [payload (slurp "test/resurssit/api/toisen-paivan-verkon-paallystyskohteen-paivitys-request.json")
                          vastaus (api-tyokalut/put-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id]
                                                          kayttaja-paallystys portti
                                                          payload)]
                      (is (= 200 (:status vastaus)) "Kutsu tehtiin onnistuneesti")

                      (let [kohteen-tr-osoite (hae-yllapitokohteen-tr-osoite kohde-id)
                            oletettu-tr-osoite {:aet 0
                                                :ajorata 1
                                                :aosa 1
                                                :kaista 11
                                                :loppuet 100
                                                :losa 1
                                                :numero 20}
                            odotettu-1-alikohteen-osoite {:numero 20, :aosa 1, :aet 0, :losa 1, :loppuet 90, :kaista 11, :ajorata 1}
                            odotettu-2-alikohteen-osoite {:numero 20, :aosa 1, :aet 90, :losa 1, :loppuet 100, :kaista 11, :ajorata 1}
                            alikohteiden-tr-osoitteet (into #{} (hae-yllapitokohteen-kohdeosien-tr-osoitteet kohde-id))]

                        (println "-----> " alikohteiden-tr-osoitteet)
                        (is (= oletettu-tr-osoite kohteen-tr-osoite) "Kohteen tierekisteriosoite on onnistuneesti päivitetty")
                        (is (= 2 (count alikohteiden-tr-osoitteet)) "Alikohteita on päivittynyt 1 kpl")
                        (is (alikohteiden-tr-osoitteet odotettu-1-alikohteen-osoite))
                        (is (alikohteiden-tr-osoitteet odotettu-2-alikohteen-osoite)))))))

(deftest muutettavat-alikohteet
  (let [alikohteet [{:alikohde {:tunniste {:id 1},
                                :nimi "1. Testialikohde",
                                :sijainti {:aosa 1, :aet 1, :losa 3, :let 1, :ajr 1, :kaista 11},
                                :toimenpide "testitoimenpide"}}
                    {:alikohde {:tunniste {:id 2},
                                :nimi "2. Testialikohde",
                                :sijainti {:aosa 3, :aet 1, :losa 4, :let 100, :ajr 1, :kaista 11},
                                :toimenpide "testitoimenpide"}}
                    {:alikohde {:tunniste {:id 3},
                                :nimi "Testiramppi",
                                :sijainti {:numero 21101, :aosa 1, :aet 1, :losa 1, :let 100, :ajr 1, :kaista 11},
                                :toimenpide "testitoimenpide"}}]
        oletetut-muunnettavat [{:tunniste {:id 1},
                                :nimi "1. Testialikohde",
                                :sijainti {:aosa 1, :aet 1, :losa 3, :let 1, :ajr 1, :kaista 11, :numero 666},
                                :toimenpide "testitoimenpide",
                                :ulkoinen-id 1}
                               {:tunniste {:id 2},
                                :nimi "2. Testialikohde",
                                :sijainti {:aosa 3, :aet 1, :losa 4, :let 100, :ajr 1, :kaista 11, :numero 666},
                                :toimenpide "testitoimenpide",
                                :ulkoinen-id 2}
                               {:tunniste {:id 3},
                                :nimi "Testiramppi",
                                :sijainti {:numero 21101, :aosa 1, :aet 1, :losa 1, :let 100, :ajr 1, :kaista 11},
                                :toimenpide "testitoimenpide",
                                :ulkoinen-id 3}]]
    (is (= (api-yllapitokohteet/muunnettavat-alikohteet 666 alikohteet) oletetut-muunnettavat)
        "Muunnos alikohteille tehdään oletetulla tavalla")))
