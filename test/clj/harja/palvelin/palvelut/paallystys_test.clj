(ns harja.palvelin.palvelut.paallystys-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.paallystys :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [cheshire.core :as cheshire]))


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

(def pot-testidata
  {:aloituspvm     (java.sql.Date. 105 9 1)
   :valmistumispvm (java.sql.Date. 105 9 2)
   :takuupvm       (java.sql.Date. 105 9 3)
   :muutoshinta    0
   :ilmoitustiedot {:osoitteet    [{:tie                       1
                                    :aosa                      2
                                    :aet                       3
                                    :losa                      4
                                    :let                       5
                                    :ajorata                   1
                                    :suunta                    0
                                    :kaista                    1
                                    :paallystetyyppi           1
                                    :raekoko                   1
                                    :massa                     2
                                    :rc%                       3
                                    :tyomenetelma              12
                                    :leveys                    5
                                    :massamaara                7
                                    :pinta-ala                 8
                                    :edellinen-paallystetyyppi 1}]

                    :kiviaines    [{:esiintyma      "asd"
                                    :km-arvo        "asd"
                                    :muotoarvo      "asd"
                                    :sideainetyyppi "asd"
                                    :pitoisuus      54
                                    :lisaaineet     "asd"}]

                    :alustatoimet [{:tie                 1
                                    :aosa                2
                                    :aet                 3
                                    :losa                4
                                    :let                 5
                                    :kasittelymenetelma  1
                                    :paksuus             1234
                                    :verkkotyyppi        1
                                    :tekninen-toimenpide 1
                                    }]

                    :tyot         [{:tyyppi           :ajoradan-paallyste
                                    :toimenpidekoodi  3
                                    :tilattu-maara    100
                                    :toteutunut-maara 200
                                    :yksikko          "km"
                                    :yksikkohinta     5}]
                    }})

(def paallystyskohde-id-jolla-ei-ilmoitusta (ffirst (q (str "
                                                           SELECT paallystyskohde.id as paallystyskohde_id
                                                           FROM paallystyskohde
                                                           FULL OUTER JOIN paallystysilmoitus ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                                           WHERE paallystysilmoitus.id IS NULL;"))))


(def paallystyskohde-id-jolla-on-ilmoitus (ffirst (q (str "
                                                           SELECT paallystyskohde.id as paallystyskohde_id
                                                           FROM paallystyskohde
                                                           JOIN paallystysilmoitus ON paallystyskohde.id = paallystysilmoitus.paallystyskohde;"))))

(deftest paallystyskohteet-haettu-oikein
  (let [res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-paallystyskohteet +kayttaja-jvh+
                            {:urakka-id  @muhoksen-paallystysurakan-id
                             :sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM paallystyskohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " @muhoksen-paallystysurakan-id ")")))]
    (is (= (count res) kohteiden-lkm) "Päällystyskohteiden määrä")))

(deftest skeemavalidointi-toimii
  (let [paallystyskohde-id paallystyskohde-id-jolla-ei-ilmoitusta]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:ilmoitustiedot :ylimaarainen-keyword] "Olen cräppidataa, en halua kantaan :("))
          maara-ennen-pyyntoa (ffirst (q
                                        (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id
                                             "AND sopimus = " sopimus-id ";")))]

      (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :tallenna-paallystysilmoitus
                                                    +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                    :sopimus-id         sopimus-id
                                                                    :paallystysilmoitus paallystysilmoitus})))
      (let [maara-pyynnon-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id
                                                 "AND sopimus = " sopimus-id ";")))]
        (is (= maara-ennen-pyyntoa maara-pyynnon-jalkeen))))))

(deftest tallenna-paallystysilmoitus-kantaan
  (let [paallystyskohde-id paallystyskohde-id-jolla-ei-ilmoitusta]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
          maara-ennen-lisaysta (ffirst (q
                                         (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id
                                              "AND sopimus = " sopimus-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                   :sopimus-id         sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus})
      (let [maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id
                                                   "AND sopimus = " sopimus-id ";")))
            paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paallystysilmoitus-paallystyskohteella
                                                        +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                        :sopimus-id         sopimus-id
                                                                        :paallystyskohde-id paallystyskohde-id})]
        (log/debug "POT kannassa: " (pr-str paallystysilmoitus-kannassa))
        (log/debug "Muutoshinta: " (:muutoshinta paallystysilmoitus-kannassa))
        (log/debug "Muutoshinta tyyppi: " (type (:muutoshinta paallystysilmoitus-kannassa)))
        (is (not (nil? paallystysilmoitus-kannassa)))
        (is (= (:tila paallystysilmoitus-kannassa) :valmis))
        (is (= (:muutoshinta paallystysilmoitus-kannassa) (java.math.BigDecimal. 500)))
        (is (= (:ilmoitustiedot paallystysilmoitus-kannassa) (:ilmoitustiedot paallystysilmoitus)))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen) "Tallennuksen jälkeen päällystysilmoituksien määrä")
        (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";"))))))


(deftest paivita-paallystysilmoitukselle-paatostiedot
  (let [paallystyskohde-id paallystyskohde-id-jolla-on-ilmoitus]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc :paatos_taloudellinen_osa :hyvaksytty)
                                 (assoc :paatos_tekninen_osa :hyvaksytty)
                                 (assoc :perustelu "Hyvä ilmoitus!"))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                   :sopimus-id         sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus})
      (let [paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paallystysilmoitus-paallystyskohteella
                                                        +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                        :sopimus-id         sopimus-id
                                                                        :paallystyskohde-id paallystyskohde-id})]
        (log/debug "POT kannassa: " (pr-str paallystysilmoitus-kannassa))
        (is (= (:tila paallystysilmoitus-kannassa) :lukittu))
        (is (= (:paatos_tekninen_osa paallystysilmoitus-kannassa) :hyvaksytty))
        (is (= (:paatos_taloudellinen_osa paallystysilmoitus-kannassa) :hyvaksytty))
        (is (= (:ilmoitustiedot paallystysilmoitus-kannassa) (:ilmoitustiedot paallystysilmoitus)))))))