(ns harja.palvelin.integraatiot.api.yllapitokohteet-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.yllapitokohteet :as api-yllapitokohteet]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.skeema :as skeema]
            [harja.palvelin.ajastetut-tehtavat.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.integraatiot.vkm.vkm-test :refer [+testi-vkm+]]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [clojure.core.async :refer [<!! timeout]]
            [clojure.string :as str]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.komponentit.sonja :as sonja]
            [clojure.java.io :as io]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]
            [harja.palvelin.integraatiot.sonja.sahkoposti.sanomat :as sanomat])
  (:use org.httpkit.fake))

(def kayttaja-paallystys "skanska")
(def kayttaja-tiemerkinta "tiemies")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja-paallystys
    :fim (component/using
           (fim/->FIM +testi-fim+)
           [:db :integraatioloki])
    :vkm (component/using
           (vkm/->VKM +testi-vkm+)
           [:db :integraatioloki])
    :sonja (feikki-sonja)
    :sonja-sahkoposti (component/using
                        (sahkoposti/luo-sahkoposti "foo@example.com"
                                                   {:sahkoposti-sisaan-jono "email-to-harja"
                                                    :sahkoposti-ulos-jono "harja-to-email"
                                                    :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                        [:sonja :db :integraatioloki])
    :api-yllapitokohteet (component/using (api-yllapitokohteet/->Yllapitokohteet)
                                          [:http-palvelin :db :integraatioloki :liitteiden-hallinta
                                           :fim :vkm :sonja-sahkoposti])))

(use-fixtures :each jarjestelma-fixture)

