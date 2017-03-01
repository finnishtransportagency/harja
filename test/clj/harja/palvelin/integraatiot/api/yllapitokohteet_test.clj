(ns harja.palvelin.integraatiot.api.yllapitokohteet-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [clojure.core.match :refer [match]]
            [harja.palvelin.integraatiot.api.yllapitokohteet :as api-yllapitokohteet]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.skeema :as skeema]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [clojure.walk :as walk]
            [clojure.string :as str]))

(def kayttaja-paallystys "skanska")
(def kayttaja-tiemerkinta "tiemies")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-paallystys
    :api-yllapitokohteet (component/using (api-yllapitokohteet/->Yllapitokohteet) [:http-palvelin :db :integraatioloki])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-yllapitokohteiden-haku
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/5/yllapitokohteet"] kayttaja-paallystys portti)
        data (cheshire/decode (:body vastaus) true)
        yllapitokohteet (mapv :yllapitokohde (:yllapitokohteet data))
        leppajarven-ramppi (first (filter #(= (:nimi %) "Leppäjärven ramppi")
                                          yllapitokohteet))]
    (is (= 200 (:status vastaus)))
    (is (= 5 (count yllapitokohteet)))
    (is (some? leppajarven-ramppi))
    (is (some? (:tiemerkinta-takaraja (:aikataulu leppajarven-ramppi))))
    (is (some? (:paallystys-aloitettu (:aikataulu leppajarven-ramppi))))
    (is (some? (:paallystys-valmis (:aikataulu leppajarven-ramppi))))
    (is (some? (:valmis-tiemerkintaan (:aikataulu leppajarven-ramppi))))
    (is (some? (:tiemerkinta-aloitettu (:aikataulu leppajarven-ramppi))))
    (is (some? (:tiemerkinta-valmis (:aikataulu leppajarven-ramppi))))
    (is (some? (:kohde-valmis (:aikataulu leppajarven-ramppi))))
    (is (some? (:takuupvm (get-in leppajarven-ramppi [:aikataulu :paallystysilmoitus]))))))

(deftest yllapitokohteiden-haku-ei-toimi-ilman-oikeuksia
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/5/yllapitokohteet" urakka] "Erkki Esimerkki" portti)]
    (is (= 403 (:status vastaus)))
    (is (.contains (:body vastaus) "Tuntematon käyttäjätunnus: Erkki Esimerkki"))))

(deftest yllapitokohteiden-haku-ei-toimi-tuntemattomalle-urakalle
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/123467890/yllapitokohteet" urakka] kayttaja-paallystys portti)]
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "tuntematon-urakka"))))

(deftest uuden-paallystysilmoituksen-kirjaaminen-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-tielta-20-jolla-ei-paallystysilmoitusta)
        paallystysilmoitusten-maara-kannassa-ennen (ffirst (q "SELECT COUNT(*) FROM paallystysilmoitus"))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]

    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti."))

    ;; Tarkistetaan, että tiedot tallentuivat oikein
    (let [paallystysilmoitus (first (q (str "SELECT ilmoitustiedot, takuupvm, tila, id
                                             FROM paallystysilmoitus WHERE paallystyskohde = " kohde)))
          ilmoitustiedot (konv/jsonb->clojuremap (first paallystysilmoitus))
          paallystysilmoitusten-maara-kannassa-jalkeen (ffirst (q "SELECT COUNT(*) FROM paallystysilmoitus"))]
      ;; Päällystysilmoitusten määrä kasvoi yhdellä
      (is (= (+ paallystysilmoitusten-maara-kannassa-ennen 1) paallystysilmoitusten-maara-kannassa-jalkeen))

      ;; Tiedot ovat skeeman mukaiset
      (is (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+ ilmoitustiedot))

      ;; Tiedot vastaavat API:n kautta tullutta payloadia
      (is (match ilmoitustiedot
                 {:osoitteet [{:kohdeosa-id _
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
                                  :paksuus 1
                                  :verkon-sijainti 1}]}
                 true))
      (is (some? (get paallystysilmoitus 1)) "Takuupvm on")
      (is (= (get paallystysilmoitus 2) "aloitettu") "Ei asetettu käsiteltäväksi, joten tila on aloitettu")

      (let [alikohteet (q-map (str "SELECT sijainti, tr_numero FROM yllapitokohdeosa WHERE yllapitokohde = " kohde))]
        (is (every? #(and (not (nil? (:sijainti %))) (not (nil? (:tr_numero %)))) alikohteet)
            "Kaikilla alikohteilla on sijainti & tienumero"))

      (u "DELETE FROM paallystysilmoitus WHERE id = " (get paallystysilmoitus 3) ";"))))

(deftest uuden-paallystysilmoituksen-kirjaaminen-kasiteltavaksi-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-tielta-20-jolla-ei-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str true))))]

    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti."))

    ;; Tarkistetaan, että tila on valmis
    (let [tila (ffirst (q (str "SELECT tila FROM paallystysilmoitus WHERE paallystyskohde = " kohde)))]
      (is (= tila "valmis")))))

