(ns harja.palvelin.palvelut.hallinta.kojelauta-kustiskorjaus-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.palvelut.hallinta.kojelauta :as kojelauta]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja
             [testi :refer :all]]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kojelauta-hallinta (component/using
                                (kojelauta/->KojelautaHallinta)
                                [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(defn kustannussuunnitelman-tila [urakka osio hoitovuosi]
  (first (q-map (format "SELECT id, urakka, osio, hoitovuosi, vahvistaja, vahvistettu, luoja, muokkaaja FROM suunnittelu_kustannussuunnitelman_tila
  WHERE urakka = %s AND osio = '%s' AND hoitovuosi = %s;" urakka osio hoitovuosi))))


;; Seuraavat testit liittyvät vain Kojelaudan kustannussuunnitelman tilojen korjaamiseen, ja sen koodin laadun varmistamiseen
(deftest hankintakustannus-nakyy-oikein-tilataulussa
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        iin-talvihoidon-toimenpideinstanssi (hae-toimenpideinstanssi-id-nimella "Iin MHU 2021-2026 Talvihoito TP")
        iin-sopimus-id (hae-sopimus-id-nimella "MHU Ii sopimus")
        integraatio-kayttajan-id (hae-kayttajan-id-kayttajanimella "Integraatio")
        ;; luodaan vahvistamaton hankintakustannus urakkaan hoitokaudelle 2021-2022
        _ (i (format "INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus, luotu, luoja, summa_indeksikorjattu, versio)
        VALUES (2021, 10, 22, %s, %s, '2024-10-22 12:22:55.341000', %s,  23.496, 0);" iin-talvihoidon-toimenpideinstanssi iin-sopimus-id kayttaja-id))
        ;; luodaan vahvistettu hankintakustannus urakkaan hoitokaudelle 2024-2025
        _ (i (format "INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus, luotu, luoja, summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio)
        VALUES (2024, 10, 44, %s, %s, '2024-10-22 12:34:47.559000', %s, 57.112, '2024-10-22 12:34:50.641000', %s, 0);" iin-talvihoidon-toimenpideinstanssi iin-sopimus-id kayttaja-id kayttaja-id))

        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "hankintakustannukset"))
        ;; Päivitetään kust. suunnitelman tilataulu niiltä hoitovuosilta jonne muutos tehtiin, jonka jälkeen assertoidaan että tieto on päivittynyt oikein
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "hankintakustannukset"))
        tilataulu-hankintakustannus-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 1)
        tilataulu-hankintakustannus-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 2)
        tilataulu-hankintakustannus-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 3)
        tilataulu-hankintakustannus-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 4)
        tilataulu-hankintakustannus-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 5)]
    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (not (empty? tilataulut-paivitystyksen-jalkeen)) "Päivityksen jälkeen urakassa on tietoa tilataulussa")
    (is (integer? (:id tilataulu-hankintakustannus-2021)) "2021 rivillä on id")
    (is (false? (:vahvistettu tilataulu-hankintakustannus-2021)) "2021 tilatieto on false")
    (is (nil? (:vahvistaja tilataulu-hankintakustannus-2021)) "2021 ei vahvistajaa")
    (is (empty? tilataulu-hankintakustannus-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-hankintakustannus-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-hankintakustannus-2025) "2025 tilatietoa ei ole")
    (is (true? (:vahvistettu tilataulu-hankintakustannus-2024)) "2024 vahvistettu on true")
    (is (number? (:vahvistaja tilataulu-hankintakustannus-2024)) "2024 vahvistaja löytyy")
    (is (= (:luoja tilataulu-hankintakustannus-2021) integraatio-kayttajan-id) "Luoja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")
    (is (nil? (:muokkaaja tilataulu-hankintakustannus-2021)) "Muokkaaja on insertin jälkeen nil.")
    (is (= (:luoja tilataulu-hankintakustannus-2024) integraatio-kayttajan-id) "Luoja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")
    (is (nil? (:muokkaaja tilataulu-hankintakustannus-2024)) "Muokkaaja on insertin jälkeen nil")))

