(ns harja.palvelin.integraatiot.api.yllapitokohteet_test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [clojure.core.match :refer [match]]
            [harja.palvelin.integraatiot.api.yllapitokohteet :as api-yllapitokohteet]
            [harja.kyselyt.konversio :as konv]))

(def kayttaja "skanska")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-yllapitokohteet (component/using (api-yllapitokohteet/->Yllapitokohteet) [:http-palvelin :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(deftest tarkista-yllapitokohteiden-haku
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/5/yllapitokohteet"] kayttaja portti)
        data (cheshire/decode (:body vastaus) true)]
    (is (= 200 (:status vastaus)))
    (is (= 5 (count (:yllapitokohteet data))))))

(deftest yllapitokohteiden-haku-ei-toimi-ilman-oikeuksia
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/5/yllapitokohteet" urakka] "Erkki Esimerkki" portti)]
    (is (= 403 (:status vastaus)))
    (is (.contains (:body vastaus) "Tuntematon käyttäjätunnus: Erkki Esimerkki"))))

(deftest yllapitokohteiden-haku-ei-toimi-tuntemattomalle-urakalle
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/123467890/yllapitokohteet" urakka] kayttaja portti)]
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "tuntematon-urakka"))))

(deftest paallystysilmoituksen-kirjaaminen-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/paallystysilmoituksen_kirjaus.json"))]

    (log/debug "Vastaus: " (pr-str vastaus))
    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti."))


    ;; Tarkistetana, että tiedot tallentuivat oikein
    (let [paallystysilmoitus (first (q (str "SELECT ilmoitustiedot, aloituspvm, valmispvm_kohde,
                                             takuupvm, valmispvm_paallystys, muutoshinta
                                             FROM paallystysilmoitus WHERE paallystyskohde = " kohde)))
          ilmoitustiedot (konv/jsonb->clojuremap (first paallystysilmoitus))]
      (is (match ilmoitustiedot ;; Tiedot vastaavat API:n kautta tullutta payloadia
                 {:tyot [{:tyo "työtehtävä"
                          :tyyppi "tasaukset"
                          :yksikko "kpl"
                          :yksikkohinta 55.4
                          :tilattu-maara 1.2
                          :toteutunut-maara 1.2}],
                  :osoitteet [{:kohdeosa-id _
                               :edellinen-paallystetyyppi nil
                               :lisaaineet "lisäaineet"
                               :leveys 1.2
                               :kokonaismassamaara 12.3
                               :sideainetyyppi 1
                               :muotoarvo "testi"
                               :esiintyma "testi"
                               :pitoisuus 1.2
                               :pinta-ala 2.2
                               :massamenekki 22
                               :kuulamylly 4
                               :raekoko 12
                               :tyomenetelma 72
                               :rc% 54
                               :paallystetyyppi 11
                               :km-arvo "testi"}],
                  :alustatoimet [{:verkkotyyppi 1
                                  :tr-alkuosa 1
                                  :tr-loppuetaisyys 15
                                  :verkon-tarkoitus 5
                                  :kasittelymenetelma 1
                                  :tr-loppuosa 5
                                  :tr-alkuetaisyys 1
                                  :tekninen-toimenpide 1
                                  :paksuus 1.2
                                  :verkon-sijainti 1}]}
                 true))
      (is (some? (get paallystysilmoitus 1)))
      (is (some? (get paallystysilmoitus 2)))
      (is (some? (get paallystysilmoitus 3)))
      (is (some? (get paallystysilmoitus 4))))))

(deftest paallystysilmoituksen-kirjaaminen-ei-toimi-ilman-oikeuksia
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         +kayttaja-tero+ portti
                                         (slurp "test/resurssit/api/paallystysilmoituksen_kirjaus.json"))]
    (is (= 403 (:status vastaus)))))