(deftest paallystysilmoituksen-paivittaminen-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-tielta-20-jolla-paallystysilmoitus)
        paallystysilmoitusten-maara-kannassa-ennen (ffirst (q "SELECT COUNT(*) FROM paallystysilmoitus"))
        vanha-paallystysilmoitus (first (q (str "SELECT ilmoitustiedot, takuupvm, tila
                                             FROM paallystysilmoitus WHERE paallystyskohde = " kohde)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]

    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti."))

    ;; Tarkistetana, että tiedot tallentuivat oikein
    (let [paallystysilmoitus (first (q (str "SELECT ilmoitustiedot, takuupvm, tila
                                             FROM paallystysilmoitus WHERE paallystyskohde = " kohde)))
          ilmoitustiedot (konv/jsonb->clojuremap (first paallystysilmoitus))
          paallystysilmoitusten-maara-kannassa-jalkeen (ffirst (q "SELECT COUNT(*) FROM paallystysilmoitus"))]
      ;; Pottien määrä pysyy samana
      (is (= paallystysilmoitusten-maara-kannassa-ennen paallystysilmoitusten-maara-kannassa-jalkeen))
      ;; Tiedot ovat skeeman mukaiset
      (is (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+
                          ilmoitustiedot))

      ;; Tiedot vastaavat API:n kautta tullutta payloadia
      (is (match ilmoitustiedot
                 {:osoitteet [{:kohdeosa-id _
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
                                  :paksuus 1
                                  :verkon-sijainti 1}]}
                 true))
      (is (some? (get paallystysilmoitus 1)) "Takuupvm on")
      (is (= (get paallystysilmoitus 2) (get vanha-paallystysilmoitus 2)) "Tila ei muuttunut miksikään"))))

(deftest paallystysilmoituksen-paivittaminen-ei-paivita-lukittua-paallystysilmoitusta
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-tielta-20-jolla-lukittu-paallystysilmoitus)
        _ (assert kohde "Ei lukittua kohdetta. Onko testidatassa vikaa?")
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]

    (is (= 500 (:status vastaus)))
    (is (.contains (:body vastaus) "Päällystysilmoitus on lukittu"))))

(deftest paallystysilmoituksen-kirjaaminen-ei-toimi-ilman-oikeuksia
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         (:kayttajanimi +kayttaja-tero+) portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]
    (is (= 403 (:status vastaus)))))

(deftest paallystysilmoituksen-kirjaaminen-estaa-paivittamasta-urakkaan-kuulumatonta-kohdetta
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "Ylläpitokohde ei kuulu urakkaan"))))