(deftest erillishankinta-nakyy-oikein-tilataulussa
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        iin-hoidonjohto-toimenpideinstanssi (hae-toimenpideinstanssi-id-nimella "Iin MHU 2021-2026 MHU ja HJU Hoidon johto")
        iin-sopimus-id (hae-sopimus-id-nimella "MHU Ii sopimus")
        tehtavaryhma-id (hae-tehtavaryhman-id "Erillishankinnat (W)")
        integraatio-kayttajan-id (hae-kayttajan-id-kayttajanimella "Integraatio")
        ;; luodaan vahvistamaton erillishankinta urakkaan hoitokaudelle 2021-2022
        _ (i (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu, luoja,
        summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio)
        VALUES (2021, 10, 11, 'laskutettava-tyo', null, %s, %s, %s, '2024-10-22 13:10:31.712000', %s, 11.748, null, null, 0, 'erillishankinnat');" tehtavaryhma-id iin-hoidonjohto-toimenpideinstanssi iin-sopimus-id kayttaja-id))
        ;; luodaan vahvistettu erillishankinta urakkaan hoitokaudelle 2024-2025
        _ (i (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, tehtavaryhma, toimenpideinstanssi, sopimus, luotu,
        luoja, muokattu, muokkaaja, summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio, rahavaraus_id)
        VALUES (2024, 10, 44, 'laskutettava-tyo', null, %s, %s, %s, '2024-10-22 13:10:15.421000', %s, '2024-10-22 13:31:17.776000',
        %s, 57.112, '2024-10-22 13:31:20.924000', %s, 0, 'erillishankinnat', null);"  tehtavaryhma-id iin-hoidonjohto-toimenpideinstanssi iin-sopimus-id kayttaja-id kayttaja-id kayttaja-id))

        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "erillishankinnat"))
        ;; Päivitetään kust. suunnitelman tilataulu niiltä hoitovuosilta jonne muutos tehtiin, jonka jälkeen assertoidaan että tieto on päivittynyt oikein
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "erillishankinnat"))
        tilataulu-erillishankinnat-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 1)
        tilataulu-erillishankinnat-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 2)
        tilataulu-erillishankinnat-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 3)
        tilataulu-erillishankinnat-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 4)
        tilataulu-erillishankinnat-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 5)]

    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (not (empty? tilataulut-paivitystyksen-jalkeen)) "Päivityksen jälkeen urakassa on tietoa tilataulussa")
    (is (integer? (:id tilataulu-erillishankinnat-2021)) "2021 rivillä on id")
    (is (false? (:vahvistettu tilataulu-erillishankinnat-2021)) "2021 tilatieto on false")
    (is (nil? (:vahvistaja tilataulu-erillishankinnat-2021)) "2021 ei vahvistajaa")
    (is (empty? tilataulu-erillishankinnat-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-erillishankinnat-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-erillishankinnat-2025) "2025 tilatietoa ei ole")
    (is (true? (:vahvistettu tilataulu-erillishankinnat-2024)) "2024 vahvistettu on true")
    (is (number? (:vahvistaja tilataulu-erillishankinnat-2024)) "2024 vahvistaja löytyy")
    (is (= (:luoja tilataulu-erillishankinnat-2021) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")
    (is (= (:luoja tilataulu-erillishankinnat-2024) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")))

(deftest tavoitehintainen-rahavaraus-nakyy-oikein-tilataulussa
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        iin-talvihoidon-toimenpideinstanssi (hae-toimenpideinstanssi-id-nimella "Iin MHU 2021-2026 Talvihoito TP")
        iin-sopimus-id (hae-sopimus-id-nimella "MHU Ii sopimus")
        integraatio-kayttajan-id (hae-kayttajan-id-kayttajanimella "Integraatio")
        ;; luodaan vahvistamaton tavoitehintainen-rahavaraus urakkaan hoitokaudelle 2021-2022
        _ (i (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, toimenpideinstanssi, sopimus, luotu, luoja,
        summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio)
        VALUES (2021, 10, 11, 'laskutettava-tyo', %s, %s, '2024-10-22 13:10:31.712000', %s, 11.748, null, null, 0, 'tavoitehintaiset-rahavaraukset');"  iin-talvihoidon-toimenpideinstanssi iin-sopimus-id kayttaja-id))
        ;; luodaan vahvistettu tavoitehintainen-rahavaraus urakkaan hoitokaudelle 2024-2025
        _ (i (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, toimenpideinstanssi, sopimus, luotu, luoja,
        summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio)
        VALUES (2024, 10, 11, 'laskutettava-tyo', %s, %s, '2024-10-22 13:10:31.712000', %s, 11.748, '2024-10-22 13:10:31.712000', %s, 0, 'tavoitehintaiset-rahavaraukset');"  iin-talvihoidon-toimenpideinstanssi iin-sopimus-id kayttaja-id kayttaja-id))

        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset"))
        ;; Päivitetään kust. suunnitelman tilataulu niiltä hoitovuosilta jonne muutos tehtiin, jonka jälkeen assertoidaan että tieto on päivittynyt oikein
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset"))
        tilataulu-tavoitehintaiset-rahavaraukset-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 1)
        tilataulu-tavoitehintaiset-rahavaraukset-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 2)
        tilataulu-tavoitehintaiset-rahavaraukset-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 3)
        tilataulu-tavoitehintaiset-rahavaraukset-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 4)
        tilataulu-tavoitehintaiset-rahavaraukset-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 5)]
    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (not (empty? tilataulut-paivitystyksen-jalkeen)) "Päivityksen jälkeen urakassa on tietoa tilataulussa")
    (is (integer? (:id tilataulu-tavoitehintaiset-rahavaraukset-2021)) "2021 rivillä on id")
    (is (false? (:vahvistettu tilataulu-tavoitehintaiset-rahavaraukset-2021)) "2021 tilatieto on false")
    (is (nil? (:vahvistaja tilataulu-tavoitehintaiset-rahavaraukset-2021)) "2021 ei vahvistajaa")
    (is (empty? tilataulu-tavoitehintaiset-rahavaraukset-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-tavoitehintaiset-rahavaraukset-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-tavoitehintaiset-rahavaraukset-2025) "2025 tilatietoa ei ole")
    (is (true? (:vahvistettu tilataulu-tavoitehintaiset-rahavaraukset-2024)) "2024 vahvistettu on true")
    (is (number? (:vahvistaja tilataulu-tavoitehintaiset-rahavaraukset-2024)) "2024 vahvistaja löytyy")
    (is (= (:luoja tilataulu-tavoitehintaiset-rahavaraukset-2021) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")
    (is (= (:luoja tilataulu-tavoitehintaiset-rahavaraukset-2024) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")))

(deftest johto-ja-hallintokorvaus-nakyy-oikein-tilataulussa
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        iin-talvihoidon-toimenpideinstanssi (hae-toimenpideinstanssi-id-nimella "Iin MHU 2021-2026 Talvihoito TP")
        iin-sopimus-id (hae-sopimus-id-nimella "MHU Ii sopimus")
        integraatio-kayttajan-id (hae-kayttajan-id-kayttajanimella "Integraatio")
        ;; luodaan vahvistamaton johto-ja-hallintokorvaus urakkaan hoitokaudelle 2021-2022
        _ (i (format "INSERT INTO johto_ja_hallintokorvaus (\"urakka-id\", \"toimenkuva-id\", tunnit, tuntipalkka, luotu, luoja, vuosi, kuukausi, \"ennen-urakkaa\", \"osa-kuukaudesta\", \"siirretty?\", tuntipalkka_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio)
        VALUES (%s, 1, 40, 50, '2024-10-23 08:54:46.213000', %s, 2021, 10, false, 1, false, 53.4, null, null, 0)" iin-mhu-urakka-id kayttaja-id))
        ;; luodaan vahvistettu johto-ja-hallintokorvaus urakkaan hoitokaudelle 2024-2025
        _ (i (format "INSERT INTO johto_ja_hallintokorvaus (\"urakka-id\", \"toimenkuva-id\", tunnit, tuntipalkka, luotu, luoja, vuosi, kuukausi, \"ennen-urakkaa\", \"osa-kuukaudesta\", \"siirretty?\", tuntipalkka_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio)
        VALUES (%s, 1, 40, 50, '2024-10-23 08:54:46.213000', %s, 2024, 10, false, 1, false, 53.4, '2024-10-23 08:57:22.317000', %s, 0)" iin-mhu-urakka-id kayttaja-id kayttaja-id))

        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "johto-ja-hallintokorvaus"))
        ;; Päivitetään kust. suunnitelman tilataulu niiltä hoitovuosilta jonne muutos tehtiin, jonka jälkeen assertoidaan että tieto on päivittynyt oikein
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "johto-ja-hallintokorvaus"))
        tilataulu-johto-ja-hallintokorvaus-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 1)
        tilataulu-johto-ja-hallintokorvaus-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 2)
        tilataulu-johto-ja-hallintokorvaus-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 3)
        tilataulu-johto-ja-hallintokorvaus-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 4)
        tilataulu-johto-ja-hallintokorvaus-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 5)]
    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (not (empty? tilataulut-paivitystyksen-jalkeen)) "Päivityksen jälkeen urakassa on tietoa tilataulussa")
    (is (integer? (:id tilataulu-johto-ja-hallintokorvaus-2021)) "2021 rivillä on id")
    (is (false? (:vahvistettu tilataulu-johto-ja-hallintokorvaus-2021)) "2021 tilatieto on false")
    (is (nil? (:vahvistaja tilataulu-johto-ja-hallintokorvaus-2021)) "2021 ei vahvistajaa")
    (is (empty? tilataulu-johto-ja-hallintokorvaus-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-johto-ja-hallintokorvaus-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-johto-ja-hallintokorvaus-2025) "2025 tilatietoa ei ole")
    (is (true? (:vahvistettu tilataulu-johto-ja-hallintokorvaus-2024)) "2024 vahvistettu on true")
    (is (number? (:vahvistaja tilataulu-johto-ja-hallintokorvaus-2024)) "2024 vahvistaja löytyy")
    (is (= (:luoja tilataulu-johto-ja-hallintokorvaus-2021) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")
    (is (= (:luoja tilataulu-johto-ja-hallintokorvaus-2024) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")))

(deftest hoidonjohtopalkkio-nakyy-oikein-tilataulussa
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        iin-hoidonjohto-toimenpideinstanssi (hae-toimenpideinstanssi-id-nimella "Iin MHU 2021-2026 MHU ja HJU Hoidon johto")
        iin-sopimus-id (hae-sopimus-id-nimella "MHU Ii sopimus")
        hoidonjohdon-tehtava-id (hae-tehtavan-id-nimella "Hoidonjohtopalkkio")
        integraatio-kayttajan-id (hae-kayttajan-id-kayttajanimella "Integraatio")
        ;; luodaan vahvistamaton hoidonjohtopalkkio urakkaan hoitokaudelle 2021-2022
        _ (i (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, toimenpideinstanssi, sopimus, luotu, luoja,
        summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio)
        VALUES (2021, 10, 11, 'laskutettava-tyo', %s, %s, %s, '2024-10-22 13:10:31.712000', %s, 11.748, null, null, 0, 'hoidonjohtopalkkio');"  hoidonjohdon-tehtava-id iin-hoidonjohto-toimenpideinstanssi iin-sopimus-id kayttaja-id))
        ;; luodaan vahvistettu hoidonjohtopalkkio urakkaan hoitokaudelle 2024-2025
        _ (i (format "INSERT INTO kustannusarvioitu_tyo (vuosi, kuukausi, summa, tyyppi, tehtava, toimenpideinstanssi, sopimus, luotu, luoja,
        summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio, osio)
        VALUES (2024, 10, 11, 'laskutettava-tyo', %s, %s, %s, '2024-10-22 13:10:31.712000', %s, 11.748, '2024-10-22 13:10:31.712000', %s, 0, 'hoidonjohtopalkkio');"  hoidonjohdon-tehtava-id iin-hoidonjohto-toimenpideinstanssi iin-sopimus-id kayttaja-id kayttaja-id))

        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "hoidonjohtopalkkio"))
        ;; Päivitetään kust. suunnitelman tilataulu niiltä hoitovuosilta jonne muutos tehtiin, jonka jälkeen assertoidaan että tieto on päivittynyt oikein
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "hoidonjohtopalkkio"))
        tilataulu-hoidonjohtopalkkio-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 1)
        tilataulu-hoidonjohtopalkkio-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 2)
        tilataulu-hoidonjohtopalkkio-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 3)
        tilataulu-hoidonjohtopalkkio-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 4)
        tilataulu-hoidonjohtopalkkio-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 5)]
    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (not (empty? tilataulut-paivitystyksen-jalkeen)) "Päivityksen jälkeen urakassa on tietoa tilataulussa")
    (is (integer? (:id tilataulu-hoidonjohtopalkkio-2021)) "2021 rivillä on id")
    (is (false? (:vahvistettu tilataulu-hoidonjohtopalkkio-2021)) "2021 tilatieto on false")
    (is (nil? (:vahvistaja tilataulu-hoidonjohtopalkkio-2021)) "2021 ei vahvistajaa")
    (is (empty? tilataulu-hoidonjohtopalkkio-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-hoidonjohtopalkkio-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-hoidonjohtopalkkio-2025) "2025 tilatietoa ei ole")
    (is (true? (:vahvistettu tilataulu-hoidonjohtopalkkio-2024)) "2024 vahvistettu on true")
    (is (number? (:vahvistaja tilataulu-hoidonjohtopalkkio-2024)) "2024 vahvistaja löytyy")
    (is (= (:luoja tilataulu-hoidonjohtopalkkio-2021) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")
    (is (= (:luoja tilataulu-hoidonjohtopalkkio-2024) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")))

(deftest tavoite-ja-kattohinta-nakyy-oikein-tilataulussa
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        integraatio-kayttajan-id (hae-kayttajan-id-kayttajanimella "Integraatio")
        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "tavoite-ja-kattohinta"))
        ;; luodaan vahvistettu tavoite- ja kattohinta urakkaan hoitokaudelle 2024-2025
        ;; Päivitä urakka_tavoite taulua (oli sattumoisin jo testidatassa) siten että syntyy vahvistettu tavoite- ja kattohinta
        _ (u (format "UPDATE urakka_tavoite SET indeksikorjaus_vahvistettu = '2024-10-23 10:45:52.467000', vahvistaja = %s, muokkaaja = %s WHERE urakka = %s AND hoitokausi = %s;" kayttaja-id kayttaja-id iin-mhu-urakka-id 4))
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "tavoite-ja-kattohinta"))
        tilataulu-tavoite-ja-kattohinta-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoite-ja-kattohinta" 1)
        tilataulu-tavoite-ja-kattohinta-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoite-ja-kattohinta" 2)
        tilataulu-tavoite-ja-kattohinta-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoite-ja-kattohinta" 3)
        tilataulu-tavoite-ja-kattohinta-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoite-ja-kattohinta" 4)
        tilataulu-tavoite-ja-kattohinta-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoite-ja-kattohinta" 5)]
    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (not (empty? tilataulut-paivitystyksen-jalkeen)) "Päivityksen jälkeen urakassa on tietoa tilataulussa")
    (is (nil? (:id tilataulu-tavoite-ja-kattohinta-2021)) "2021 rivillä ei ole id;tä")
    (is (nil? (:vahvistettu tilataulu-tavoite-ja-kattohinta-2021)) "2021 tilatieto on false")
    (is (nil? (:vahvistaja tilataulu-tavoite-ja-kattohinta-2021)) "2021 ei vahvistajaa")
    (is (empty? tilataulu-tavoite-ja-kattohinta-2021) "2021 tilatietoa ei ole")
    (is (empty? tilataulu-tavoite-ja-kattohinta-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-tavoite-ja-kattohinta-2023) "2023 tilatietoa ei ole")
    (is (not (empty? tilataulu-tavoite-ja-kattohinta-2024)) "2024 tilatieto on")
    (is (empty? tilataulu-tavoite-ja-kattohinta-2025) "2025 tilatietoa ei ole")
    (is (integer? (:id tilataulu-tavoite-ja-kattohinta-2024)) "2024 rivillä ei ole id;tä")
    (is (true? (:vahvistettu tilataulu-tavoite-ja-kattohinta-2024)) "2024 vahvistettu on true")
    (is (number? (:vahvistaja tilataulu-tavoite-ja-kattohinta-2024)) "2024 vahvistaja löytyy")
    (is (= (:luoja tilataulu-tavoite-ja-kattohinta-2024) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")))

(deftest hankintakustannusta-ei-luoda-ellei-sita-ei-ole-kirjattu
  ;; tehdään sellainen testi, missä tietoa EI LÖYDY taulusta, ja varmistetaan ettei päivitysmekanistmi sitä sinne itse keksi
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "hankintakustannukset"))
        ;; Päivitetään kust. suunnitelman tilataulu, tässä testissä tieto ei saa syntyä koska sitä ei ole kirjattu
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "hankintakustannukset"))
        tilataulu-hankintakustannus-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 1)
        tilataulu-hankintakustannus-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 2)
        tilataulu-hankintakustannus-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 3)
        tilataulu-hankintakustannus-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 4)
        tilataulu-hankintakustannus-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 5)]
    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (empty? tilataulut-paivitystyksen-jalkeen) "Päivityksen jälkeenkään urakassa ei ole tietoa tilataulussa")
    (is (empty? tilataulu-hankintakustannus-2021) "2021 tilatietoa ei ole")
    (is (empty? tilataulu-hankintakustannus-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-hankintakustannus-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-hankintakustannus-2024) "2024 tilatietoa ei ole")
    (is (empty? tilataulu-hankintakustannus-2025) "2025 tilatietoa ei ole")))