(deftest tarkista-yllapitokohteiden-haku
  (let [muhoksen-paallystysurakan-id (hae-muhoksen-paallystysurakan-id)
        vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" muhoksen-paallystysurakan-id "/yllapitokohteet")]
                                        kayttaja-paallystys
                                        portti)
        data (cheshire/decode (:body vastaus) true)
        yllapitokohteet (mapv :yllapitokohde (:yllapitokohteet data))
        leppajarven-ramppi-2017 (first (filter #(= (:nimi %) "Leppäjärven ramppi")
                                               yllapitokohteet))
        leppajarven-ramppi-2018 (first (filter #(= (:nimi %) "Leppäjärven ramppi 2018")
                                               yllapitokohteet))]

    (log/debug "leppajarven-ramppi-2018 " leppajarven-ramppi-2018)

    (is (= 200 (:status vastaus)))
    (is (= 12 (count yllapitokohteet))
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
  (let [muhoksen-paallystysurakan-id (hae-muhoksen-paallystysurakan-id)
        vastaus (api-tyokalut/get-kutsu [(str "/api/urakat/" muhoksen-paallystysurakan-id "/yllapitokohteet") urakka]
                                        "Erkki Esimerkki"
                                        portti)]
    (is (= 403 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Tuntematon käyttäjätunnus: Erkki Esimerkki"))))

(deftest yllapitokohteiden-haku-ei-toimi-tuntemattomalle-urakalle
  (let [vastaus (api-tyokalut/get-kutsu ["/api/urakat/123467890/yllapitokohteet" urakka] kayttaja-paallystys portti)]
    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "tuntematon-urakka"))))

(deftest uuden-paallystysilmoituksen-kirjaaminen-toimii
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        ;; Testiä varten tuhoa kohteen olemassa oleva POT, kirjataan siis uusi
        _ (u "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id ";")
        paallystysilmoitusten-maara-kannassa-ennen (ffirst (q "SELECT COUNT(*) FROM paallystysilmoitus"))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))
        kohdeosa-1-kannassa (first (q-map (str "SELECT id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,
        tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, toimenpide, paallystetyyppi, raekoko, tyomenetelma, massamaara
        FROM yllapitokohdeosa WHERE yllapitokohde = " kohde-id " AND nimi = '1. testialikohde' LIMIT 1;")))
        kohdeosa-2-kannassa (first (q-map (str "SELECT id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,
        tr_loppuosa, tr_loppuetaisyys, tr_ajorata, tr_kaista, toimenpide, paallystetyyppi, raekoko, tyomenetelma, massamaara
        FROM yllapitokohdeosa WHERE yllapitokohde = " kohde-id " AND nimi = '2. testialikohde' LIMIT 1;")))]

    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti."))

    ;; Tarkistetaan, että tiedot tallentuivat oikein
    (let [paallystysilmoitus (first (q (str "SELECT ilmoitustiedot, takuupvm, tila, id
                                             FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id)))
          ilmoitustiedot-kannassa (konv/jsonb->clojuremap (first paallystysilmoitus))
          paallystysilmoitusten-maara-kannassa-jalkeen (ffirst (q "SELECT COUNT(*) FROM paallystysilmoitus"))]
      ;; Päällystysilmoitusten määrä kasvoi yhdellä
      (is (= (+ paallystysilmoitusten-maara-kannassa-ennen 1) paallystysilmoitusten-maara-kannassa-jalkeen))

      ;; Tiedot ovat skeeman mukaiset
      (is (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+ ilmoitustiedot-kannassa))

      ;; Tiedot vastaavat API:n kautta tullutta payloadia
      (tarkista-map-arvot {:alustatoimet [{:kasittelymenetelma 1
                                           :paksuus 1
                                           :tekninen-toimenpide 1
                                           :tr-numero 22
                                           :tr-alkuetaisyys 1
                                           :tr-alkuosa 3
                                           :tr-loppuetaisyys 10
                                           :tr-loppuosa 3
                                           :tr-ajorata 1
                                           :tr-kaista 11
                                           :verkkotyyppi 1
                                           :verkon-sijainti 1
                                           :verkon-tarkoitus 5}]
                           :osoitteet [{:esiintyma "testi"
                                        :km-arvo "testi"
                                        :kokonaismassamaara 12.3
                                        :kuulamylly 4
                                        :leveys 1.2
                                        :lisaaineet "lisäaineet"
                                        :massamenekki 22
                                        :muotoarvo "testi"
                                        :paallystetyyppi 11
                                        :pinta-ala 2.2
                                        :pitoisuus 1.2
                                        :raekoko 12
                                        :rc% 54
                                        :sideainetyyppi 1
                                        :tyomenetelma 72}
                                       {:esiintyma "testi2"
                                        :km-arvo "testi2"
                                        :kokonaismassamaara 12.3
                                        :kuulamylly 4
                                        :leveys 1.2
                                        :lisaaineet "lisäaineet"
                                        :massamenekki 22
                                        :muotoarvo "testi2"
                                        :paallystetyyppi 11
                                        :pinta-ala 2.2
                                        :pitoisuus 1.2
                                        :raekoko 12
                                        :rc% 54
                                        :sideainetyyppi 1
                                        :tyomenetelma 72}]}
                          (update ilmoitustiedot-kannassa :osoitteet
                                  (fn [osoitteet]
                                    (mapv #(dissoc % :kohdeosa-id) osoitteet))))
      (tarkista-map-arvot
        {:massamaara nil
         :nimi "1. testialikohde"
         :paallystetyyppi nil
         :raekoko nil
         :toimenpide nil
         :tr_ajorata 1
         :tr_alkuetaisyys 1
         :tr_alkuosa 3
         :tr_kaista 11
         :tr_loppuetaisyys 10
         :tr_loppuosa 3
         :tr_numero 22
         :tyomenetelma nil
         :yllapitokohde kohde-id}
        (dissoc kohdeosa-1-kannassa :id))
      (tarkista-map-arvot
        {:massamaara nil
         :nimi "2. testialikohde"
         :paallystetyyppi nil
         :raekoko nil
         :toimenpide nil
         :tr_ajorata 2
         :tr_alkuetaisyys 3030
         :tr_alkuosa 3
         :tr_kaista 21
         :tr_loppuetaisyys 5
         :tr_loppuosa 4
         :tr_numero 22
         :tyomenetelma nil
         :yllapitokohde kohde-id}
        (dissoc kohdeosa-2-kannassa :id))
      (is (some? (get paallystysilmoitus 1)) "Takuupvm on")
      (is (= (get paallystysilmoitus 2) "aloitettu") "Ei asetettu käsiteltäväksi, joten tila on aloitettu")

      (let [alikohteet (q-map (str "SELECT sijainti, tr_numero FROM yllapitokohdeosa WHERE yllapitokohde = " kohde-id))]
        (is (every? #(and (not (nil? (:sijainti %))) (not (nil? (:tr_numero %)))) alikohteet)
            "Kaikilla alikohteilla on sijainti & tienumero"))

      (u "DELETE FROM paallystysilmoitus WHERE id = " (get paallystysilmoitus 3) ";"))))

(deftest uuden-paallystysilmoituksen-kirjaaminen-ilman-alikohteen-ajorataa-ja-kaistaa-ei-toimi
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        ;; Testiä varten tuhoa kohteen olemassa oleva POT, kirjataan siis uusi
        _ (u "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id ";")
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus_ilman_alikohteen_ajorataa_ja_kaistaa.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "JSON ei ole validia"))))

(deftest uuden-paallystysilmoituksen-kirjaaminen-kasiteltavaksi-toimii
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        ;; Poista päällystysilmoitus testiä varten
        _ (u "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id ";")
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str true))))]

    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti."))

    ;; Tarkistetaan, että tila on valmis
    (let [tila (ffirst (q (str "SELECT tila FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id)))]
      (is (= tila "valmis")))))

(deftest paallystysilmoituksen-paivittaminen-toimii
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        paallystysilmoitusten-maara-kannassa-ennen (ffirst (q "SELECT COUNT(*) FROM paallystysilmoitus"))
        vanha-paallystysilmoitus (first (q (str "SELECT ilmoitustiedot, takuupvm, tila
                                             FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id)))
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]

    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti."))

    ;; Tarkistetaan, että tiedot tallentuivat oikein
    (let [paallystysilmoitus (first (q (str "SELECT ilmoitustiedot, takuupvm, tila
                                             FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id)))
          ilmoitustiedot-kannassa (konv/jsonb->clojuremap (first paallystysilmoitus))
          paallystysilmoitusten-maara-kannassa-jalkeen (ffirst (q "SELECT COUNT(*) FROM paallystysilmoitus"))
          kohdeosa-1-kannassa (first (q-map (str "SELECT id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,
        tr_loppuosa, tr_loppuetaisyys, tr_kaista, tr_ajorata, toimenpide, paallystetyyppi, raekoko, tyomenetelma, massamaara
        FROM yllapitokohdeosa WHERE yllapitokohde = " kohde-id " AND nimi = '1. testialikohde' LIMIT 1;")))
          kohdeosa-2-kannassa (first (q-map (str "SELECT id, yllapitokohde, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,
        tr_loppuosa, tr_loppuetaisyys, tr_kaista, tr_ajorata, toimenpide, paallystetyyppi, raekoko, tyomenetelma, massamaara
        FROM yllapitokohdeosa WHERE yllapitokohde = " kohde-id " AND nimi = '2. testialikohde' LIMIT 1;")))]
      ;; Pottien määrä pysyy samana
      (is (= paallystysilmoitusten-maara-kannassa-ennen paallystysilmoitusten-maara-kannassa-jalkeen))
      ;; Tiedot ovat skeeman mukaiset
      (is (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+ ilmoitustiedot-kannassa))

      ;; Tiedot vastaavat API:n kautta tullutta payloadia
      (is (= (reduce-kv (fn [m k v]
                          (assoc m k (if (= k :osoitteet)
                                       (mapv (fn [tiedot]
                                               (dissoc tiedot :kohdeosa-id))
                                             v)
                                       v)))
                        {} ilmoitustiedot-kannassa)
             {:osoitteet [{
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
	                   :km-arvo "testi"}
	                  {:lisaaineet "lisäaineet"
	                   :leveys 1.2
	                   :kokonaismassamaara 12.3
	                   :sideainetyyppi 1
	                   :muotoarvo "testi2"
	                   :esiintyma "testi2"
	                   :pitoisuus 1.2
	                   :pinta-ala 2.2
	                   :massamenekki 22
	                   :kuulamylly 4
	                   :raekoko 12
	                   :tyomenetelma 72
	                   :rc% 54
	                   :paallystetyyppi 11
	                   :km-arvo "testi2"}]
	      :alustatoimet [{:tr-kaista 11
	                      :verkkotyyppi 1
	                      :tr-ajorata 1
	                      :verkon-tarkoitus 5
	                      :kasittelymenetelma 1
	                      :tr-loppuosa 3
	                      :tr-alkuosa 3
	                      :tekninen-toimenpide 1
	                      :tr-loppuetaisyys 10
	                      :tr-alkuetaisyys 1
	                      :tr-numero 22
	                      :paksuus 1
	                      :verkon-sijainti 1}]}))

      (is (= (dissoc kohdeosa-1-kannassa :id)
             {:yllapitokohde 22
	      :tr_kaista 11
	      :massamaara nil
	      :nimi "1. testialikohde"
	      :tr_loppuosa 3
	      :raekoko nil
	      :tyomenetelma nil
	      :tr_numero 22
	      :paallystetyyppi nil
	      :tr_loppuetaisyys 10
	      :tr_alkuetaisyys 1
	      :tr_ajorata 1
	      :tr_alkuosa 3
	      :toimenpide nil}))
      (is (= (dissoc kohdeosa-2-kannassa :id)
             {:yllapitokohde 22
	      :tr_kaista 21
	      :massamaara nil
	      :nimi "2. testialikohde"
	      :tr_loppuosa 4
	      :raekoko nil
	      :tyomenetelma nil
	      :tr_numero 22
	      :paallystetyyppi nil
	      :tr_loppuetaisyys 5
	      :tr_alkuetaisyys 3030
	      :tr_ajorata 2
	      :tr_alkuosa 3
	      :toimenpide nil}))
      (is (some? (get paallystysilmoitus 1)) "Takuupvm on")
      (is (= (get paallystysilmoitus 2) (get vanha-paallystysilmoitus 2)) "Tila ei muuttunut miksikään"))))

(deftest paallystysilmoituksen-paivittaminen-muiden-kohdeosien-paalle-ei-onnistu
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-yllapitokohde-tielta-20-jolla-paallystysilmoitus)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus_kohdeosat_paallekkain.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]

    (is (not= 200 (:status vastaus)))
    (is (not (str/includes? (:body vastaus) "Päällystysilmoitus kirjattu onnistuneesti.")))))

(deftest paallystysilmoituksen-paivittaminen-ei-paivita-lukittua-paallystysilmoitusta
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        _ (u "UPDATE paallystysilmoitus SET tila = 'lukittu' WHERE paallystyskohde = " kohde-id)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]

    (is (= 500 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Päällystysilmoitus on lukittu"))))

(deftest paallystysilmoituksen-kirjaaminen-ei-toimi-ilman-oikeuksia
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         "LX123456789" portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]
    (is (= 403 (:status vastaus)))))

(deftest paallystysilmoituksen-kirjaaminen-estaa-paivittamasta-urakkaan-kuulumatonta-kohdetta
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/paallystysilmoitus"]
                                         kayttaja-paallystys portti
                                         (-> "test/resurssit/api/paallystysilmoituksen_kirjaus.json"
                                             slurp
                                             (.replace "__VALMIS__" (str false))))]
    (is (= 400 (:status vastaus)))))

(deftest aikataulun-kirjaaminen-ilmoituksettomalle-kohteelle-toimii
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (str/includes? (:body vastaus) "Kohteella ei ole päällystysilmoitusta"))))

(deftest paallystyksen-aikataulun-paivittaminen-valittaa-sahkopostin-kun-kohde-valmis-tiemerkintaan-paivittyy
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        sahkoposti-valitetty (atom false)]
    (sonja/kuuntele! (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus
       #".*api\/urakat.*" :allow]
      (let [urakka-id (hae-muhoksen-paallystysurakan-id)
            kohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
            vastaus (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-paallystys")]
                                             kayttaja-paallystys portti
                                             (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
        (is (= 200 (:status vastaus)))

        ;; Leppäjärvi oli jo merkitty valmiiksi tiemerkintään, mutta sitä päivitettiin -> pitäisi lähteä maili
        (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 5000)
        (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")

        ;; Laitetaan sama pyyntö uudelleen, maili ei lähde koska valmis tiemerkintään -pvm sama kuin aiempi
        (reset! sahkoposti-valitetty false)
        (let [vastaus (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-paallystys")]
                                               kayttaja-paallystys portti
                                               (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
          (is (= 200 (:status vastaus)))
          (<!! (timeout 5000))
          (is (false? @sahkoposti-valitetty) "Sähköposti ei lähtenyt, eikä pitänytkään"))))))

(deftest paallystyksen-aikataulun-paivittaminen-valittaa-sahkopostin-kun-kohde-valmis-tiemerkintaan-ekaa-kertaa
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        sahkoposti-valitetty (atom false)]
    (sonja/kuuntele! (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus
       #".*api\/urakat.*" :allow]
      (let [urakka-id (hae-muhoksen-paallystysurakan-id)
            kohde-id (hae-yllapitokohde-nakkilan-ramppi)
            vastaus (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-paallystys")]
                                             kayttaja-paallystys portti
                                             (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
        (is (= 200 (:status vastaus)))

        ;; Valmiiksi tiemerkintään annettiin ekaa kertaa tälle kohteelle -> pitäisi lähteä maili
        (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 5000)
        (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")))))

(deftest tiemerkinnan-paivittaminen-valittaa-sahkopostin-kun-kohde-valmis
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        sahkoposti-valitetty (atom false)
        viestit (atom nil)]
    (sonja/kuuntele! (:sonja jarjestelma)
                    "harja-to-email"
                    (fn [viesti]
                      (reset! viestit (conj @viestit (sanomat/lue-sahkoposti (.getText viesti))))
                      (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus
       #".*api\/urakat.*" :allow]
      (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
            kohde-id (hae-yllapitokohde-nakkilan-ramppi)
            vastaus (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-tiemerkinta")]
                                             kayttaja-tiemerkinta portti
                                             (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]
        (is (= 200 (:status vastaus)))

        ;; Tiemerkintä valmis oli annettu aiemmin, mutta nyt se päivittyi -> mailia menemään
        (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 5000)
        (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")

        ;; Lähetetään sama pyyntö uudelleen, pvm ei muutu, ei lennä mailit
        (reset! sahkoposti-valitetty false)
        ;; FIXME Onkohan tämä bugi? Maili ei kai saisi lähteä jos pvm on sama kuin ennen. Nyt näyttää siltä että joskus menee testi läpi ja joskus ei
        #_(let [vastaus (api-tyokalut/post-kutsu [(str "/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/aikataulu-tiemerkinta")]
                                                 kayttaja-tiemerkinta portti
                                                 (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]
            (is (= 200 (:status vastaus)))
            (<!! (timeout 5000))
            (is (false? @sahkoposti-valitetty) "Maili ei lähtenyt, eikä pitänytkään"))))))

(deftest aikataulun-kirjaaminen-toimii-kohteelle-jolla-ilmoitus
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]

    (is (= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Aikataulu kirjattu onnistuneesti."))
    (is (not (str/includes? (:body vastaus) "Kohteella ei ole päällystysilmoitusta")))))

(deftest aikataulun-kirjaaminen-paallystysurakan-kohteelle-toimii
  (let [urakka (hae-utajarven-paallystysurakan-id)
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
  (let [urakka (hae-oulun-tiemerkintaurakan-id)
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
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         "LX123456789" portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]

    (is (= 403 (:status vastaus)))))

(deftest aikataulun-kirjaaminen-tiemerkintaan-ei-toimi-ilman-oikeuksia
  (let [urakka (hae-oulun-tiemerkintaurakan-id)
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         "LX123456789" portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]

    (is (= 403 (:status vastaus)))))

(deftest tiemerkinnan-aikataulun-kirjaus-ei-onnistu-paallystysurakalle
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "mutta urakan tyyppi on"))))

(deftest paallystyksen-aikataulun-kirjaus-ei-onnistu-tiemerkintaurakalle
  (let [urakka (hae-oulun-tiemerkintaurakan-id)
        kohde (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-tiemerkinta portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "mutta urakan tyyppi on"))))

(deftest paallystyksen-viallisen-aikataulun-kirjaus-ei-onnistu-tiemerkintapvm-vaarin
  (let [urakka (hae-utajarven-paallystysurakan-id)
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
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus_viallinen_paallystys_valmis_ilman_paallystyksen_alkua.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Päällystystä ei voi merkitä valmiiksi, aloitus puuttuu."))))

(deftest tiemerkinnan-viallisen-aikataulun-kirjaus-ei-onnistu-tiemerkinnan-valmispvm-vaarin
  (let [urakka (hae-oulun-tiemerkintaurakan-id)
        kohde (hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         kayttaja-tiemerkinta portti
                                         (slurp "test/resurssit/api/tiemerkinnan_aikataulun_kirjaus_viallinen_tiemerkinta_valmis_ilman_alkua.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Tiemerkintää ei voi merkitä valmiiksi, aloitus puuttuu."))))

(deftest aikataulun-kirjaus-vaatii-paallystys-valmis-jos-paallystys-aloitettu-annettu
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/aikataulun-kirjaus-vaatii-paallystys-valmis-jos-paallystys-aloitettu-annettu.json"))]
    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Kun annetaan päällystyksen aloitusaika, anna myös päällystyksen valmistumisen aika tai aika-arvio"))))

(deftest aikataulun-kirjaus-vaatii-tiemerkinta-valmis-jos-tiemerkinta-aloitettu-annettu
  (let [urakka (hae-oulun-tiemerkintaurakan-id)
        kohde (hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-tiemerkinta"]
                                         kayttaja-tiemerkinta portti
                                         (slurp "test/resurssit/api/aikataulun-kirjaus-vaatii-tiemerkinta-valmis-jos-tiemerkinta-aloitettu-annettu.json"))]

    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Kun annetaan tiemerkinnän aloitusaika, anna myös tiemerkinnän valmistumisen aika tai aika-arvio"))))

(deftest aikataulun-kirjaaminen-estaa-paivittamasta-urakkaan-kuulumatonta-kohdetta
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka)
        vastaus (api-tyokalut/post-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde "/aikataulu-paallystys"]
                                         kayttaja-paallystys portti
                                         (slurp "test/resurssit/api/paallystyksen_aikataulun_kirjaus.json"))]
    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "Ylläpitokohde ei kuulu urakkaan"))))

(deftest yllapitokohteen-paivitys-tiemerkintaurakkaan-ei-onnistu-paallystyskayttajana
  ;; Ylläpitokohteen päivitys voidaan tehdä vain päällystysurakkaan
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        kohde-id (hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa urakka-id)
        vastaus (api-tyokalut/put-kutsu ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id]
                                        kayttaja-paallystys portti
                                        (slurp "test/resurssit/api/paallystyskohteen-paivitys-request.json"))]
    (is (= 400 (:status vastaus)))))

(deftest yllapitokohteen-paivitys-tiemerkintaurakkaan-ei-onnistu-tiemerkintakayttajana
  ;; Ylläpitokohteen päivitys voidaan tehdä vain päällystysurakkaan
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        kohde-id (hae-yllapitokohde-jonka-tiemerkintaurakka-suorittaa urakka-id)
        vastaus (api-tyokalut/put-kutsu ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id]
                                        kayttaja-tiemerkinta portti
                                        (slurp "test/resurssit/api/paallystyskohteen-paivitys-request.json"))]
    (is (= 400 (:status vastaus)))))

(deftest avoimen-yllapitokohteen-paivittaminen-toimii
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde-id (hae-yllapitokohde-nakkilan-ramppi)
        ;; Testiä varten tuhoa kohteen POT
        _ (u "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id ";")
        payload (slurp "test/resurssit/api/paallystyskohteen-paivitys-request.json")
        vastaus (api-tyokalut/put-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id]
                                        kayttaja-paallystys portti
                                        payload)]
    (is (= 200 (:status vastaus)))

    (let [kohteen-tr-osoite (hae-yllapitokohteen-tr-osoite kohde-id)
          oletettu-tr-osoite {:aet 10
                              :ajorata 0
                              :aosa 10
                              :kaista 11
                              :loppuet 1
                              :losa 12
                              :numero 20}
          alikohteiden-tr-osoitteet (into #{} (hae-yllapitokohteen-kohdeosien-tr-osoitteet kohde-id))
          oletettu-ensimmaisen-alikohteen-tr-osoite {:aet 10
                                                     :ajorata 0
                                                     :aosa 10
                                                     :kaista 11
                                                     :loppuet 666
                                                     :losa 10
                                                     :numero 20}
          oletettu-toisen-alikohteen-tr-osoite {:aet 700
                                                :ajorata 0
                                                :aosa 10
                                                :kaista 11
                                                :loppuet 1
                                                :losa 12
                                                :numero 20}]
      (is (= oletettu-tr-osoite kohteen-tr-osoite) "Kohteen tierekisteriosoite on onnistuneesti päivitetty")
      (is (= 2 (count alikohteiden-tr-osoitteet)) "Alikohteita palautuu tallennettu määrä")
      (is (alikohteiden-tr-osoitteet oletettu-ensimmaisen-alikohteen-tr-osoite)
          "Ensimmäisen alikohteen tierekisteriosite on päivittynyt oikein")
      (is (alikohteiden-tr-osoitteet oletettu-toisen-alikohteen-tr-osoite)
          "Toisen alikohteen tierekisteriosite on päivittynyt oikein")

      (let [alikohteet (q-map (str "SELECT sijainti, tr_numero FROM yllapitokohdeosa WHERE yllapitokohde = " kohde-id))]
        (is (every? #(and (not (nil? (:sijainti %))) (not (nil? (:tr_numero %)))) alikohteet)
            "Kaikilla alikohteilla on sijainti & tienumero")))))

(deftest avoimen-yllapitokohteen-paivittaminen-paallekain-ei-onnistu
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde-id (ffirst (q "SELECT id FROM yllapitokohde WHERE nimi='Kirkkotie'"))
        payload (slurp "test/resurssit/api/paallystyskohteen-paivitys-paallekkain-request.json")
        vastaus (api-tyokalut/put-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id]
                                        kayttaja-paallystys portti
                                        payload)]
    (is (not= 200 (:status vastaus)))
    (is (str/includes? (:body vastaus) "{\"virheet\":[{\"virhe\":{\"koodi\":\"viallisia-tieosia\",\"viesti\":\"-----------\\nMuukohde\\nKohteenosa on päällekkäin osan \\\"Ouluntien kohdeosa\\\" kanssa\\nKohteenosa on päällekkäin toisen osan kanssa\\n\"}}]}"))))

(deftest avoimen-yllapitokohteen-paivittaminen-ilman-alikohteen-ajorataa-ja-kaistaa-ei-toimii
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        payload (slurp "test/resurssit/api/paallystyskohteen-paivitys-ilman-alikohteen-ajorataa-ja-kaistaa-request.json")
        {status :status} (api-tyokalut/put-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id]
                                                 kayttaja-paallystys portti
                                                 payload)]
    (is (= 400 status))))

(deftest paallystysilmoituksellisen-kohteen-paivitys-ei-onnistu
  (let [urakka (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)
        payload (slurp "test/resurssit/api/paallystyskohteen-paivitys-request.json")
        {status :status body :body} (api-tyokalut/put-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id]
                                                            kayttaja-paallystys portti
                                                            payload)]
    (is (= 400 status))
    (is (= "lukittu-yllapitokohde" (:koodi (:virhe (first (:virheet (cheshire/decode body true))))))
        "Virheelliselle kirjaukselle palautetaan oikea virhekoodi.")))

(deftest maaramuutosten-kirjaaminen-kohteelle-toimii
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
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
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-yllapitokohde-joka-ei-kuulu-urakkaan urakka-id)
        kutsudata (slurp "test/resurssit/api/maaramuutosten-kirjaus-request.json")
        polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/maaramuutokset"]
        vastaus (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata)]
    (is (= 400 (:status vastaus)))
    (is (str/includes? (:body vastaus) "tuntematon-yllapitokohde"))))

(deftest tarkastuksen-kirjaaminen-kohteelle-toimii
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
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
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        kohde-id (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)
        hae-tarkastukset #(q-map "SELECT * FROM tarkastus WHERE yllapitokohde =" kohde-id)
        tarkastukset-ennen-kirjausta (hae-tarkastukset)
        polku ["/api/urakat/" urakka-id "/yllapitokohteet/" kohde-id "/tarkastus"]
        kutsudata (slurp "test/resurssit/api/usean-yllapitokohteen-tarkastuksen-kirjaus-request.json")
        ;; Transaktion toiminnan testaaminen overridaamalla mapv funktion on vähän huono,
        ;; koska tämä nyt riippuu siitä, että harja.palvelin.integraatiot.api.kasittely.tarkastukset/luo-tai-paivita-tarkastukset
        ;; transaction sisällä käytetään mapv funktiota usean tarkastuksen tallentamiseen eikä mitään muuta looppia.
        vastaus (with-redefs [mapv (fn [annettu-fn args]
                                     (vec (map-indexed
                                            #(if (and (= (-> %2 :tarkastus :tunniste :id) 1337)
                                                      (= (-> %2 :tarkastus :tarkastaja :etunimi) "Taneli"))
                                               (throw (org.postgresql.util.PSQLException. "Foo" (org.postgresql.util.PSQLState/DATA_ERROR)))
                                               (annettu-fn %2))
                                            args)))]
                  (api-tyokalut/post-kutsu polku kayttaja-paallystys portti kutsudata))
        tarkastukset-kirjauksen-jalkeen (hae-tarkastukset)]
    (is (= 500 (:status vastaus)))
    (is (= (count tarkastukset-ennen-kirjausta) (count tarkastukset-kirjauksen-jalkeen)))))

