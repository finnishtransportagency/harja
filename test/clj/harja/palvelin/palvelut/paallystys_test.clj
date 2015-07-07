(ns harja.palvelin.palvelut.paallystys-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.paallystys :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :urakan-paallystyskohteet (component/using
                                                    (->Paallystys)
                                                    [:http-palvelin :db])
                        :urakan-paallystysilmoitus-paallystyskohteella (component/using
                                                                         (->Paallystys)
                                                                         [:http-palvelin :db])
                        :tallenna-paallystysilmoitus (component/using
                                                       (->Paallystys)
                                                       [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest paallystyskohteet-haettu-oikein
  (let [res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-paallystyskohteet +kayttaja-jvh+
                            {:urakka-id  @muhoksen-paallystysurakan-id
                             :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id})
        kohteiden-lkm (ffirst (q
                                (str "SELECT count(*)
                                      FROM paallystyskohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")")))]
    (is (= (count res) kohteiden-lkm) "Päällystyskohteiden määrä")))

(deftest tallenna-paallystysilmoitus-kantaan
  (let [paallystyskohde-id-jolla-ei-ilmoitusta (ffirst (q (str "
                                                           SELECT paallystyskohde.id as paallystyskohde_id
                                                           FROM paallystyskohde
                                                           FULL OUTER JOIN paallystysilmoitus ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                                           WHERE paallystysilmoitus.id IS NULL;")))]
    (is (not (nil? paallystyskohde-id-jolla-ei-ilmoitusta)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus {:paallystyskohde-id paallystyskohde-id-jolla-ei-ilmoitusta
                              :aloituspvm         (java.sql.Date. 105 9 1)
                              :valmistumispvm     (java.sql.Date. 105 9 2)
                              :takuupvm           (java.sql.Date. 105 9 3)
                              :muutoshinta        0
                              :ilmoitustiedot     "{\"kohde\":12554556785645,\"kohdenimi\":\"Leppäkorvenrampit\",\"tarjoushinta\":90000,\"hinta\":5000,\"osoitteet\":[{\"tie\":2846,\"aosa\":5,\"aet\":22,\"losa\":5,\"let\":9377,\"ajorata\":0,\"suunta\":0,\"kaista\":1,\"paallystetyyppi\":21,\"raekoko\":16,\"massa\":100,\"rc%\":0,\"tyomenetelma\":12,\"leveys\":6.5,\"massamaara\":1781,\"edellinen-paallystetyyppi\":12,\"pinta-ala\":15},{\"tie\":2846,\"aosa\":5,\"aet\":22,\"losa\":5,\"let\":9377,\"ajorata\":1,\"suunta\":0,\"kaista\":1,\"paallystetyyppi\":21,\"raekoko\":10,\"massa\":512,\"rc%\":0,\"tyomenetelma\":12,\"leveys\":4,\"massamaara\":1345,\"edellinen-paallystetyyppi\":11,\"pinta-ala\":9}],\"kiviaines\":[{\"esiintyma\":\"KAMLeppäsenoja\",\"km-arvo\":\"An14\",\"muotoarvo\":\"Fi20\",\"sideainetyyppi\":\"B650/900\",\"pitoisuus\":4.3,\"lisaaineet\":\"Tartuke\"}],\"alustatoimet\":[{\"tie\":5,\"aosa\":22,\"aet\":3,\"losa\":5,\"let\":4785,\"kasittelymenetelma\":13,\"paksuus\":30,\"verkkotyyppi\":1,\"tekninen-toimenpide\":2}],\"tyot\":[{\"tyyppi\":\"ajoradan-paallyste\",\"toimenpidekoodi\":1350,\"tilattu-maara\":10000,\"toteutunut-maara\":10100,\"yksikkohinta\":20}]}"}
          maara-ennen-lisaysta (ffirst (q
                                         (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id
                                              "AND sopimus = " sopimus-id ";")))
          tallenna-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                                        :sopimus-id         sopimus-id
                                                                                        :paallystysilmoitus paallystysilmoitus})
          maara-lisayksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id
                                                 "AND sopimus = " sopimus-id ";")))
          #_paallystysilmoitus-kannassa #_(kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :urakan-paallystysilmoitus-paallystyskohteella +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                                                                   :sopimus-id         sopimus-id
                                                                                                                   :paallystyskohde-id paallystyskohde-id-jolla-ei-ilmoitusta})
          #_lisatty #_(first (filter #(= (:ilmoitustiedot %) (:ilmoitustiedot paallystysilmoitus)) paallystysilmoitus-kannassa))]
      ; TODO Tarkista että muutoshinta laskettiin ja tila asetettiin oikein
      (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen) "Tallennuksen jälkeen päällystysilmoituksien määrä")
      (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id-jolla-ei-ilmoitusta ";")))))