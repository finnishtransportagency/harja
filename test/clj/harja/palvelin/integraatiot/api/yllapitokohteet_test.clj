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

;; TODO Feikkaa tieverkon tsekkaus.
(deftest paallystysilmoituksen-kirjaaminen-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/paallystysilmoituksen_kirjaus.json"))]

    (log/debug "Vastaus: " (pr-str vastaus))
    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti."))


    (let [paallystysilmoitus (first (q (str "SELECT ilmoitustiedot, aloituspvm, valmispvm_kohde,
                                             muutoshinta, takuupvm, valmispvm_paallystys
                                             FROM paallystysilmoitus WHERE paallystyskohde = " kohde)))
          ilmoitustiedot (konv/jsonb->clojuremap (first paallystysilmoitus))]
      (is (= ilmoitustiedot {:tyot [{:tyo "työtehtävä"
                                     :tyyppi "tasaukset"
                                     :yksikko "kpl"
                                     :yksikkohinta 55.4
                                     :tilattu-maara 1.2
                                     :toteutunut-maara 1.2}],
                             :osoitteet [{:kohdeosa-id 15
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
                             :alustatoimet [{:verkkotyyppi 1 ;; TODO TR-osoitteet eivät tallennu oikein
                                             :aosa 1
                                             :let 15
                                             :verkon-tarkoitus 5
                                             :kasittelymenetelma 1
                                             :losa 5
                                             :aet 1
                                             :tekninen-toimenpide 1
                                             :paksuus 1.2
                                             :verkon-sijainti 1}]}))
      #_(is (match ilmoitustiedot
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
                                  :aosa 1
                                  :let 15
                                  :verkon-tarkoitus 5
                                  :kasittelymenetelma 1
                                  :losa 5
                                  :aet 1
                                  :tekninen-toimenpide 1
                                  :paksuus 1.2
                                  :verkon-sijainti 1}]}
                 true))
      ;; TODO Tarkista pvm:t
      (log/debug "POT: " (pr-str paallystysilmoitus)))))

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
    (is (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta"))

    ;; TODO Tarkista arvot kannasta

    ))

(deftest aikataulun-kirjaaminen-toimii-kohteelle-jolla-ilmoitus
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-jolla-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu"]
                                         kayttaja portti
                                         (slurp "test/resurssit/api/aikataulun_kirjaus.json"))]

    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (not (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta")))

    ;; TODO Tarkista arvot kannasta

    ))

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
