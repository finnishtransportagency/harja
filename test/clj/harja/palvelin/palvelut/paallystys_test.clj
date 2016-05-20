(ns harja.palvelin.palvelut.paallystys-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.paallystys :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [cheshire.core :as cheshire]
            [harja.pvm :as pvm]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :urakan-paallystysilmoitus-paallystyskohteella (component/using
                                                                         (->Paallystys)
                                                                         [:http-palvelin :db])
                        :tallenna-paallystysilmoitus (component/using
                                                       (->Paallystys)
                                                       [:http-palvelin :db])
                        :tallenna-paallystyskohde (component/using
                                                    (->Paallystys)
                                                    [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def pot-testidata
  {:aloituspvm (pvm/luo-pvm 2005 9 1)
   :valmispvm_kohde (pvm/luo-pvm 2005 9 2)
   :valmispvm_paallystys (pvm/luo-pvm 2005 9 2)
   :takuupvm (pvm/luo-pvm 2005 9 3)
   :muutoshinta 0
   :ilmoitustiedot {:osoitteet [{:nimi "Tie 666"
                                 :tie 666
                                 :aosa 2
                                 :aet 3
                                 :losa 4
                                 :let 5
                                 :ajorata 1
                                 :kaista 1
                                 :paallystetyyppi 1
                                 :raekoko 1
                                 :massa 2
                                 :rc% 3
                                 :tyomenetelma 12
                                 :leveys 5
                                 :massamaara 7
                                 :pinta-ala 8
                                 :edellinen-paallystetyyppi 1}
                                {:nimi "Tie 555"
                                 :tie 555
                                 :aosa 2
                                 :aet 3
                                 :losa 4
                                 :let 5
                                 :ajorata 1
                                 :kaista 1
                                 :paallystetyyppi 1
                                 :raekoko 1
                                 :massa 2
                                 :rc% 3
                                 :tyomenetelma 12
                                 :leveys 5
                                 :massamaara 7
                                 :pinta-ala 8
                                 :edellinen-paallystetyyppi 1
                                 :poistettu true}]

                    :kiviaines [{:esiintyma "asd"
                                 :km-arvo "asd"
                                 :muotoarvo "asd"
                                 :sideainetyyppi "asd"
                                 :pitoisuus 54
                                 :lisaaineet "asd"}]

                    :alustatoimet [{:aosa 2
                                    :aet 3
                                    :losa 4
                                    :let 5
                                    :kasittelymenetelma 1
                                    :paksuus 1234
                                    :verkkotyyppi 1
                                    :verkon-sijainti 1
                                    :verkon-tarkoitus 1
                                    :tekninen-toimenpide 1}]

                    :tyot [{:tyyppi :ajoradan-paallyste
                            :tyo "AB 16/100 LTA"
                            :tilattu-maara 100
                            :toteutunut-maara 200
                            :yksikko "km"
                            :yksikkohinta 5}]}})

(def paallystyskohde-id-jolla-ei-ilmoitusta (ffirst (q (str "
                                                           SELECT yllapitokohde.id as paallystyskohde_id
                                                           FROM yllapitokohde
                                                           FULL OUTER JOIN paallystysilmoitus ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                                                           WHERE paallystysilmoitus.id IS NULL
                                                           AND urakka = " (hae-muhoksen-paallystysurakan-id) "
                                                           AND sopimus = " (hae-muhoksen-paallystysurakan-paasopimuksen-id) ";"))))
(log/debug "Päällystyskohde id ilman ilmoitusta: " paallystyskohde-id-jolla-ei-ilmoitusta)

(def paallystyskohde-id-jolla-on-ilmoitus (ffirst (q (str "
                                                           SELECT yllapitokohde.id as paallystyskohde_id
                                                           FROM yllapitokohde
                                                           JOIN paallystysilmoitus ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                                                           WHERE urakka = " (hae-muhoksen-paallystysurakan-id) " AND sopimus = " (hae-muhoksen-paallystysurakan-paasopimuksen-id) ";"))))
(log/debug "Päällystyskohde id jolla on ilmoitus: " paallystyskohde-id-jolla-on-ilmoitus)

(deftest skeemavalidointi-toimii
  (let [paallystyskohde-id paallystyskohde-id-jolla-ei-ilmoitusta]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:ilmoitustiedot :ylimaarainen-keyword] "Huonoa dataa, jota ei saa päästää kantaan."))
          maara-ennen-pyyntoa (ffirst (q
                                        (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :tallenna-paallystysilmoitus
                                                    +kayttaja-jvh+ {:urakka-id urakka-id
                                                                    :sopimus-id sopimus-id
                                                                    :paallystysilmoitus paallystysilmoitus})))
      (let [maara-pyynnon-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]
        (is (= maara-ennen-pyyntoa maara-pyynnon-jalkeen))))))

(deftest tallenna-uusi-paallystysilmoitus-kantaan
  (let [paallystyskohde-id paallystyskohde-id-jolla-ei-ilmoitusta]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
          maara-ennen-lisaysta (ffirst (q
                                         (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                   :sopimus-id sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus})
      (let [maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))
            paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paallystysilmoitus-paallystyskohteella
                                                        +kayttaja-jvh+ {:urakka-id urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :paallystyskohde-id paallystyskohde-id})]
        (log/debug "POTTI kannassa: " (pr-str paallystysilmoitus-kannassa))
        (log/debug "Muutoshinta: " (:muutoshinta paallystysilmoitus-kannassa))
        (log/debug "Muutoshinta tyyppi: " (type (:muutoshinta paallystysilmoitus-kannassa)))
        (is (not (nil? paallystysilmoitus-kannassa)))
        #_(is (= (:tila paallystysilmoitus-kannassa) :valmis))
        #_(is (= (:muutoshinta paallystysilmoitus-kannassa) (java.math.BigDecimal. 500)))
        #_(is (= (:ilmoitustiedot paallystysilmoitus-kannassa) (:ilmoitustiedot paallystysilmoitus)))
        #_(is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen) "Tallennuksen jälkeen päällystysilmoituksien määrä")
        (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";"))))))


(deftest paivita-paallystysilmoitukselle-paatostiedot
  (let [paallystyskohde-id paallystyskohde-id-jolla-on-ilmoitus]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc :paatos-taloudellinen-osa :hyvaksytty)
                                 (assoc :paatos-tekninen-osa :hyvaksytty)
                                 (assoc :perustelu-tekninen-osa "Hyvä ilmoitus!"))
          alikohteet-maara-ennen-tallennusta (ffirst (q
                                                       (str "SELECT count(*) FROM yllapitokohdeosa
                                                       WHERE yllapitokohde = " paallystyskohde-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                   :sopimus-id sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus})
      (let [paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paallystysilmoitus-paallystyskohteella
                                                        +kayttaja-jvh+ {:urakka-id urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :paallystyskohde-id paallystyskohde-id})
            alikohteet-maara-tallennuksen-jalkeen (ffirst (q
                                                         (str "SELECT count(*) FROM yllapitokohdeosa
                                                       WHERE yllapitokohde = " paallystyskohde-id ";")))]
        (is (not (nil? paallystysilmoitus-kannassa)))
        (is (= (:tila paallystysilmoitus-kannassa) :lukittu))
        (is (= (:paatos-tekninen-osa paallystysilmoitus-kannassa) :hyvaksytty))
        (is (= (:paatos-taloudellinen-osa paallystysilmoitus-kannassa) :hyvaksytty))
        (is (= (:perustelu-tekninen-osa paallystysilmoitus-kannassa) (:perustelu-tekninen-osa paallystysilmoitus)))

        ;; Tie 666 tiedot tallentuivat kantaan, mutta tie 555 ei koska oli poistettu
        (is (some #(= (:nimi %) "Tie 666")
                  (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet])))
        (is (not (some #(= (:nimi %) "Tie 555")
                   (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet]))))
        (let [toimenpide-avaimet [:paallystetyyppi :raekoko :massa :rc% :tyomenetelma
                                  :leveys :massamaara :pinta-ala :edellinen-paallystetyyppi]
              tie-avaimet [:nimi :tie :aosa :aet :losa :let :kaista :ajorata]]
          ;; Lisättiin yksi alikohde uutena. Toista ei lisätty, koska se oli merkitty poistetuksi
          (is (= alikohteet-maara-ennen-tallennusta (- alikohteet-maara-tallennuksen-jalkeen 1)))
          ;; Tien tietoja ei tallennettu ilmoitukseen, menevät yllapitokohdeosa-tauluun
          (is (= (select-keys (:ilmoitustiedot paallystysilmoitus-kannassa) toimenpide-avaimet)
                 (select-keys (:ilmoitustiedot paallystysilmoitus) toimenpide-avaimet)))
          (is (empty? (select-keys (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet]) tie-avaimet))))

        ; Lukittu, ei voi enää päivittää
        (log/debug "Tarkistetaan, ettei voi muokata lukittua ilmoitusta.")
        (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                                   :sopimus-id sopimus-id
                                                                                                   :paallystysilmoitus paallystysilmoitus})))

        (u (str "UPDATE paallystysilmoitus SET
                      tila = NULL,
                      paatos_tekninen_osa = NULL,
                      paatos_taloudellinen_osa = NULL,
                      perustelu_tekninen_osa = NULL
                  WHERE paallystyskohde =" paallystyskohde-id ";"))))))

(deftest ala-paivita-paallystysilmoitukselle-paatostiedot-jos-ei-oikeuksia
  (let [paallystyskohde-id paallystyskohde-id-jolla-on-ilmoitus]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc :paatos-taloudellinen-osa :hyvaksytty)
                                 (assoc :paatos-tekninen-osa :hyvaksytty)
                                 (assoc :perustelu-tekninen-osa "Yritän saada ilmoituksen hyväksytyksi ilman oikeuksia."))]

      (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :tallenna-paallystysilmoitus +kayttaja-tero+ {:urakka-id urakka-id
                                                                                                  :sopimus-id sopimus-id
                                                                                                  :paallystysilmoitus paallystysilmoitus}))))))