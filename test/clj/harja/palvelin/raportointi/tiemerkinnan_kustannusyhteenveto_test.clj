(ns harja.palvelin.raportointi.tiemerkinnan-kustannusyhteenveto-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto :as raportti]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.raportointi.testiapurit :as apurit]))

(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pdf-vienti (component/using
                                      (pdf-vienti/luo-pdf-vienti)
                                      [:http-palvelin])
                        :raportointi (component/using
                                       (raportointi/luo-raportointi)
                                       [:db :pdf-vienti])
                        :raportit (component/using
                                    (raportit/->Raportit)
                                    [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))

(use-fixtures :once (compose-fixtures jarjestelma-fixture urakkatieto-fixture))

(defn testidata-uusiksi!
  "Poistaa raporttiin liittyvän datan urakasta ja luo uuden (vuodesta 2010 eteenpäin)."
  []
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        tiemerkinnan-tpi (:id (first (q-map "SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id " LIMIT 1")))]

    ;; Tyhjennetään testattavan urakan kaikki raportilla näkyvät tiedot testin ajaksi
    (u (str "DELETE FROM kokonaishintainen_tyo WHERE toimenpideinstanssi IN
  (SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id ");"))
    (u (str "DELETE FROM tiemerkinnan_yksikkohintainen_toteuma WHERE urakka = " urakka-id ";"))
    (u (str "DELETE FROM yllapito_muu_toteuma WHERE urakka = " urakka-id ";"))
    (u (str "DELETE FROM sanktio WHERE toimenpideinstanssi IN
    (SELECT id FROM toimenpideinstanssi WHERE urakka = " urakka-id ");"))
    (u (str "DELETE FROM laatupoikkeama WHERE urakka = " urakka-id ";"))
    (u (str "DELETE FROM yllapitokohde WHERE urakka = " urakka-id ";"))

    ;; Luodaan tyhjästä uudet tiedot, jotta nähdään, että raportissa käytetty data on laskettu oikein

    ;; Kok. hint. työt
    (u (str "INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
    VALUES (2017, 10, 1, '2017-10-15', " tiemerkinnan-tpi ", " sopimus-id ");"))
    (u (str "INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
    VALUES (2017, 11, 2, '2017-10-15', " tiemerkinnan-tpi ", " sopimus-id ");"))
    (u (str "INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
    VALUES (2007, 1, 999999, '2017-10-15', " tiemerkinnan-tpi ", " sopimus-id ");")) ; Ei aikavälillä

    ;; Ylläpitokohteet
    (u "INSERT INTO yllapitokohde (urakka, sopimus, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys, yllapitokohdetyotyyppi)
    VALUES (" urakka-id "," sopimus-id ", 'Nopea testikohde', 20, 1, 1, 'paallystys');")
    (u "INSERT INTO yllapitokohde (urakka, sopimus, nimi, tr_numero, tr_alkuosa, tr_alkuetaisyys,  yllapitokohdetyotyyppi, poistettu)
    VALUES (" urakka-id "," sopimus-id ", 'Nopea poistettu testikohde', 20, 1, 1, 'paallystys', true);")

    ;; Yks. hint. työt
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,null, 1, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null,
    'Testitoteuma 1', 20, 8, 5);"))
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus, poistettu)
    VALUES (" urakka-id " ,null, 1, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null,
    'Testitoteuma 1', 20, 8, 5, true);"))
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,null, 100, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2018-01-01', null,
    'Testitoteuma 2', 21, 8, 5);"))
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,(SELECT id FROM yllapitokohde WHERE nimi = 'Nopea testikohde'),
    1, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', '',
    'Testitoteuma 1', null, null, null);")) ; Ylläpitokohteeseen liittyvä työ
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,(SELECT id FROM yllapitokohde WHERE nimi = 'Nopea poistettu testikohde'),
    1, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', '',
    'Testitoteuma 1', null, null, null);")) ; Poistettuun ylläpitokohteeseen liittyvä työ, ei näy raportilla
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,null, 5, 'suunnitelma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null,
    'Testitoteuma 1', 20, 8, 5);")) ; Suunnitelma
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus, poistettu)
    VALUES (" urakka-id " ,null, 5, 'suunnitelma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null,
    'Testitoteuma 1', 20, 8, 5, true);")) ; Poistettu suunnitelma
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,null, 5, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2000-01-01', null,
    'Testitoteuma 1', 20, 8, 5);")) ; Toteuma ei aikavälillä
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus, poistettu)
    VALUES (" urakka-id " ,null, 5, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null,
    'Testitoteuma 1', 20, 8, 5, true);")) ; Poistettu toteuma

    ;; Muut työt
    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, yllapitoluokka,
    laskentakohde, luotu, luoja) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2016-10-10', 1000, 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));"))
    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, yllapitoluokka,
    laskentakohde, luotu, luoja, poistettu) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2016-10-10', 1000, 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'), true);"))
    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, yllapitoluokka,
    laskentakohde, luotu, luoja) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2014-10-10', 1001, 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));"))
    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, yllapitoluokka,
    laskentakohde, luotu, luoja) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2000-10-10', 1001, 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));")) ; Ei aikavälillä
    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, yllapitoluokka,
    laskentakohde, luotu, luoja, poistettu) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2014-10-10', 1001, 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'), true);")) ; Poistettu

    ;; arvonmuutokset
    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, tyyppi, yllapitoluokka,
    laskentakohde, luotu, luoja) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2016-10-10', 1000, 'arvonmuutos', 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));"))
    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, tyyppi, yllapitoluokka,
    laskentakohde, luotu, luoja, poistettu) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2016-10-10', 1000, 'arvonmuutos', 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'), true);"))
    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, tyyppi, yllapitoluokka,
    laskentakohde, luotu, luoja) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2014-10-10', 1001, 'arvonmuutos', 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));"))

    ;; indeksit
    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, tyyppi, yllapitoluokka,
    laskentakohde, luotu, luoja) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2014-10-10', 1001, 'indeksi', 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));"))

    ;; Sakot
    (u (str "INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos,
    perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka,
    kuvaus) VALUES ('harja-ui'::lahde, null, 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
    'hylatty'::laatupoikkeaman_paatostyyppi, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
    '2017-01-05 13:06.37', false, false, " urakka-id ", 'Ylläpitokohteeseeton suorasanktio 666');"))
    (u (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi,
    tyyppi, suorasanktio, luoja) VALUES ('yllapidon_sakko'::sanktiolaji, 1000, '2017-01-5 06:06.37', null,
    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 666')," tiemerkinnan-tpi ",
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon sakko'), true, 2);"))
    ; Poistettuun laatupoikkeamaan liittyvä sanktio, jää huomiotta raportilla
    (u (str "INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos,
    perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka,
    kuvaus, poistettu) VALUES ('harja-ui'::lahde, null, 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
    'hylatty'::laatupoikkeaman_paatostyyppi, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
    '2017-01-05 13:06.37', false, false, " urakka-id ", 'Ylläpitokohteeseeton suorasanktio 667', true);"))
    (u (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi,
    tyyppi, suorasanktio, luoja) VALUES ('yllapidon_sakko'::sanktiolaji, 1000, '2017-01-5 06:06.37', null,
    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 667')," tiemerkinnan-tpi ",
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon sakko'), true, 2);"))
    ;; Poistettu sanktio, jää huomiotta raportilla
    (u (str "INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos,
    perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka,
    kuvaus) VALUES ('harja-ui'::lahde, null, 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
    'hylatty'::laatupoikkeaman_paatostyyppi, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
    '2017-01-05 13:06.37', false, false, " urakka-id ", 'Ylläpitokohteeseeton suorasanktio 668');"))
    (u (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi,
    tyyppi, suorasanktio, luoja, poistettu) VALUES ('yllapidon_sakko'::sanktiolaji, 1000, '2017-01-5 06:06.37', null,
    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 668')," tiemerkinnan-tpi ",
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon sakko'), true, 2, true);"))
    ;; Sanktio, joka liittyy laatupoikkeamaan, joka taas liittyy poistettuun ylläpitokohteeseen, ei näy
    (u (str "INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos,
    perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka,
    kuvaus) VALUES ('harja-ui'::lahde, (SELECT id FROM yllapitokohde WHERE nimi = 'Nopea poistettu testikohde'),
    'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
    'hylatty'::laatupoikkeaman_paatostyyppi, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
    '2017-01-05 13:06.37', false, false, " urakka-id ", 'Ylläpitokohteeseeton suorasanktio 669');"))
    (u (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi,
    tyyppi, suorasanktio, luoja) VALUES ('yllapidon_sakko'::sanktiolaji, 1000, '2017-01-5 06:06.37', null,
    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 669')," tiemerkinnan-tpi ",
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon sakko'), true, 2);"))

    ;; Bonukset
    (u (str "INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos,
    perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka,
    kuvaus) VALUES ('harja-ui'::lahde, null, 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
    'hylatty'::laatupoikkeaman_paatostyyppi, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
    '2017-01-05 13:06.37', false, false, " urakka-id ", 'Ylläpitokohteeseeton suorasanktio 866');"))
    (u (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi,
    tyyppi, suorasanktio, luoja) VALUES ('yllapidon_bonus'::sanktiolaji, -1, '2017-01-5 06:06.37', null,
    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 866')," tiemerkinnan-tpi ",
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon sakko'), true, 2);"))
    ; Poistettuun laatupoikkeamaan liittyvä bonus, jää huomiotta raportilla
    (u (str "INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos,
    perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka,
    kuvaus, poistettu) VALUES ('harja-ui'::lahde, null, 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
    'hylatty'::laatupoikkeaman_paatostyyppi, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
    '2017-01-05 13:06.37', false, false, " urakka-id ", 'Ylläpitokohteeseeton suorasanktio 867', true);"))
    (u (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi,
    tyyppi, suorasanktio, luoja) VALUES ('yllapidon_bonus'::sanktiolaji, -1, '2017-01-5 06:06.37', null,
    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 867')," tiemerkinnan-tpi ",
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon sakko'), true, 2);"))
    ;; Poistettu bonus, jää huomiotta raportilla
    (u (str "INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos,
    perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka,
    kuvaus) VALUES ('harja-ui'::lahde, null, 'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
    'hylatty'::laatupoikkeaman_paatostyyppi, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
    '2017-01-05 13:06.37', false, false, " urakka-id ", 'Ylläpitokohteeseeton suorasanktio 868');"))
    (u (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi,
    tyyppi, suorasanktio, luoja, poistettu) VALUES ('yllapidon_bonus'::sanktiolaji, -1, '2017-01-5 06:06.37', null,
    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 868')," tiemerkinnan-tpi ",
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon sakko'), true, 2, true);"))
    ;; Bonus, joka liittyy laatupoikkeamaan, joka taas liittyy poistettuun ylläpitokohteeseen, ei näy
    (u (str "INSERT INTO laatupoikkeama (lahde, yllapitokohde, tekija, kasittelytapa, muu_kasittelytapa, paatos,
    perustelu, tarkastuspiste, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka,
    kuvaus) VALUES ('harja-ui'::lahde, (SELECT id FROM yllapitokohde WHERE nimi = 'Nopea poistettu testikohde'),
    'tilaaja'::osapuoli, 'puhelin'::laatupoikkeaman_kasittelytapa, '',
    'hylatty'::laatupoikkeaman_paatostyyppi, 'Ei tässä ole mitään järkeä', 123, 1, NOW(), '2017-01-3 12:06.37',
    '2017-01-05 13:06.37', false, false, " urakka-id ", 'Ylläpitokohteeseeton suorasanktio 869');"))
    (u (str "INSERT INTO sanktio (sakkoryhma, maara, perintapvm, indeksi, laatupoikkeama, toimenpideinstanssi,
    tyyppi, suorasanktio, luoja) VALUES ('yllapidon_bonus'::sanktiolaji, -1000, '2017-01-5 06:06.37', null,
    (SELECT id FROM laatupoikkeama WHERE kuvaus = 'Ylläpitokohteeseeton suorasanktio 869')," tiemerkinnan-tpi ",
    (SELECT id FROM sanktiotyyppi WHERE nimi = 'Ylläpidon sakko'), true, 2);"))))

(deftest raportin-suoritus-urakalle-toimii
  (testidata-uusiksi!)
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        {:keys [kokonaishintaiset-tyot yksikkohintaiset-toteumat
                muut-tyot arvonmuutokset indeksit
                sakot bonukset yksikkohintaiset-suunnitellut-tyot toteumat-yhteensa kokonaisia-kuukausia-aikavalina?]
         :as raportin-tiedot}
        (raportti/hae-raportin-tiedot {:db (:db jarjestelma)
                                       :urakka-id urakka-id
                                       :alkupvm (pvm/luo-pvm 2010 1 1)
                                       :loppupvm (pvm/luo-pvm 2080 1 31)})]
    (is (map? raportin-tiedot))
    (is (== kokonaishintaiset-tyot 3))
    (is (== yksikkohintaiset-toteumat 102))
    (is (== yksikkohintaiset-suunnitellut-tyot 5))
    (is (== muut-tyot 2001))
    (is (== arvonmuutokset 2001))
    (is (== indeksit 1001))
    (is (== sakot 1000))
    (is (== bonukset -1))

    (is (== toteumat-yhteensa 6104)) ;; Ei sis. kok. hint. töitä koska aikaväli ei ole kk-väli
    (is (false? kokonaisia-kuukausia-aikavalina?))))

(deftest raportin-suoritus-urakalle-toimii
  (testidata-uusiksi!)
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        {:keys [kokonaishintaiset-tyot yksikkohintaiset-toteumat
                muut-tyot arvonmuutokset indeksit
                sakot bonukset yksikkohintaiset-suunnitellut-tyot toteumat-yhteensa kokonaisia-kuukausia-aikavalina?]
         :as raportin-tiedot}
        (raportti/hae-raportin-tiedot {:db (:db jarjestelma)
                                       :urakka-id urakka-id
                                       :alkupvm (pvm/luo-pvm 2014 9 1)
                                       :loppupvm (pvm/luo-pvm 2014 11 31)})]
    (is (map? raportin-tiedot))
    (is (== kokonaishintaiset-tyot 0))
    (is (== yksikkohintaiset-toteumat 0))
    (is (== yksikkohintaiset-suunnitellut-tyot 0))
    (is (== muut-tyot 1001))
    (is (== arvonmuutokset 1001))
    (is (== indeksit 1001))
    (is (== sakot 0))
    (is (== bonukset 0))

    (is (== toteumat-yhteensa 3003)) ;; Ei sis. kok. hint. töitä koska aikaväli ei ole kk-väli
    (is (true? kokonaisia-kuukausia-aikavalina?))))

(deftest kok-hint-tyot-lasketaan-vain-kuukausivalille
  (testidata-uusiksi!)
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        {:keys [kokonaishintaiset-tyot yksikkohintaiset-toteumat
                muut-tyot sakot bonukset yksikkohintaiset-suunnitellut-tyot toteumat-yhteensa kokonaisia-kuukausia-aikavalina?]
         :as raportin-tiedot}
        (raportti/hae-raportin-tiedot {:db (:db jarjestelma)
                                       :urakka-id urakka-id
                                       :alkupvm (pvm/luo-pvm 2017 9 1)
                                       :loppupvm (pvm/luo-pvm 2017 10 30)})]
    (is (map? raportin-tiedot))
    (is (== kokonaishintaiset-tyot 3))
    (is (== yksikkohintaiset-toteumat 0))
    (is (== yksikkohintaiset-suunnitellut-tyot 0))
    (is (== muut-tyot 0))
    (is (== sakot 0))
    (is (== bonukset 0))

    (is (== toteumat-yhteensa 3))
    (is (true? kokonaisia-kuukausia-aikavalina?))))