(deftest paallystysilmoituksen-kirjaaminen-estaa-paivittamasta-urakkaan-kuulumatonta-kohdetta
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/paallystysilmoituksen_kirjaus.json"))]
    (is (= 500 (:status vastaus)))))

(deftest aikataulun-kirjaaminen-ilmoituksettomalle-kohteelle-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]
    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta"))))

(deftest aikataulun-kirjaaminen-toimii-kohteelle-jolla-ilmoitus
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-jolla-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]

    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (not (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta")))))

(deftest aikataulun-kirjaaminen-paallystysurakan-kohteelle-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-ilman-paallystysilmoitusta)
        vanhat-aikataulutiedot (first (q (str "SELECT aikataulu_paallystys_alku, aikataulu_paallystys_loppu,
                                                 valmis_tiemerkintaan, aikataulu_tiemerkinta_alku,
                                                 aikataulu_tiemerkinta_loppu FROM yllapitokohde
                                                 WHERE id = " kohde)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]
    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta"))

    (let [aikataulutiedot (first (q (str "SELECT aikataulu_paallystys_alku, aikataulu_paallystys_loppu,
                                                 valmis_tiemerkintaan, aikataulu_tiemerkinta_alku,
                                                 aikataulu_tiemerkinta_loppu FROM yllapitokohde
                                                 WHERE id = " kohde)))]
      ;; Uudet päällystyksen pvm:t tallentuivat oikein
      ;; TODO Tarkat pvm-tarkistukset!
      (is (some? (get aikataulutiedot 0)))
      (is (some? (get aikataulutiedot 1)))
      (is (some? (get aikataulutiedot 2)))
      ;; Tiemerkinnän tiedot eivät päivity, koska kyseessä ei ole tiemerkintäurakka
      (is (= (get aikataulutiedot 3) (get vanhat-aikataulutiedot 3)))
      (is (= (get aikataulutiedot 4) (get vanhat-aikataulutiedot 4))))))

(deftest aikataulun-kirjaaminen-tiemerkintaurakan-kohteelle-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id) ;; TODO VALITSE TIEMERKINTÄURAKKA
        kohde (hae-yllapitokohde-jolla-paallystysilmoitusta)
        vanhat-aikataulutiedot (first (q (str "SELECT aikataulu_paallystys_alku, aikataulu_paallystys_loppu,
                                                 valmis_tiemerkintaan, aikataulu_tiemerkinta_alku,
                                                 aikataulu_tiemerkinta_loppu FROM yllapitokohde
                                                 WHERE id = " kohde)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]

    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (not (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta")))

    (let [aikataulutiedot (first (q (str "SELECT aikataulu_paallystys_alku, aikataulu_paallystys_loppu,
                                                 valmis_tiemerkintaan, aikataulu_tiemerkinta_alku,
                                                 aikataulu_tiemerkinta_loppu FROM yllapitokohde
                                                 WHERE id = " kohde)))]
      ; Päällystyksen tiedot eivät tallennu, koska päivitetään tiemerkintäurakkaa
      (is (= (get aikataulutiedot 0) (get vanhat-aikataulutiedot 0)))
      (is (= (get aikataulutiedot 1) (get vanhat-aikataulutiedot 1)))
      (is (= (get aikataulutiedot 2) (get vanhat-aikataulutiedot 2)))
      ;; Valittu tiemerkintäurakka on valittu suorittamaan kyseinen ylläpitokohde, joten pvm:t päivittyvät:
      ;; TODO Tarkat pvm-tarkistukset!
      (is (some? (get aikataulutiedot 3)))
      (is (some? (get aikataulutiedot 4))))))

(deftest aikataulun-kirjaaminen-ei-toimi-ilman-oikeuksia
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         +kayttaja-tero+ portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]

    (is (= 403 (:status vastaus)))))

(deftest aikataulun-kirjaaminen-estaa-paivittamasta-urakkaan-kuulumatonta-kohdetta
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]
    (is (= 500 (:status vastaus)))))