(deftest erillishankintaa-ei-luoda-tilatauluun-ellei-sita-ole-kirjattu
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "erillishankinnat"))
        ;; Päivitetään kust. suunnitelman tilataulu, tässä testissä tieto ei saa syntyä koska sitä ei ole kirjattu
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "erillishankinnat"))
        tilataulu-erillishankinnat-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 1)
        tilataulu-erillishankinnat-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 2)
        tilataulu-erillishankinnat-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 3)
        tilataulu-erillishankinnat-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 4)
        tilataulu-erillishankinnat-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "erillishankinnat" 5)]

    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (empty? tilataulut-paivitystyksen-jalkeen) "Päivityksen jälkeenkään urakassa on tietoa tilataulussa")
    (is (empty? tilataulu-erillishankinnat-2021) "2021 tilatietoa ei ole")
    (is (empty? tilataulu-erillishankinnat-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-erillishankinnat-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-erillishankinnat-2024) "2024 tilatietoa ei ole")
    (is (empty? tilataulu-erillishankinnat-2025) "2025 tilatietoa ei ole")))

(deftest tavoitehintaista-rahavarausta-ei-luoda-tilatauluun-ellei-sita-ole-kirjattu
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset"))
        ;; Päivitetään kust. suunnitelman tilataulu, tässä testissä tieto ei saa syntyä koska sitä ei ole kirjattu
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset"))
        tilataulu-tavoitehintaiset-rahavaraukset-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 1)
        tilataulu-tavoitehintaiset-rahavaraukset-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 2)
        tilataulu-tavoitehintaiset-rahavaraukset-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 3)
        tilataulu-tavoitehintaiset-rahavaraukset-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 4)
        tilataulu-tavoitehintaiset-rahavaraukset-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "tavoitehintaiset-rahavaraukset" 5)]

    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (empty? tilataulut-paivitystyksen-jalkeen) "Päivityksen jälkeenkään urakassa on tietoa tilataulussa")
    (is (empty? tilataulu-tavoitehintaiset-rahavaraukset-2021) "2021 tilatietoa ei ole")
    (is (empty? tilataulu-tavoitehintaiset-rahavaraukset-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-tavoitehintaiset-rahavaraukset-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-tavoitehintaiset-rahavaraukset-2024) "2024 tilatietoa ei ole")
    (is (empty? tilataulu-tavoitehintaiset-rahavaraukset-2025) "2025 tilatietoa ei ole")))

