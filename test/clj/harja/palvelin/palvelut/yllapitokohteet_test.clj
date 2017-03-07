(ns harja.palvelin.palvelut.yllapitokohteet-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet :refer :all]
            [harja.testi :refer :all]
            [clojure.core.match :refer [match]]
            [harja.jms-test :refer [feikki-sonja]]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clojure.java.io :as io]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.palvelut.yllapitokohteet :as yllapitokohteet])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :sonja (feikki-sonja)
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-sisaan-kuittausjono "email-to-harja-ack"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :http-palvelin (testi-http-palvelin)
                        :yllapitokohteet (component/using
                                           (yllapitokohteet/->Yllapitokohteet)
                                           [:http-palvelin :db :fim :sonja-sahkoposti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(def yllapitokohde-testidata {:kohdenumero 999
                              :nimi "Testiramppi4564ddf"
                              :yllapitokohdetyotyyppi :paallystys
                              :sopimuksen_mukaiset_tyot 400
                              :bitumi_indeksi 123
                              :kaasuindeksi 123})

(def yllapitokohdeosa-testidata {:nimi "Testiosa123456"
                                 :tr-numero 20
                                 :tr-alkuosa 1
                                 :tr-alkuetaisyys 1
                                 :tr-loppuosa 2
                                 :tr-loppuetaisyys 2
                                 :kvl 4
                                 :nykyinen_paallyste 2
                                 :toimenpide "Ei tehdä mitään"})

(defn yllapitokohde-id-jolla-on-paallystysilmoitus []
  (ffirst (q (str "SELECT yllapitokohde.id as paallystyskohde_id
                   FROM yllapitokohde
                   JOIN paallystysilmoitus ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                   WHERE urakka = " (hae-muhoksen-paallystysurakan-id) " AND sopimus = " (hae-muhoksen-paallystysurakan-paasopimuksen-id) ";"))))


(deftest paallystyskohteet-haettu-oikein
  (let [kohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                :urakan-yllapitokohteet +kayttaja-jvh+
                                {:urakka-id @muhoksen-paallystysurakan-id
                                 :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")")))
        leppajarven-ramppi (first (filter #(= (:nimi %) "Leppäjärven ramppi")
                                          kohteet))]
    (is (= (count kohteet) kohteiden-lkm) "Päällystyskohteiden määrä")
    (is (== (:maaramuutokset leppajarven-ramppi) 205)
        "Leppäjärven rampin määrämuutos laskettu oikein")))

(deftest paallystysurakan-aikatauluhaku-toimii
  (let [urakan-yllapitokohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :urakan-yllapitokohteet +kayttaja-jvh+
                                               {:urakka-id @muhoksen-paallystysurakan-id
                                                :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                                                :vuosi 2017})
        aikataulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                  {:urakka-id @muhoksen-paallystysurakan-id
                                   :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                                   :vuosi 2017})
        leppajarven-ramppi (first (filter #(= (:nimi %) "Leppäjärven ramppi") aikataulu))
        muut-kohteet (filter #(not= (:nimi %) "Leppäjärven ramppi") aikataulu)]
    (is (= (count urakan-yllapitokohteet) (count aikataulu))
        "Jokaiselle kohteelle saatiin haettua aikataulu")
    (is (false? (:tiemerkintaurakan-voi-vaihtaa? leppajarven-ramppi))
        "Leppäjärven rampilla on kirjauksia, ei saa vaihtaa suorittavaa tiemerkintäurakkaa")
    (is (every? true? (map :tiemerkintaurakan-voi-vaihtaa? muut-kohteet))
        "Muiden kohteiden tiemerkinnän suorittaja voidaan vaihtaa")))

(deftest tiemerkintaurakan-aikatauluhaku-toimii
  (let [aikataulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                  {:urakka-id (hae-oulun-tiemerkintaurakan-id)
                                   :sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
                                   :vuosi 2017})]
    (is (= (count aikataulu) 3) "Löytyi kaikki tiemerkintäurakalle osoitetut ylläpitokohteet")
    (is (not-any? #(contains? % :suorittava-tiemerkintaurakka) aikataulu)
        "Tiemerkinnän aikataulu ei sisällä päällystysurakkaan liittyvää tietoa")))

(deftest paallystyskohteet-haettu-oikein-vuodelle-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :urakan-yllapitokohteet +kayttaja-jvh+
                                {:urakka-id @muhoksen-paallystysurakan-id
                                 :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                                 :vuosi 2017})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")
                                      AND vuodet @> ARRAY[2017]::int[]")))
        ei-yha-kohde (first (filter #(= (:nimi %) "Ei YHA-kohde") vastaus))
        muut-kohteet (filter #(not= (:nimi %) "Ei YHA-kohde") vastaus)]
    (is (> (count vastaus) 0) "Päällystyskohteita löytyi")
    (is (= (count vastaus) kohteiden-lkm) "Löytyi oikea määrä kohteita")
    (is (true? (:yllapitokohteen-voi-poistaa? ei-yha-kohde))
        "Ei YHA -kohteen saa poistaa (ei ole mitään kirjauksia)")
    (is (every? false? (map :yllapitokohteen-voi-poistaa? muut-kohteet))
        "Muita kohteita ei saa poistaa (sisältävät kirjauksia)")))

(deftest paallystyskohteet-haettu-oikein-vuodelle-2016
  (let [res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-yllapitokohteet +kayttaja-jvh+
                            {:urakka-id @muhoksen-paallystysurakan-id
                             :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                             :vuosi 2016})]
    (is (= (count res) 0) "Ei päällystyskohteita vuodelle 2016")))

(deftest tallenna-paallystyskohde-kantaan
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        urakan-geometria-ennen-muutosta (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :sopimus-id sopimus-id
                                                              :kohteet [yllapitokohde-testidata]})
    (let [maara-lisayksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
          kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :urakan-yllapitokohteet
                                           +kayttaja-jvh+ {:urakka-id urakka-id
                                                           :sopimus-id sopimus-id})
          urakan-geometria-lisayksen-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]
      (log/debug "Kohteet kannassa: " (pr-str kohteet-kannassa))
      (is (not (nil? kohteet-kannassa)))
      (is (not= urakan-geometria-ennen-muutosta urakan-geometria-lisayksen-jalkeen "Urakan geometria päivittyi"))
      (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))

      ;; Edelleen jos ylläpitokohde poistetaan, niin myös geometria päivittyy
      (let [lisatty-kohde (first (filter #(= (:nimi %) "Testiramppi4564ddf") kohteet-kannassa))
            _ (is lisatty-kohde "Lisätty kohde löytyi vastauksesta")
            lisatty-kohde (assoc lisatty-kohde :poistettu true)
            kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                       :sopimus-id sopimus-id
                                                                                       :kohteet [lisatty-kohde]})
            poistettu-kohde (first (filter #(= (:nimi %) "Testiramppi4564ddf") kohteet-kannassa))
            urakan-geometria-poiston-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]

        (is (nil? poistettu-kohde) "Poistettua kohdetta ei ole enää vastauksessa")
        (is (not= urakan-geometria-lisayksen-jalkeen urakan-geometria-poiston-jalkeen "Geometria päivittyi")))

      ;; Siivoa sotkut
      (u (str "DELETE FROM yllapitokohde WHERE nimi = 'Testiramppi4564ddf';")))))

(deftest tallenna-paallystyskohde-kantaan-vuodelle-2015
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :sopimus-id sopimus-id
                                                              :vuosi 2015
                                                              :kohteet [yllapitokohde-testidata]})
    (let [kohteet-kannassa (ffirst (q
                                     (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")
                                      AND vuodet @> ARRAY[2015]::int[]")))]
      (is (= kohteet-kannassa 1) "Kohde tallentui oikein")
      (u (str "DELETE FROM yllapitokohde WHERE nimi = 'Testiramppi4564ddf';")))))

(deftest ala-poista-paallystyskohdetta-jolla-ilmoitus
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        maara-ennen-testia (ffirst (q
                                     (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        kohteet-ennen-testia (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :urakan-yllapitokohteet +kayttaja-jvh+
                                             {:urakka-id @muhoksen-paallystysurakan-id
                                              :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id})
        kohde-jolla-ilmoitus (first (filter :paallystysilmoitus-id kohteet-ennen-testia))
        paivitetyt-kohteet (map
                             (fn [kohde] (if (= (:id kohde) (:id kohde-jolla-ilmoitus))
                                           (assoc kohde :poistettu true)
                                           kohde))
                             kohteet-ennen-testia)]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :sopimus-id sopimus-id
                                                              :kohteet paivitetyt-kohteet})
    (let [maara-testin-jalkeen (ffirst (q
                                         (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
          kohteet-testin-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :urakan-yllapitokohteet
                                                 +kayttaja-jvh+ {:urakka-id urakka-id
                                                                 :sopimus-id sopimus-id})]
      (is (= maara-ennen-testia maara-testin-jalkeen))
      (is (= kohteet-testin-jalkeen kohteet-ennen-testia)))))

(deftest tallenna-yllapitokohdeosa-kantaan
  (let [yllapitokohde-id (yllapitokohde-id-jolla-on-paallystysilmoitus)]
    (is (not (nil? yllapitokohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          urakan-geometria-ennen-muutosta (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))
          maara-ennen-lisaysta (ffirst (q
                                         (str "SELECT count(*) FROM yllapitokohdeosa
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = yllapitokohdeosa.yllapitokohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-yllapitokohdeosat +kayttaja-jvh+ {:urakka-id urakka-id
                                                                  :sopimus-id sopimus-id
                                                                  :yllapitokohde-id yllapitokohde-id
                                                                  :osat [yllapitokohdeosa-testidata]})
      (let [maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*) FROM yllapitokohdeosa
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = yllapitokohdeosa.yllapitokohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))
            kohdeosat-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :yllapitokohteen-yllapitokohdeosat
                                               +kayttaja-jvh+ {:urakka-id urakka-id
                                                               :sopimus-id sopimus-id
                                                               :yllapitokohde-id yllapitokohde-id})
            urakan-geometria-muutoksen-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]
        (log/debug "Kohdeosa kannassa: " (pr-str kohdeosat-kannassa))
        (is (not (nil? kohdeosat-kannassa)))
        (is (every? :sijainti kohdeosat-kannassa) "Geometria muodostettiin")
        (is (not= urakan-geometria-ennen-muutosta urakan-geometria-muutoksen-jalkeen "Urakan geometria päivittyi"))
        (is (match (first kohdeosat-kannassa)
                   {:tr-kaista nil
                    :sijainti _
                    :tr-ajorata nil
                    :tr-loppuosa 2
                    :tunnus nil
                    :tr-alkuosa 1
                    :tr-loppuetaisyys 2
                    :nimi "Testiosa123456"
                    :id _
                    :tr-alkuetaisyys 1
                    :tr-numero 20
                    :toimenpide "Ei tehdä mitään"}
                   true))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))
        (u (str "DELETE FROM yllapitokohdeosa WHERE nimi = 'Testiosa123456';"))))))

(deftest tallenna-paallystysurakan-aikataulut
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        vuosi 2017
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        kohteet [{:kohdenumero "L03"
                  :aikataulu-paallystys-alku (pvm/->pvm-aika "19.5.2017 12:00")
                  :aikataulu-muokkaaja (:id +kayttaja-jvh+)
                  :urakka urakka-id
                  :aikataulu-kohde-valmis (pvm/->pvm "29.5.2017")
                  :nimi "Leppäjärven ramppi",
                  :valmis-tiemerkintaan (pvm/->pvm-aika "23.5.2017 12:00")
                  :aikataulu-paallystys-loppu (pvm/->pvm-aika "20.5.2017 12:00"),
                  :id yllapitokohde-id
                  :sopimus sopimus-id
                  :aikataulu-muokattu (pvm/->pvm-aika "29.5.2017 12:00")
                  :aikataulu-tiemerkinta-takaraja (pvm/->pvm "1.6.2017")
                  :aikataulu-tiemerkinta-alku nil,
                  :aikataulu-tiemerkinta-loppu (pvm/->pvm "26.5.2017")}]
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-yllapitokohteiden-aikataulu
                                +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :sopimus-id sopimus-id
                                 :vuosi vuosi
                                 :kohteet kohteet})
        maara-paivityksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        vastaus-leppajarven-ramppi (first (filter #(= "Leppäjärven ramppi" (:nimi %)) vastaus))
        odotettu {:aikataulu-kohde-valmis (pvm/->pvm "29.5.2017")
                  :aikataulu-muokkaaja (:id +kayttaja-jvh+)
                  :aikataulu-paallystys-alku (pvm/->pvm-aika "19.5.2017 12:00")
                  :aikataulu-paallystys-loppu (pvm/->pvm-aika "20.5.2017 12:00")
                  :aikataulu-tiemerkinta-takaraja (pvm/->pvm "1.6.2017")
                  :aikataulu-tiemerkinta-alku (pvm/->pvm-aika "22.5.2017 00:00")
                  :aikataulu-tiemerkinta-loppu (pvm/->pvm-aika "23.5.2017 00:00")
                  :id yllapitokohde-id
                  :kohdenumero "L03"
                  :nimi "Leppäjärven ramppi"
                  :sopimus sopimus-id
                  :urakka urakka-id
                  :valmis-tiemerkintaan (pvm/->pvm-aika "23.5.2017 12:00")}]
    (is (= maara-ennen-lisaysta maara-paivityksen-jalkeen (count vastaus)))
    (is (= (:aikataulu-paallystys-alku odotettu) (:aikataulu-paallystys-alku vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-paallystys-alku")
    (is (= (:aikataulu-paallystys-loppu odotettu) (:aikataulu-paallystys-loppu vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-paallystys-loppu")
    (is (= (:aikataulu-tiemerkinta-takaraja odotettu) (:aikataulu-tiemerkinta-takaraja vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-tiemerkinta-takaraja")
    (is (= (:aikataulu-tiemerkinta-alku odotettu) (:aikataulu-tiemerkinta-alku vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-tiemerkinta-alku")
    (is (= (:aikataulu-tiemerkinta-loppu odotettu) (:aikataulu-tiemerkinta-loppu vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-tiemerkinta-loppu")
    (is (= (:aikataulu-kohde-valmis odotettu) (:aikataulu-kohde-valmis vastaus-leppajarven-ramppi)) "päällystyskohteen :aikataulu-kohde-valmis")))

(deftest paallystyksen-merkitseminen-valmiiksi-toimii
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        yllapitokohde-id (hae-yllapitokohde-oulaisten-ohitusramppi-jolla-ei-aikataulutietoja)
        suorittava-tiemerkintaurakka-id (hae-lapin-tiemerkintaurakan-id)
        sahkoposti-valitetty (atom false)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-lapin-tiemerkintaurakan-kayttajat.xml"))
        vuosi 2017]

    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))

    (with-fake-http
      [+testi-fim+ fim-vastaus]

      ;; Lisätään kohteelle ensin päällystyksen aikataulutiedot ja suorittava tiemerkintäurakka
      (let [tiemerkintapvm (pvm/->pvm-aika "23.5.2017 12:00")
            _ (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-yllapitokohteiden-aikataulu
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :sopimus-id sopimus-id
                               :vuosi vuosi
                               :kohteet [{:id yllapitokohde-id
                                          :suorittava-tiemerkintaurakka suorittava-tiemerkintaurakka-id
                                          :aikataulu-paallystys-alku (pvm/->pvm-aika "19.5.2017 12:00")
                                          :aikataulu-paallystys-loppu (pvm/->pvm-aika "20.5.2017 12:00")}]})
            ;; Merkitään kohde valmiiksi tiemerkintään
            aikataulu-ennen-testia (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                                   {:urakka-id urakka-id
                                                    :sopimus-id sopimus-id
                                                    :vuosi vuosi})
            oulaisten-ohitusramppi-ennen-testia (first (filter #(= (:nimi %) "Oulaisten ohitusramppi")
                                                         aikataulu-ennen-testia))
            muut-kohteet-ennen-testia (first (filter #(not= (:nimi %) "Oulaisten ohitusramppi")
                                                     aikataulu-ennen-testia))
            vastaus-kun-merkittu-valmiiksi (kutsu-palvelua (:http-palvelin jarjestelma)
                                                           :merkitse-kohde-valmiiksi-tiemerkintaan +kayttaja-jvh+
                                                           {:tiemerkintapvm tiemerkintapvm
                                                            :kohde-id yllapitokohde-id
                                                            :urakka-id urakka-id
                                                            :sopimus-id sopimus-id
                                                            :vuosi vuosi})
            oulaisten-ohitusramppi-testin-jalkeen (first (filter #(= (:nimi %) "Oulaisten ohitusramppi")
                                                                 vastaus-kun-merkittu-valmiiksi))
            muut-kohteet-testin-jalkeen (first (filter #(not= (:nimi %) "Oulaisten ohitusramppi")
                                                       vastaus-kun-merkittu-valmiiksi))]

        (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 5000)
        (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")

        ;; Valmiiksi merkitsemisen jälkeen tilanne on sama kuin ennen merkintää, sillä erotuksella, että
        ;; valittu kohde merkittiin valmiiksi tiemerkintään
        (is (= muut-kohteet-ennen-testia muut-kohteet-testin-jalkeen))
        (is (= (dissoc oulaisten-ohitusramppi-ennen-testia
                       :aikataulu-tiemerkinta-takaraja
                       :tiemerkintaurakan-voi-vaihtaa?)
               (dissoc oulaisten-ohitusramppi-ennen-testia
                       :aikataulu-tiemerkinta-takaraja
                       :tiemerkintaurakan-voi-vaihtaa?)))
        (is (nil? (:aikataulu-tiemerkinta-takaraja oulaisten-ohitusramppi-ennen-testia)))
        (is (nil? (:valmis-tiemerkintaan oulaisten-ohitusramppi-ennen-testia)))
        (is (some? (:aikataulu-tiemerkinta-takaraja oulaisten-ohitusramppi-testin-jalkeen)))
        (is (some? (:valmis-tiemerkintaan oulaisten-ohitusramppi-testin-jalkeen)))))))

(deftest yllapitokohteen-suorittavan-tiemerkintaurakan-vaihto-ei-toimi-jos-kirjauksia
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        lapin-urakka-id (hae-lapin-tiemerkintaurakan-id)
        vuosi 2017
        yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        kohteet [{:urakka urakka-id
                  :id yllapitokohde-id
                  :sopimus sopimus-id
                  :aikataulu-paallystys-alku (pvm/->pvm-aika "19.5.2017 12:00")
                  :aikataulu-kohde-valmis (pvm/->pvm "29.5.2017")
                  :suorittava-tiemerkintaurakka lapin-urakka-id
                  :valmis-tiemerkintaan (pvm/->pvm-aika "23.5.2017 12:00")
                  :aikataulu-paallystys-loppu (pvm/->pvm-aika "20.5.2017 12:00"),
                  :aikataulu-tiemerkinta-takaraja (pvm/->pvm "1.6.2017")
                  :aikataulu-tiemerkinta-alku nil,
                  :aikataulu-tiemerkinta-loppu (pvm/->pvm "26.5.2017")}]
        aiempi-aikataulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                         {:urakka-id @muhoksen-paallystysurakan-id
                                          :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
                                          :vuosi vuosi})
        aiempi-aikataulu-leppajarven-ramppi (first (filter #(= (:nimi %) "Leppäjärven ramppi") aiempi-aikataulu))
        nykyinen-aikataulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-yllapitokohteiden-aikataulu
                                           +kayttaja-jvh+
                                           {:urakka-id urakka-id
                                            :sopimus-id sopimus-id
                                            :vuosi vuosi
                                            :kohteet kohteet})
        nykyinen-aikataulu-leppajarven-ramppi (first (filter #(= (:nimi %) "Leppäjärven ramppi") nykyinen-aikataulu))]
    (is (not= (:suorittava-tiemerkintaurakka nykyinen-aikataulu-leppajarven-ramppi) lapin-urakka-id)
        "Suorittavaa tiemerkintäurakkaa ei vaihdettu, koska tiemerkintäurakasta on tehty kirjauksia kohteelle")
    (is (= (:suorittava-tiemerkintaurakka aiempi-aikataulu-leppajarven-ramppi)
           (:suorittava-tiemerkintaurakka nykyinen-aikataulu-leppajarven-ramppi))
        "Leppäjärven rampin suorittava tiemerkintäurakka on edelleen sama")))

(deftest testidatassa-validit-kohteet
  ;; On kiva jos meidän oma testidata on validia
  (let [kohteet (q "SELECT
                   ypko.tr_numero,
                   ypko.tr_alkuosa,
                   ypko.tr_alkuetaisyys,
                   ypko.tr_loppuosa,
                   ypko.tr_loppuetaisyys,
                   ypk.tr_numero as kohde_tr_numero,
                   ypk.tr_alkuosa as kohde_tr_alkuosa,
                   ypk.tr_alkuetaisyys as kohde_tr_alkuetaisyys,
                   ypk.tr_loppuosa as kohde_tr_loppuosa,
                   ypk.tr_loppuetaisyys as kohde_tr_loppuetaisyys
                   FROM yllapitokohdeosa ypko
                   LEFT JOIN yllapitokohde ypk ON ypko.yllapitokohde = ypk.id;")]
    (doseq [kohde kohteet]
      (is (= (get kohde 0) (get kohde 5)) "Alikohteen tienumero on sama kuin pääkohteella")
      (is (and (>= (get kohde 1) (get kohde 6))
               (<= (get kohde 1) (get kohde 8))) "Alikohde on kohteen sisällä")
      (is (and (>= (get kohde 3) (get kohde 6))
               (<= (get kohde 3) (get kohde 8))) "Alikohde on kohteen sisällä"))))
