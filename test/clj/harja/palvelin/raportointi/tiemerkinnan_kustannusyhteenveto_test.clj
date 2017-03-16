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
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures jarjestelma-fixture urakkatieto-fixture))

(deftest raportin-suoritus-urakalle-toimii
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

    ;; Luodaan tyhjästä uudet tiedot, jotta nähdään, että raportissa käytetty data on laskettu oikein

    (u (str "INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
    VALUES (2017, 10, 1, '2017-10-15', " tiemerkinnan-tpi ", " sopimus-id ");"))
    (u (str "INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
    VALUES (2017, 11, 2, '2017-10-15', " tiemerkinnan-tpi ", " sopimus-id ");"))
    (u (str "INSERT INTO kokonaishintainen_tyo (vuosi,kuukausi,summa,maksupvm,toimenpideinstanssi,sopimus)
    VALUES (2007, 1, 999999, '2017-10-15', " tiemerkinnan-tpi ", " sopimus-id ");")) ; Ei aikavälillä

    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,null, 1, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null,
    'Testitoteuma 1', 20, 8, 5);"))
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,null, 100, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2018-01-01', null,
    'Testitoteuma 2', 21, 8, 5);"))
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,null, 5, 'suunnitelma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null,
    'Testitoteuma 1', 20, 8, 5);")) ; Suunnitelma
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus)
    VALUES (" urakka-id " ,null, 1, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2000-01-01', null,
    'Testitoteuma 1', 20, 8, 5);")) ; Ei aikavälillä
    (u (str "INSERT INTO tiemerkinnan_yksikkohintainen_toteuma(urakka, yllapitokohde, hinta, hintatyyppi,
    paivamaara, hinta_kohteelle, selite, tr_numero, yllapitoluokka, pituus, poistettu)
    VALUES (" urakka-id " ,null, 1, 'toteuma':: tiemerkinta_toteuma_hintatyyppi, '2016-01-01', null,
    'Testitoteuma 1', 20, 8, 5, true);")) ; Poistettu

    (u (str "INSERT INTO yllapito_muu_toteuma (urakka, sopimus, selite, pvm, hinta, yllapitoluokka,
    laskentakohde, luotu, luoja) VALUES (" urakka-id ", " sopimus-id ", 'Selite 1', '2016-10-10', 1000, 1,
    (SELECT id FROM urakka_laskentakohde WHERE nimi = 'Laskentakohde 1'),  NOW(),
    (SELECT id FROM kayttaja where kayttajanimi = 'jvh'));"))
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

    (let [{:keys [kokonaishintaiset-tyot yksikkohintaiset-toteumat
                  muut-tyot sakot bonukset toteumat-yhteensa kk-vali?]
           :as raportin-tiedot}
          (raportti/hae-raportin-tiedot {:db (:db jarjestelma)
                                         :urakka-id urakka-id
                                         :alkupvm (pvm/luo-pvm 2010 1 1)
                                         :loppupvm (pvm/luo-pvm 2080 1 1)})]
      (is (map? raportin-tiedot))
      (is (== kokonaishintaiset-tyot 3))
      (is (== yksikkohintaiset-toteumat 101))
      (is (== muut-tyot 2001))

      (is (== toteumat-yhteensa 2106))
      (is (false? kk-vali?)))))