(deftest johto-ja-hallintokorvausta-ei-luoda-tilatauluun-ellei-sita-ole-kirjattu
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "johto-ja-hallintokorvaus"))
        ;; Päivitetään kust. suunnitelman tilataulu, tässä testissä tieto ei saa syntyä koska sitä ei ole kirjattu
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "johto-ja-hallintokorvaus"))
        tilataulu-johto-ja-hallintokorvaus-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 1)
        tilataulu-johto-ja-hallintokorvaus-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 2)
        tilataulu-johto-ja-hallintokorvaus-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 3)
        tilataulu-johto-ja-hallintokorvaus-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 4)
        tilataulu-johto-ja-hallintokorvaus-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "johto-ja-hallintokorvaus" 5)]

    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (empty? tilataulut-paivitystyksen-jalkeen) "Päivityksen jälkeenkään urakassa on tietoa tilataulussa")
    (is (empty? tilataulu-johto-ja-hallintokorvaus-2021) "2021 tilatietoa ei ole")
    (is (empty? tilataulu-johto-ja-hallintokorvaus-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-johto-ja-hallintokorvaus-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-johto-ja-hallintokorvaus-2024) "2024 tilatietoa ei ole")
    (is (empty? tilataulu-johto-ja-hallintokorvaus-2025) "2025 tilatietoa ei ole")))