(deftest tiemerkintatoteuman-kirjaaminen-kohteelle-toimii
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
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


(deftest osoitteiden-muunnos-vkmn-kanssa
  (let [urakka (hae-muhoksen-paallystysurakan-id)
        kohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)

        ;; Testiä varten tuhoa POT
        _ (u "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id ";")
        vkm-vastaus (slurp "test/resurssit/vkm/vkm-vastaus-alikohteiden-kanssa.txt")]
    (with-fake-http [+testi-vkm+ vkm-vastaus
                     #".*api\/urakat.*" :allow]
                    (let [payload (slurp "test/resurssit/api/toisen-paivan-verkon-paallystyskohteen-paivitys-request.json")
                          vastaus (api-tyokalut/put-kutsu ["/api/urakat/" urakka "/yllapitokohteet/" kohde-id]
                                                          kayttaja-paallystys portti
                                                          payload)]
                      (is (= 200 (:status vastaus)) "Kutsu tehtiin onnistuneesti")

                      (let [kohteen-tr-osoite (hae-yllapitokohteen-tr-osoite kohde-id)
                            oletettu-tr-osoite {:numero 20
                                                :aosa 1
                                                :aet 1
                                                :losa 4
                                                :loppuet 100
                                                :ajorata 1
                                                :kaista 11}
                            odotettu-1-alikohteen-osoite {:numero 20, :aosa 1, :aet 1, :losa 1, :loppuet 100, :kaista 11, :ajorata 1}
                            odotettu-2-alikohteen-osoite {:numero 20, :aosa 1, :aet 100, :losa 4, :loppuet 100, :kaista 11, :ajorata 1}
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