(deftest aikataulun-kirjaaminen-ilmoituksettomalle-kohteelle-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta"))))

(deftest aikataulun-kirjaaminen-toimii-kohteelle-jolla-ilmoitus
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-muhoksen-yllapitokohde-jolla-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]

    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (not (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta")))))

(deftest aikataulun-kirjaaminen-paallystysurakan-kohteelle-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vanhat-aikataulutiedot (first (q (str "SELECT aikataulu_paallystys_alku, aikataulu_paallystys_loppu,
                                                 valmis_tiemerkintaan, aikataulu_tiemerkinta_alku,
                                                 aikataulu_tiemerkinta_loppu FROM yllapitokohde
                                                 WHERE id = " kohde)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
    (is (= 200 (:status vastaus)))
    (is (.contains (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (.contains (:body vastaus) "Kohteella ei ole päällystysilmoitusta"))

    (let [aikataulutiedot (first (q (str "SELECT aikataulu_paallystys_alku, aikataulu_paallystys_loppu,
                                                 valmis_tiemerkintaan, aikataulu_tiemerkinta_alku,
                                                 aikataulu_tiemerkinta_loppu, aikataulu_tiemerkinta_takaraja FROM yllapitokohde
                                                 WHERE id = " kohde)))]
      ;; Uudet päällystyksen pvm:t tallentuivat oikein
      (is (some? (get aikataulutiedot 0)))
      (is (some? (get aikataulutiedot 1)))
      (is (some? (get aikataulutiedot 2)))
      (is (some? (get aikataulutiedot 5)))
      ;; Tiemerkinnän tiedot eivät päivity, koska kyseessä ei ole tiemerkintäurakka
      (is (= (get aikataulutiedot 3) (get vanhat-aikataulutiedot 3)))
      (is (= (get aikataulutiedot 4) (get vanhat-aikataulutiedot 4))))))

(deftest aikataulun-kirjaaminen-tiemerkintaurakan-kohteelle-toimii
  (let [urakka (hae-oulun-tiemerkintaurakan-id)
        kohde (hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa urakka)
        vanhat-aikataulutiedot (first (q (str "SELECT aikataulu_paallystys_alku, aikataulu_paallystys_loppu,
                                                 valmis_tiemerkintaan, aikataulu_tiemerkinta_alku,
                                                 aikataulu_tiemerkinta_loppu FROM yllapitokohde
                                                 WHERE id = " kohde)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         kayttaja-tiemerkinta portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]
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
      (is (some? (get aikataulutiedot 3)))
      (is (some? (get aikataulutiedot 4))))))

(deftest aikataulun-kirjaaminen-paallystykseen-ei-toimi-ilman-oikeuksia
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         (:kayttajanimi +kayttaja-tero+) portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]

    (is (= 403 (:status vastaus)))))

(deftest aikataulun-kirjaaminen-tiemerkintaan-ei-toimi-ilman-oikeuksia
  (let [urakka (hae-oulun-tiemerkintaurakan-id)
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         (:kayttajanimi +kayttaja-tero+) portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]

    (is (= 403 (:status vastaus)))))

(deftest tiemerkinnan-aikataulun-kirjaus-ei-onnistu-paallystysurakalle
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]

    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "mutta urakan tyyppi on"))))

(deftest paallystyksen-aikataulun-kirjaus-ei-onnistu-tiemerkintaurakalle
  (let [urakka (hae-oulun-tiemerkintaurakan-id)
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-tiemerkinta portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]

    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "mutta urakan tyyppi on"))))

(deftest aikataulun-kirjaaminen-estaa-paivittamasta-urakkaan-kuulumatonta-kohdetta
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "Ylläpitokohde ei kuulu urakkaan"))))

(deftest avoimen-kohteen-paivittaminen-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde-id (hae-yllapitokohde-kuusamontien-testi-jolta-puuttuu-paallystysilmoitus)
        payload (slurp "test/resurssit/api/paallystyskohteen-paivitys-request.json")
        {status :status} (api-tyokalut/put-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id]
                                                 kayttaja-paallystys portti
                                                 payload)]
    (is (= 200 status))

    (let [kohteen-tr-osoite (hae-yllapitokohteen-tr-osoite kohde-id)
          oletettu-tr-osoite {:aet 1
                              :ajorata 1
                              :aosa 14
                              :kaista 1
                              :loppuet 1
                              :losa 17
                              :numero 20}
          alikohteiden-tr-osoitteet (hae-yllapitokohteen-kohdeosien-tr-osoitteet kohde-id)
          oletettu-ensimmaisen-alikohteen-tr-osoite {:aet 1
                                                     :ajorata 1
                                                     :aosa 14
                                                     :kaista 1
                                                     :loppuet 666
                                                     :losa 14
                                                     :numero 20}
          oletettu-toisen-alikohteen-tr-osoite {:aet 666
                                                :ajorata 1
                                                :aosa 14
                                                :kaista 1
                                                :loppuet 1
                                                :losa 17
                                                :numero 20}]
      (is (= oletettu-tr-osoite kohteen-tr-osoite) "Kohteen tierekisteriosoite on onnistuneesti päivitetty")
      (is (= 2 (count alikohteiden-tr-osoitteet)) "Alikohteita on päivittynyt 2 kpl")
      (is (= oletettu-ensimmaisen-alikohteen-tr-osoite (first alikohteiden-tr-osoitteet))
          "Ensimmäisen alikohteen tierekisteriosite on päivittynyt oikein")
      (is (= oletettu-toisen-alikohteen-tr-osoite (second alikohteiden-tr-osoitteet))
          "Toisen alikohteen tierekisteriosite on päivittynyt oikein")

      (let [alikohteet (q-map (str "SELECT sijainti, tr_numero FROM yllapitokohdeosa WHERE yllapitokohde = " kohde-id))]
        (is (every? #(and (not (nil? (:sijainti %))) (not (nil? (:tr_numero %)))) alikohteet)
            "Kaikilla alikohteilla on sijainti & tienumero")))))

(deftest paallystysilmoituksellisen-kohteen-paivitys-ei-onnistu
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        payload (slurp "test/resurssit/api/paallystyskohteen-paivitys-request.json")
        {status :status body :body} (api-tyokalut/put-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id]
                                                            kayttaja-paallystys portti
                                                            payload)]
    (is (= 400 status))
    (is (= "lukittu-yllapitokohde" (:koodi (:virhe (first (:virheet (cheshire/decode body true))))))
        "Virheelliselle kirjaukselle palautetaan oikea virhekoodi.")))