(deftest hoidonjohtopalkkiota-ei-luoda-tilatauluun-ellei-sita-ole-kirjattu
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "hoidonjohtopalkkio"))
        ;; Päivitetään kust. suunnitelman tilataulu, tässä testissä tieto ei saa syntyä koska sitä ei ole kirjattu
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s AND osio = '%s';" iin-mhu-urakka-id "hoidonjohtopalkkio"))
        tilataulu-hoidonjohtopalkkio-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 1)
        tilataulu-hoidonjohtopalkkio-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 2)
        tilataulu-hoidonjohtopalkkio-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 3)
        tilataulu-hoidonjohtopalkkio-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 4)
        tilataulu-hoidonjohtopalkkio-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "hoidonjohtopalkkio" 5)]

    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (empty? tilataulut-paivitystyksen-jalkeen) "Päivityksen jälkeenkään urakassa on tietoa tilataulussa")
    (is (empty? tilataulu-hoidonjohtopalkkio-2021) "2021 tilatietoa ei ole")
    (is (empty? tilataulu-hoidonjohtopalkkio-2022) "2022 tilatietoa ei ole")
    (is (empty? tilataulu-hoidonjohtopalkkio-2023) "2023 tilatietoa ei ole")
    (is (empty? tilataulu-hoidonjohtopalkkio-2024) "2024 tilatietoa ei ole")
    (is (empty? tilataulu-hoidonjohtopalkkio-2025) "2025 tilatietoa ei ole")))
