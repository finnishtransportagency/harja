(ns harja.palvelin.palvelut.tiemerkintojen-kustannus-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [dk.ative.docjure.spreadsheet :as xls]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]
            [harja.palvelin.palvelut.kustannusten-kirjaus :as tiemerkkarit]
            [harja.palvelin.palvelut.yllapitokohteet.tiemerkinta-kustannukset-excel :as tm-excel])
  (:import (org.apache.poi.xssf.usermodel XSSFWorkbook)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :tiemerkinta-korjauskustannus (component/using
                                          (tiemerkkarit/->TiemerkinnanKustannusKirjaukset)
                                          [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each jarjestelma-fixture)

(deftest hae-tiemerkinta-kustannuskirjaukset-toimii
  (testing "Tiemerkintä-kustannuskirjausten hakeminen toimii"
    (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
          urakka (first (q-map "SELECT nimi, id, alkupvm, loppupvm FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2017-2024'"))
          kustannusvuosi 2022
          kustannus-summa 25000.0

          ;; Siivotaan vanhat kustannukset jos niitä on
          _ (u (format "DELETE FROM tiemerkinta_korjauskustannus WHERE urakka = %s AND kustannusvuosi = %s"
                 urakka-id kustannusvuosi))

          ;; Lisätään testidataa
          _ (i (format "INSERT INTO tiemerkinta_korjauskustannus (urakka, kustannusvuosi, kustannus, pk1, pk2, pk3, luoja, luotu, muokkaaja, muokattu) 
                        VALUES 
                        (%s, %s, %s, 25.0, 35.0, 40.0, 1, now(), 1, now())"
                 urakka-id kustannusvuosi kustannus-summa))

          ;; Haetaan kustannukset
          kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                         :hae-tiemerkinta-kustannuskirjaus
                         +kayttaja-jvh+
                         {:urakka urakka})
          
          ;; Suodatetaan meitä kiinnostavat rivit
          kustannus-rivi (first (filter #(= (:kustannusvuosi %) kustannusvuosi) kustannukset))
          urakan-kaikki-vuodet (range (pvm/vuosi (:alkupvm urakka))
                                 (+ (pvm/vuosi (:loppupvm urakka)) 1))]

      ;; Tarkistetaan että hakutulos ei ole tyhja
      (is (not (empty? kustannukset)) "Kustannuksia löytyy")

      ;; Tarkistetaan että lisätty kustannus löytyy
      (is (= (:urakka kustannus-rivi) urakka-id) "Oikea urakka")
      (is (= (:kustannusvuosi kustannus-rivi) kustannusvuosi) "Oikea vuosi")
      (is (= (:kustannus kustannus-rivi) kustannus-summa) "Oikea kustannus")
      (is (= (:pk1 kustannus-rivi) 25.0) "Oikea pk1")
      (is (= (:pk2 kustannus-rivi) 35.0) "Oikea pk2")
      (is (= (:pk3 kustannus-rivi) 40.0) "Oikea pk3")

      ;; Tarkistetaan että kaikille vuosille on rivit
      (is (= (count kustannukset) (count urakan-kaikki-vuodet))
        "Kaikkien vuosien rivit löytyvät (myös defaultit)")

      ;; Tarkistetaan että default-rivit ovat nolla-arvoisia
      (let [default-rivi (first (filter #(and
                                           (= (:kustannusvuosi %) (+ kustannusvuosi 1))
                                           (not= kustannusvuosi (+ kustannusvuosi 1)))
                                  kustannukset))]
        (when default-rivi
          (is (= (:kustannus default-rivi) 0) "Default-kustannus on nolla")
          (is (= (:pk1 default-rivi) 0) "Default-pk1 on nolla")
          (is (= (:pk2 default-rivi) 0) "Default-pk2 on nolla")
          (is (= (:pk3 default-rivi) 0) "Default-pk3 on nolla")))

      ;; Siivotaan testidatat
      (u (format "DELETE FROM tiemerkinta_korjauskustannus WHERE urakka = %s AND kustannusvuosi = %s"
           urakka-id kustannusvuosi)))))

(deftest tallennus-paivitys-toimii
  (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
        urakka (first (q-map "SELECT nimi, id, alkupvm, loppupvm FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2017-2024'"))
        kustannusvuosi 2020
        kustannus-tallennus 123456
        kustannus-paivitys 654321
        params (conj [] {:urakka urakka-id
                         :muokkaaja 3
                         :kustannusvuosi kustannusvuosi
                         :kustannus kustannus-tallennus
                         :pk1 25.0M
                         :pk2 15.0M
                         :pk3 60.0M})


        ;;lisää uuden kustannuksen
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
            {:urakka urakka :tiedot params})

        tallennuksen-jalkeen (kutsu-palvelua
                               (:http-palvelin jarjestelma)
                               :hae-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
                               {:urakka urakka})





        kustannust (into {} (filter #(= (:kustannusvuosi %) kustannusvuosi) tallennuksen-jalkeen))
        _ (is (= (:urakka kustannust) urakka-id))
        _ (is (= (int (:kustannus kustannust)) kustannus-tallennus))

        ;;päivittää aiemmin lisättyä kustannusta
        paivita-params (conj [] (assoc (first params) :kustannus kustannus-paivitys))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
            {:urakka urakka :tiedot paivita-params})
        paivityksen-jalkeen (kutsu-palvelua
                              (:http-palvelin jarjestelma)
                              :hae-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
                              {:urakka urakka})
        kustannusp (into {} (filter #(= (:kustannusvuosi %) kustannusvuosi) paivityksen-jalkeen))
        _ (is (= (:urakka kustannusp) urakka-id))
        _ (is (= (int (:kustannus kustannusp)) kustannus-paivitys))]
    (u (str "DELETE FROM tiemerkinta_korjauskustannus WHERE urakka = 12 AND kustannusvuosi = " kustannusvuosi))))

(deftest rivin-nollaus-toimii
  (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
        urakka (first (q-map "SELECT nimi, id, alkupvm, loppupvm FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2017-2024'"))
        kustannusvuosi 2021
        kustannus-tallennus 20000
        params (conj [] {:urakka urakka-id
                         :muokkaaja 3
                         :kustannusvuosi kustannusvuosi
                         :kustannus kustannus-tallennus
                         :pk1 25.0M
                         :pk2 25.0M
                         :pk3 50.0M})
        nollaa-params (conj [] {:urakka urakka-id
                         :muokkaaja 3
                         :kustannusvuosi kustannusvuosi
                         :kustannus 0
                         :pk1 0.0M
                         :pk2 0.0M
                         :pk3 0.0M})


        ;;lisää uuden kustannuksen
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
            {:urakka urakka :tiedot params})

        tallennuksen-jalkeen (kutsu-palvelua
                                (:http-palvelin jarjestelma)
                                :hae-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
                                {:urakka urakka})

        kustannus (into {} (filter #(= (:kustannusvuosi %) kustannusvuosi) tallennuksen-jalkeen))
        _ (is (= (:urakka kustannus) urakka-id))
        _ (is (= (int (:kustannus kustannus)) kustannus-tallennus))


        ;;nollaa aiempi kustannus
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
            {:urakka urakka :tiedot nollaa-params})

        nollauksen-jalkeen (kutsu-palvelua
                               (:http-palvelin jarjestelma)
                               :hae-tiemerkinta-kustannuskirjaus +kayttaja-jvh+
                               {:urakka urakka})

        kustannus-nollauksen-jalkeen (into {} (filter #(= (:kustannusvuosi %) kustannusvuosi) nollauksen-jalkeen))
        _ (is (= (bigdec (:kustannus kustannus-nollauksen-jalkeen)) 0.0M))]
  (u (str "DELETE FROM tiemerkinta_korjauskustannus WHERE urakka = 12 AND kustannusvuosi = " kustannusvuosi))))

(deftest hae-tiemerkinta-paallystyskohteiden-kustannukset-toimii
  (testing "Päällystyskohteiden kustannusten hakeminen toimii oikein"
    (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
          urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
          kohdenumero "123"
          urakka (first (q-map "SELECT nimi, id, alkupvm, loppupvm FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2017-2024'"))
          yllapitokohde-id (i (format "INSERT INTO yllapitokohde 
                               (nimi, urakka, sopimus, tr_numero, tr_alkuosa, tr_alkuetaisyys, 
                               tr_loppuosa, tr_loppuetaisyys,
                               yllapitokohdetyyppi, yllapitokohdetyotyyppi, 
                               yotyo, vuodet, kohdenumero, suorittava_tiemerkintaurakka) 
                               VALUES 
                               ('Testikohde', %s, 
                               (SELECT id FROM sopimus WHERE urakka = %s LIMIT 1), 
                               4, 1, 0, 1, 100, 
                               'paallyste'::YLLAPITOKOHDETYYPPI, 
                               'paallystys'::YLLAPITOKOHDETYOTYYPPI,
                               false, '{%s}'::INTEGER[], %s, %s) " urakka-id urakka-id nykyinen-vuosi kohdenumero urakka-id))


          ;; Lisää kustannus
          _ (i (format "INSERT INTO tiemerkinta_yllapitokohteen_kustannus 
               (yllapitokohde, linjamerkinnat, pienmerkinnat, jyrsinnat, muut_kustannukset, luoja, luotu) 
               VALUES 
               (%s, 1000, 500, 300, 400, 1, now())"
                 yllapitokohde-id))

          ;; Hae kustannukset
          kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                         :hae-tiemerkinta-paallystyskohteiden-kustannukset
                         +kayttaja-jvh+
                         {:urakka-id urakka-id :urakka-alkupvm (:loppupvm urakka)})]

      ;; Tarkista että tulos on oikein
      (is (seq kustannukset) "Kustannuksia löytyy")
      (is (some #(= (:kohdenumero %) kohdenumero) kustannukset)
        "Lisätty kohde löytyy tuloksista")
      (let [kohde (first (filter #(= (:kohdenumero %) kohdenumero) kustannukset))]
        (is (= (:linjamerkinnat kohde) 1000.00M))
        (is (= (:pienmerkinnat kohde) 500.00M))
        (is (= (:jyrsinnat kohde) 300.00M))
        (is (= (:muut-kustannukset kohde) 400.00M)))

      ;; Siivoa testidatat
      (u (format "DELETE FROM tiemerkinta_yllapitokohteen_kustannus WHERE yllapitokohde = %s"
           yllapitokohde-id))
      (u (format "DELETE FROM yllapitokohde WHERE id = %s"
           yllapitokohde-id)))))

(deftest tallenna-tiemerkinta-yllapitokohteiden-kustannukset-toimii
  (testing "Tiemerkintä-ylläpitokohteiden kustannusten tallennus toimii"
    (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
          urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
          kohdenumero "123"
          urakka (first (q-map "SELECT nimi, id, alkupvm, loppupvm FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2017-2024'"))

          ;; Luo ylläpitokohde
          yllapitokohde-id (i (format "INSERT INTO yllapitokohde
                                     (nimi, urakka, sopimus, tr_numero, tr_alkuosa, tr_alkuetaisyys, 
                                      tr_loppuosa, tr_loppuetaisyys, 
                                      yllapitokohdetyyppi, yllapitokohdetyotyyppi,
                                      yotyo, vuodet, kohdenumero, suorittava_tiemerkintaurakka) 
                                     VALUES 
                                     ('Testikohde tallennusta varten', %s, 
                                      (SELECT id FROM sopimus WHERE urakka = %s LIMIT 1), 
                                      4, 1, 0, 1, 100, 
                                      'paallyste'::YLLAPITOKOHDETYYPPI, 
                                      'paallystys'::YLLAPITOKOHDETYOTYYPPI,
                                      false, '{%s}'::INTEGER[], %s, %s)
                                     RETURNING id" urakka-id urakka-id nykyinen-vuosi kohdenumero urakka-id))

          ;; Tallenna kustannukset
          kustannustiedot {:tiedot [{:id yllapitokohde-id
                                     :linjamerkinnat 1200.50M
                                     :pienmerkinnat 600.75M
                                     :jyrsinnat 350.25M
                                     :muut-kustannukset 320.25M}]}

          tallennustulos (kutsu-palvelua (:http-palvelin jarjestelma)
                           :tallenna-tiemerkinta-yllapitokohteiden-kustannukset
                           +kayttaja-jvh+
                           kustannustiedot)

          ;; Hae kustannukset ja tarkista että tallennus onnistui
          kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                         :hae-tiemerkinta-paallystyskohteiden-kustannukset
                         +kayttaja-jvh+
                         {:urakka-id urakka-id :urakka-alkupvm (:loppupvm urakka)})

          kohde (first (filter #(= (:id %) yllapitokohde-id) kustannukset))]

      ;; Tarkista tallennuksen tulos
      (is (:onnistui tallennustulos) "Tallennus onnistui")
      (is (= 1 (count (:paivitetyt tallennustulos))) "Yksi kohde päivitettiin")

      ;; Tarkista että kustannukset löytyvät hakemalla
      (is (seq kustannukset) "Kustannuksia löytyy")
      (is (some #(= (:id %) yllapitokohde-id) kustannukset) "Lisätty kohde löytyy tuloksista")

      (is (= (:linjamerkinnat kohde) 1200.50M) "Linjamerkintöjen summa täsmää")
      (is (= (:pienmerkinnat kohde) 600.75M) "Pienmerkintöjen summa täsmää")
      (is (= (:jyrsinnat kohde) 350.25M) "Jyrsintöjen summa täsmää")
      (is (= (:muut-kustannukset kohde) 320.25M) "Muut kustannukset summa täsmää")

      ;; Kokeile päivitystä muuttamalla arvoja
      (let [paivitystiedot {:tiedot [{:id yllapitokohde-id
                                      :linjamerkinnat 2000.00M
                                      :pienmerkinnat 1000.00M
                                      :jyrsinnat 500.00M
                                      :muut-kustannukset 520.25M}]}

            paivitystulos (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-tiemerkinta-yllapitokohteiden-kustannukset
                            +kayttaja-jvh+
                            paivitystiedot)

            paivitetyt-kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-tiemerkinta-paallystyskohteiden-kustannukset
                                      +kayttaja-jvh+
                                      {:urakka-id urakka-id :urakka-alkupvm (:loppupvm urakka)})

            paivitetty-kohde (first (filter #(= (:id %) yllapitokohde-id) paivitetyt-kustannukset))]

        ;; Tarkista päivityksen tulos
        (is (:onnistui paivitystulos) "Päivitys onnistui")
        (is (= 1 (count (:paivitetyt paivitystulos))) "Yksi kohde päivitettiin")

        ;; Tarkista että päivitetyt kustannukset löytyvät hakemalla
        (is (not (empty? paivitetyt-kustannukset)) "Päivitettyjä kustannuksia löytyy")
        (is (some #(= (:id %) yllapitokohde-id) paivitetyt-kustannukset) "Päivitetty kohde löytyy tuloksista")

        (is (= (:linjamerkinnat paivitetty-kohde) 2000.00M) "Päivitetty linjamerkintöjen summa täsmää")
        (is (= (:pienmerkinnat paivitetty-kohde) 1000.00M) "Päivitetty pienmerkintöjen summa täsmää")
        (is (= (:jyrsinnat paivitetty-kohde) 500.00M) "Päivitetty jyrsintöjen summa täsmää")
        (is (= (:muut-kustannukset paivitetty-kohde) 520.25M) "Päivitetty Muut kustannukset summa täsmää"))

      ;; Siivoa testidatat
      (u (format "DELETE FROM tiemerkinta_yllapitokohteen_kustannus WHERE yllapitokohde = %s"
           yllapitokohde-id))
      (u (format "DELETE FROM yllapitokohde WHERE id = %s"
           yllapitokohde-id)))))


(deftest hae-tiemerkinta-paikkausten-kustannukset-toimii
  (testing "Paikkauskohteiden kustannusten hakeminen toimii oikein"
    (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
          ulkoinen-id (rand-int 1000000)
          urakka (first (q-map "SELECT nimi, id, alkupvm, loppupvm FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2017-2024'"))

          ;; Luo paikkauskohde
          paikkauskohde-id (i (format "INSERT INTO paikkauskohde
                                       (nimi, \"urakka-id\", \"ulkoinen-id\", alkupvm, loppupvm, poistettu, \"tiemerkinnan-tila\", suorittava_tiemerkintaurakka)
                                       VALUES
                                       ('Testipaikkauskohde plim', %s, '%s', '%s', '%s', false, '%s', '%s')
                                       RETURNING id"
                                urakka-id ulkoinen-id (:alkupvm urakka) (:loppupvm urakka) "valmis" urakka-id))

          ;; Lisää tieosoite paikkauskohteelle
          _ (i (format "UPDATE paikkauskohde 
                        SET tierekisteriosoite_laajennettu = 
                           ROW (20, 1, 1, 5, 16, 0, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu
                        WHERE id = %s"
                 paikkauskohde-id))

          ;; Lisää kustannus
          _ (i (format "INSERT INTO tiemerkinta_paikkauskohteen_kustannus 
                       (paikkauskohde, linjamerkinnat, pienmerkinnat, jyrsinnat, muut_kustannukset, luoja, luotu) 
                       VALUES 
                       (%s, 800, 400, 200, 400, 1, now())"
                 paikkauskohde-id))

          ;; Hae kustannukset
          kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                         :hae-tiemerkinta-paikkausten-kustannukset
                         +kayttaja-jvh+
                         {:urakka-id urakka-id :urakka-alkupvm (:alkupvm urakka)})]

      ;; Tarkista että tulos on oikein
      (is (not (empty? kustannukset)) "Paikkausten kustannuksia löytyy")
      (is (some #(= (:kohdenumero %) ulkoinen-id) kustannukset)
        "Lisätty paikkauskohde löytyy tuloksista")
      (let [kohde (first (filter #(= (:kohdenumero %) ulkoinen-id) kustannukset))]
        (is (= (:linjamerkinnat kohde) 800.00M))
        (is (= (:pienmerkinnat kohde) 400.00M))
        (is (= (:jyrsinnat kohde) 200.00M))
        (is (= (:muut-kustannukset kohde) 400.00M)))

      ;; Siivoa testidatat
      (u (format "DELETE FROM tiemerkinta_paikkauskohteen_kustannus WHERE paikkauskohde = %s"
           paikkauskohde-id))
      (u (format "DELETE FROM paikkauskohde WHERE id = %s"
           paikkauskohde-id)))))

(deftest tallenna-tiemerkinta-paikkauskohteiden-kustannukset-toimii
  (testing "Paikkauskohteiden kustannusten tallennus toimii"
    (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2017-2024")
          ulkoinen-id (rand-int 1000000)
          urakka (first (q-map "SELECT nimi, id, alkupvm, loppupvm FROM urakka WHERE nimi = 'Oulun tiemerkinnän palvelusopimus 2017-2024'"))

          ;; Luo paikkauskohde
          paikkauskohde-id (i (format "INSERT INTO paikkauskohde
                                       (nimi, \"urakka-id\", \"ulkoinen-id\", alkupvm, loppupvm, poistettu, \"tiemerkinnan-tila\", suorittava_tiemerkintaurakka)
                                       VALUES
                                       ('Testipaikkauskohde tallennus', %s, '%s', '%s', '%s', false, '%s', '%s')
                                       RETURNING id"
                                urakka-id ulkoinen-id (:alkupvm urakka) (:loppupvm urakka) "valmis", urakka-id))

          ;; Lisää tieosoite paikkauskohteelle
          _ (i (format "UPDATE paikkauskohde 
                        SET tierekisteriosoite_laajennettu = 
                           ROW (20, 1, 1, 5, 16, 0, NULL, NULL, NULL, NULL)::tr_osoite_laajennettu
                        WHERE id = %s"
                 paikkauskohde-id))

          ;; Tallenna kustannukset
          kustannustiedot {:tiedot [{:id paikkauskohde-id
                                     :linjamerkinnat 700.25M
                                     :pienmerkinnat 350.50M
                                     :jyrsinnat 200.75M
                                     :muut-kustannukset 320.25M}]}

          tallennustulos (kutsu-palvelua (:http-palvelin jarjestelma)
                           :tallenna-tiemerkinta-paikkauskohteiden-kustannukset
                           +kayttaja-jvh+
                           kustannustiedot)

          ;; Hae kustannukset ja tarkista että tallennus onnistui
          kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                         :hae-tiemerkinta-paikkausten-kustannukset
                         +kayttaja-jvh+
                         {:urakka-id urakka-id :urakka-alkupvm (:alkupvm urakka)})

          kohde (first (filter #(= (:kohdenumero %) ulkoinen-id) kustannukset))]

      ;; Tarkista tallennuksen tulos
      (is (:onnistui tallennustulos) "Tallennus onnistui")
      (is (= 1 (count (:paivitetyt tallennustulos))) "Yksi kohde päivitettiin")

      ;; Tarkista että kustannukset löytyvät hakemalla
      (is (seq kustannukset) "Kustannuksia löytyy")
      (is (some #(= (:kohdenumero %) ulkoinen-id) kustannukset) "Lisätty kohde löytyy tuloksista")

      (is (= (:linjamerkinnat kohde) 700.25M) "Linjamerkintöjen summa täsmää")
      (is (= (:pienmerkinnat kohde) 350.50M) "Pienmerkintöjen summa täsmää")
      (is (= (:jyrsinnat kohde) 200.75M) "Jyrsintöjen summa täsmää") 
      (is (= (:muut-kustannukset kohde) 320.25M) "Muut kustannukset summa täsmää")


      ;; Kokeile päivitystä muuttamalla arvoja
      (let [paivitystiedot {:tiedot [{:id paikkauskohde-id
                                      :linjamerkinnat 900.00M
                                      :pienmerkinnat 450.00M
                                      :jyrsinnat 250.00M
                                      :muut-kustannukset 520.25M}]}

            paivitystulos (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-tiemerkinta-paikkauskohteiden-kustannukset
                            +kayttaja-jvh+
                            paivitystiedot)

            paivitetyt-kustannukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :hae-tiemerkinta-paikkausten-kustannukset
                                      +kayttaja-jvh+
                                      {:urakka-id urakka-id :urakka-alkupvm (:alkupvm urakka)})

            paivitetty-kohde (first (filter #(= (:kohdenumero %) ulkoinen-id) paivitetyt-kustannukset))]

        ;; Tarkista päivityksen tulos
        (is (:onnistui paivitystulos) "Päivitys onnistui")
        (is (= 1 (count (:paivitetyt paivitystulos))) "Yksi kohde päivitettiin")

        ;; Tarkista että päivitetyt kustannukset löytyvät hakemalla
        (is (seq paivitetyt-kustannukset) "Päivitettyjä kustannuksia löytyy")
        (is (some #(= (:kohdenumero %) ulkoinen-id) paivitetyt-kustannukset) "Päivitetty kohde löytyy tuloksista")

        (is (= (:linjamerkinnat paivitetty-kohde) 900.00M) "Päivitetty linjamerkintöjen summa täsmää")
        (is (= (:pienmerkinnat paivitetty-kohde) 450.00M) "Päivitetty pienmerkintöjen summa täsmää")
        (is (= (:jyrsinnat paivitetty-kohde) 250.00M) "Päivitetty jyrsintöjen summa täsmää")
        (is (= (:muut-kustannukset paivitetty-kohde) 520.25M) "Päivitetty Muut kustannukset summa täsmää"))

      ;; Siivoa testidatat
      (u (format "DELETE FROM tiemerkinta_paikkauskohteen_kustannus WHERE paikkauskohde = %s"
           paikkauskohde-id))
      (u (format "DELETE FROM paikkauskohde WHERE id = %s"
           paikkauskohde-id)))))

;; --- Päällystyskohteiden tiemerkintäkustannusten Excel-vienti ja -tuonti ---

(def ^:private excel-testiurakka "Oulun tiemerkinnän palvelusopimus 2017-2024")

(defn- luo-paallystyskohde! [urakka-id vuosi nimi kohdenumero]
  (i (format "INSERT INTO yllapitokohde
              (nimi, urakka, sopimus, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
               yllapitokohdetyyppi, yllapitokohdetyotyyppi, yotyo, vuodet, kohdenumero, suorittava_tiemerkintaurakka)
              VALUES ('%s', %s, (SELECT id FROM sopimus WHERE urakka = %s LIMIT 1), 4, 1, 0, 1, 100,
                      'paallyste'::YLLAPITOKOHDETYYPPI, 'paallystys'::YLLAPITOKOHDETYOTYYPPI,
                      false, '{%s}'::INTEGER[], %s, %s)"
       nimi urakka-id urakka-id vuosi (if kohdenumero (str "'" kohdenumero "'") "NULL") urakka-id)))

(defn- poista-kohteet! [idt]
  (doseq [id idt]
    (u (format "DELETE FROM tiemerkinta_yllapitokohteen_kustannus WHERE yllapitokohde = %s" id))
    (u (format "DELETE FROM yllapitokohde WHERE id = %s" id))))

(defn- kohteen-kustannukset [id]
  (first (q-map (format "SELECT linjamerkinnat, pienmerkinnat, jyrsinnat, muut_kustannukset
                         FROM tiemerkinta_yllapitokohteen_kustannus WHERE yllapitokohde = %s" id))))

(defn- tuonti-workbook [rivit]
  (xls/create-workbook "HARJAAN" (into [tm-excel/otsikot] rivit)))

(defn- tuontirivi [id linja pien jyrsinta muut]
  [(double id) nil nil "4/1/0/1/100" "Testikohde" "1" linja pien jyrsinta muut nil])

(deftest tiemerkintakustannusten-excel-vienti-ja-tuonti-toimii
  (let [db (:db jarjestelma)
        vuosi (pvm/vuosi (pvm/nyt))
        urakka-id (hae-urakan-id-nimella excel-testiurakka)
        kohde-id (luo-paallystyskohde! urakka-id vuosi "Excel-testikohde" nil)]
    (try
      (let [workbook (XSSFWorkbook.)
            _ (tm-excel/vie-tiemerkintakustannukset-exceliin db workbook +kayttaja-jvh+
                {:urakka-id urakka-id :vuosi vuosi})
            sheet (first (xls/sheet-seq workbook))
            rivit (remove nil? (xls/row-seq sheet))
            solu (fn [rivi sarake] (some-> (.getCell rivi (int sarake)) xls/read-cell))
            otsikkorivi (first (filter #(= "HARJA-ID" (solu % 0)) rivit))
            kohderivi (first (filter #(= (double kohde-id) (solu % 0)) rivit))
            datarivit (filter #(number? (solu % 0)) rivit)]
        (is (= tm-excel/otsikot (mapv #(solu otsikkorivi %) (range 11))) "Otsikot oikein")
        (is (some? kohderivi) "Kohde löytyy Excelistä")
        (is (nil? (solu kohderivi 2)) "Puuttuva kohdenumero on tyhjä")
        (is (= (str "SUM(G" (inc (.getRowNum kohderivi)) ":J" (inc (.getRowNum kohderivi)) ")")
              (.getCellFormula (.getCell kohderivi 10)))
          "Kokonaishinta lasketaan kaavalla")
        (is (= 4 (inc (.getRowNum (first datarivit)))) "Data alkaa yhteenvetokaavan olettamalta riviltä")
        (is (= [true true true true true true false false false false true]
              (mapv #(.getLocked (.getCellStyle (.getCell kohderivi (int %)))) (range 11)))
          "Vain kustannussarakkeet G–J ovat muokattavissa")

        ;; Rajataan tuonti testikohteeseen, jotta muiden kohteiden testidata ei muutu
        (doseq [rivi datarivit
                :when (not= rivi kohderivi)]
          (.removeCell rivi (.getCell rivi 0)))
        (doseq [[sarake arvo] {6 1200.5 7 600.75 9 320.0}]
          (.setCellValue (.getCell kohderivi (int sarake)) (double arvo)))
        (.setBlank (.getCell kohderivi 8))

        (is (= {:tallennettu 1}
              (tm-excel/tuo-tiemerkintakustannukset-excelista db +kayttaja-jvh+ workbook urakka-id vuosi)))
        (is (= {:linjamerkinnat 1200.50M :pienmerkinnat 600.75M :jyrsinnat 0.00M :muut_kustannukset 320.00M}
              (kohteen-kustannukset kohde-id))
          "Tyhjä solu tallennetaan nollana")

        (testing "Päivitys ylikirjoittaa aiemmat arvot"
          (is (= {:tallennettu 1}
                (tm-excel/tuo-tiemerkintakustannukset-excelista db +kayttaja-jvh+
                  (tuonti-workbook [(tuontirivi kohde-id 1.0 2.0 3.0 4.0)]) urakka-id vuosi)))
          (is (= {:linjamerkinnat 1.00M :pienmerkinnat 2.00M :jyrsinnat 3.00M :muut_kustannukset 4.00M}
                (kohteen-kustannukset kohde-id)))))
      (finally
        (poista-kohteet! [kohde-id])))))

(deftest tiemerkintakustannusten-excel-tuonti-hylkaa-virheelliset-tiedostot
  (let [db (:db jarjestelma)
        vuosi (pvm/vuosi (pvm/nyt))
        urakka-id (hae-urakan-id-nimella excel-testiurakka)
        toinen-urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        kohde-id (luo-paallystyskohde! urakka-id vuosi "Excel-testikohde" "LK2")
        toisen-urakan-kohde-id (luo-paallystyskohde! toinen-urakka-id vuosi "Toisen urakan kohde" nil)
        tuo (fn [workbook]
              (tm-excel/tuo-tiemerkintakustannukset-excelista db +kayttaja-jvh+ workbook urakka-id vuosi))]
    (try
      (testing "Väärät otsikot"
        (is (= ["Tiedoston rakenne ei vastaa mallipohjaa. Lataa pohja Lataa Excel -napista."]
              (:virheet (tuo (xls/create-workbook "HARJAAN" [["Kohde-ID" "Kohdenro"] [1 2]]))))))

      (testing "Tiedostossa ei kohteita"
        (is (= ["Tiedostosta ei löytynyt yhtään kohdetta."] (:virheet (tuo (tuonti-workbook []))))))

      (testing "Virheellisestä tiedostosta ei tallenneta mitään"
        (let [vastaus (tuo (tuonti-workbook
                             [(tuontirivi kohde-id 100.0 "abc" -5.0 nil)
                              (tuontirivi kohde-id 1.0 1.0 1.0 1.0)
                              (tuontirivi toisen-urakan-kohde-id 1.0 1.0 1.0 1.0)
                              (tuontirivi 1.5 1.0 1.0 1.0 1.0)]))]
          (is (= [(str "Rivi 2: Pienmerkinnät (€) ei ole numero")
                  (str "Rivi 2: Jyrsinnät (€) ei saa olla negatiivinen")
                  (str "Rivi 2: HARJA-ID " kohde-id " esiintyy tiedostossa useammin kuin kerran")
                  (str "Rivi 3: HARJA-ID " kohde-id " esiintyy tiedostossa useammin kuin kerran")
                  (str "Rivi 4: Kohdetta HARJA-ID:llä " toisen-urakan-kohde-id
                    " ei löydy urakan valitun vuoden päällystyskohteista")
                  "Rivi 5: HARJA-ID ei ole kelvollinen"]
                (:virheet vastaus)))
          (is (nil? (kohteen-kustannukset kohde-id)) "Mitään ei tallennettu")
          (is (nil? (kohteen-kustannukset toisen-urakan-kohde-id)) "Toisen urakan kohdetta ei muutettu")))

      (testing "Liian suuri summa"
        (is (= ["Rivi 2: Linjamerkinnät (€) on liian suuri"]
              (:virheet (tuo (tuonti-workbook [(tuontirivi kohde-id 1.0E8 0.0 0.0 0.0)])))))
        (is (= ["Rivi 2: Linjamerkinnät (€) on liian suuri"]
              (:virheet (tuo (tuonti-workbook [(tuontirivi kohde-id "99999999,995" 0.0 0.0 0.0)]))))
          "Raja tarkistetaan pyöristetystä arvosta"))

      (testing "Toisen vuoden kohde hylätään"
        (is (seq (:virheet (tm-excel/tuo-tiemerkintakustannukset-excelista db +kayttaja-jvh+
                             (tuonti-workbook [(tuontirivi kohde-id 1.0 1.0 1.0 1.0)])
                             urakka-id (dec vuosi))))))
      (finally
        (poista-kohteet! [kohde-id toisen-urakan-kohde-id])))))

(deftest tiemerkintakustannusten-excel-tuonti-vaatii-kirjoitusoikeuden
  (let [vuosi (pvm/vuosi (pvm/nyt))
        urakka-id (hae-urakan-id-nimella excel-testiurakka)]
    (is (= :ei-oikeutta
          (try+
            (tm-excel/tuo-tiemerkintakustannukset-excelista (:db jarjestelma) +kayttaja-tero+
              (tuonti-workbook []) urakka-id vuosi)
            :tallennettu
            (catch harja.domain.roolit.EiOikeutta _
              :ei-oikeutta))))))