(deftest maaramuutosten-kirjaaminen-kohteelle-toimii
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        kohde-id (hae-yllapitokohde-kuusamontien-testi-jolta-puuttuu-paallystysilmoitus)
        _ (u "INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde, tyon_tyyppi, tyo, yksikko, tilattu_maara, toteutunut_maara, yksikkohinta, poistettu, luoja, luotu, muokkaaja, muokattu, jarjestelma, ulkoinen_id, ennustettu_maara)
              VALUES (" kohde-id ", 'ajoradan_paallyste', 'Esimerkki työ', 'm2', 12, 14.2, 666, FALSE, 10, '2017-01-31 15:34:32', NULL, NULL, NULL, NULL, NULL)")
        hae-maaramuutokset #(q-map "SELECT * FROM yllapitokohteen_maaramuutos WHERE yllapitokohde = " kohde-id)
        maaramuutokset-ennen-kirjausta (hae-maaramuutokset)
        harjan-kautta-kirjattu (first maaramuutokset-ennen-kirjausta)
        polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/maaramuutokset"]
        kutsudata (slurp "test/resurssit/api/maaramuutosten-kirjaus-request.json")
        vastaus (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata)
        maaramuutokset-kirjauksen-jalkeen (hae-maaramuutokset)]

    (is (= 200 (:status vastaus)) "Kirjaus tehtiin onnistuneesti")
    (is (.contains (:body vastaus) "Määrämuutokset kirjattu onnistuneesti."))
    (is (= (+ 1 (count maaramuutokset-ennen-kirjausta)) (count maaramuutokset-kirjauksen-jalkeen))
        "Vain yksi uusi määrämuutos on kirjautunut")

    (let [kutsudata (str/replace kutsudata "\"yksikkohinta\":666" "\"yksikkohinta\":888")
          vastaus (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata)
          maaramuutokset-kirjauksen-jalkeen (hae-maaramuutokset)]

      (is (= 200 (:status vastaus)) "Kirjaus tehtiin onnistuneesti")
      (is (.contains (:body vastaus) "Määrämuutokset kirjattu onnistuneesti."))
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
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        kohde-id (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka-id)
        kutsudata (slurp "test/resurssit/api/maaramuutosten-kirjaus-request.json")
        polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/maaramuutokset"]
        vastaus (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata)]
    (is (= 400 (:status vastaus)))
    (is (.contains (:body vastaus) "tuntematon-yllapitokohde"))))