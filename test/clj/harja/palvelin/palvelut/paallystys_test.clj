(ns harja.palvelin.palvelut.paallystys-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.paallystys :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [cheshire.core :as cheshire]
            [harja.pvm :as pvm]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.skeema :as skeema]))

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


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def pot-testidata
  {:aloituspvm (pvm/luo-pvm 2005 9 1)
   :valmispvm-kohde (pvm/luo-pvm 2005 9 2)
   :valmispvm-paallystys (pvm/luo-pvm 2005 9 2)
   :takuupvm (pvm/luo-pvm 2005 9 3)
   :muutoshinta 0
   :ilmoitustiedot {:osoitteet [{:nimi "Tie 666"
                                 :tr-numero 666
                                 :tr-alkuosa 2
                                 :tr-alkuetaisyys 3
                                 :tr-loppuosa 4
                                 :tr-loppuetaisyys 5
                                 :tr-ajorata 1
                                 :tr-kaista 1
                                 :paallystetyyppi 1
                                 :raekoko 1
                                 :kokonaismassamaara 2
                                 :rc% 3
                                 :tyomenetelma 12
                                 :leveys 5
                                 :massamenekki 7
                                 :pinta-ala 8
                                 :edellinen-paallystetyyppi 1
                                 :esiintyma "asd"
                                 :km-arvo "asd"
                                 :muotoarvo "asd"
                                 :sideainetyyppi 1
                                 :pitoisuus 54
                                 :lisaaineet "asd"}
                                {:poistettu true ;; HUOMAA POISTETTU, EI SAA TALLENTUA!
                                 :nimi "Tie 555"
                                 :tr-numero 555
                                 :tr-alkuosa 2
                                 :tr-alkuetaisyys 3
                                 :tr-loppuosa 4
                                 :tr-loppuetaisyys 5
                                 :tr-ajorata 1
                                 :tr-kaista 1
                                 :paallystetyyppi 1
                                 :raekoko 1
                                 :kokonaismassamaara 2
                                 :rc% 3
                                 :tyomenetelma 12
                                 :leveys 5
                                 :massamenekki 7
                                 :pinta-ala 8
                                 :edellinen-paallystetyyppi 1
                                 :esiintyma "asd"
                                 :km-arvo "asd"
                                 :muotoarvo "asd"
                                 :sideainetyyppi 1
                                 :pitoisuus 54
                                 :lisaaineet "asd"}]

                    :alustatoimet [{:tr-alkuosa 2
                                    :tr-alkuetaisyys 3
                                    :tr-loppuosa 4
                                    :tr-loppuetaisyys 5
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

(deftest skeemavalidointi-toimii
  (let [paallystyskohde-id (hae-muhoksen-yllapitokohde-jolla-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:ilmoitustiedot :ylimaarainen-keyword]
                                           "Huonoa dataa, jota ei saa päästää kantaan."))
          maara-ennen-pyyntoa
          (ffirst
            (q
              (str "SELECT count(*) FROM paallystysilmoitus"
                   " LEFT JOIN yllapitokohde ON yllapitokohde.id = paallystysilmoitus.paallystyskohde"
                   " AND urakka = " urakka-id
                   " AND sopimus = " sopimus-id ";")))]

      (is (thrown? RuntimeException
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-paallystysilmoitus
                                   +kayttaja-jvh+ {:urakka-id urakka-id
                                                   :sopimus-id sopimus-id
                                                   :paallystysilmoitus paallystysilmoitus})))
      (let [maara-pyynnon-jalkeen
            (ffirst
              (q
                (str "SELECT count(*) FROM paallystysilmoitus"
                     " LEFT JOIN yllapitokohde ON yllapitokohde.id = paallystysilmoitus.paallystyskohde"
                     " AND urakka = " urakka-id
                     " AND sopimus = " sopimus-id ";")))]
        (is (= maara-ennen-pyyntoa maara-pyynnon-jalkeen))))))

(deftest testidata-on-validia
  ;; On kiva jos testaamme näkymää ja meidän testidata menee validoinnista läpi
  (let [ilmoitustiedot (q "SELECT ilmoitustiedot FROM paallystysilmoitus")]
    (doseq [[ilmoitusosa] ilmoitustiedot]
      (is (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+
                          (konv/jsonb->clojuremap ilmoitusosa))))))

(deftest tallenna-uusi-paallystysilmoitus-kantaan
  (let [paallystyskohde-id (hae-yllapitokohde-kuusamontien-testi-jolta-puuttuu-paallystysilmoitus)]
    (is (not (nil? paallystyskohde-id)))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi ilmoitus")
    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
          maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                   :sopimus-id sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus})
      (let [maara-lisayksen-jalkeen (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))
            muutoshinta (ffirst (q (str "SELECT muutoshinta FROM paallystysilmoitus WHERE paallystyskohde = (SELECT id FROM yllapitokohde WHERE id =" paallystyskohde-id ");")))
            paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paallystysilmoitus-paallystyskohteella
                                                        +kayttaja-jvh+ {:urakka-id urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :paallystyskohde-id paallystyskohde-id})]
        (log/debug "Testitallennus valmis. POTTI kannassa: " (pr-str paallystysilmoitus-kannassa))
        (is (not (nil? paallystysilmoitus-kannassa)))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen) "Tallennuksen jälkeen päällystysilmoituksien määrä")
        (is (= (:tila paallystysilmoitus-kannassa) :valmis))
        (is (= (:muutoshinta paallystysilmoitus-kannassa) muutoshinta))
        (is (= (:ilmoitustiedot paallystysilmoitus-kannassa)
               {:alustatoimet [{:kasittelymenetelma 1
                                :paksuus 1234
                                :tekninen-toimenpide 1
                                :tr-alkuetaisyys 3
                                :tr-alkuosa 2
                                :tr-loppuetaisyys 5
                                :tr-loppuosa 4
                                :verkkotyyppi 1
                                :verkon-sijainti 1
                                :verkon-tarkoitus 1}]
                :osoitteet [{:edellinen-paallystetyyppi 1
                             :esiintyma "asd"
                             :km-arvo "asd"
                             :kohdeosa-id 18
                             :kokonaismassamaara 2
                             :leveys 5
                             :lisaaineet "asd"
                             :massamenekki 7
                             :muotoarvo "asd"
                             :nimi "Tie 666"
                             :paallystetyyppi 1
                             :pinta-ala 8
                             :pitoisuus 54
                             :raekoko 1
                             :rc% 3
                             :sideainetyyppi 1
                             :toimenpide nil
                             :tr-ajorata 1
                             :tr-alkuetaisyys 3
                             :tr-alkuosa 2
                             :tr-kaista 1
                             :tr-loppuetaisyys 5
                             :tr-loppuosa 4
                             :tr-numero 666
                             :tunnus nil
                             :tyomenetelma 12}]
                :tyot [{:tilattu-maara 100
                        :toteutunut-maara 200
                        :tyo "AB 16/100 LTA"
                        :tyyppi :ajoradan-paallyste
                        :yksikko "km"
                        :yksikkohinta 5}]}))
        (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";"))))))


(deftest paivita-paallystysilmoitukselle-paatostiedot
  (let [paallystyskohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:taloudellinen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:tekninen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:tekninen-osa :perustelu] "Hyvä ilmoitus!"))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :sopimus-id sopimus-id
                       :paallystysilmoitus paallystysilmoitus})
      (let [paallystysilmoitus-kannassa
            (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-paallystysilmoitus-paallystyskohteella +kayttaja-jvh+
                            {:urakka-id urakka-id
                             :sopimus-id sopimus-id
                             :paallystyskohde-id paallystyskohde-id})]
        (is (not (nil? paallystysilmoitus-kannassa)))
        (is (= (:tila paallystysilmoitus-kannassa) :lukittu))
        (is (= (get-in paallystysilmoitus-kannassa [:tekninen-osa :paatos]) :hyvaksytty))
        (is (= (get-in paallystysilmoitus-kannassa [:taloudellinen-osa :paatos]) :hyvaksytty))
        (is (= (get-in paallystysilmoitus-kannassa [:tekninen-osa :perustelu])
               (get-in paallystysilmoitus [:tekninen-osa :perustelu])))

        ;; Tie 666 tiedot tallentuivat kantaan, mutta tie 555 ei koska oli poistettu
        (is (some #(= (:nimi %) "Tie 666")
                  (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet])))
        (is (not (some #(= (:nimi %) "Tie 555")
                       (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet]))))
        (is (= (:ilmoitustiedot paallystysilmoitus-kannassa)
               {:alustatoimet [{:kasittelymenetelma 1
                                :paksuus 1234
                                :tekninen-toimenpide 1
                                :tr-alkuetaisyys 3
                                :tr-alkuosa 2
                                :tr-loppuetaisyys 5
                                :tr-loppuosa 4
                                :verkkotyyppi 1
                                :verkon-sijainti 1
                                :verkon-tarkoitus 1}]
                :osoitteet [{:edellinen-paallystetyyppi 1
                             :esiintyma "asd"
                             :km-arvo "asd"
                             :kohdeosa-id 18
                             :kokonaismassamaara 2
                             :leveys 5
                             :lisaaineet "asd"
                             :massamenekki 7
                             :muotoarvo "asd"
                             :nimi "Tie 666"
                             :paallystetyyppi 1
                             :pinta-ala 8
                             :pitoisuus 54
                             :raekoko 1
                             :rc% 3
                             :sideainetyyppi 1
                             :toimenpide nil
                             :tr-ajorata 1
                             :tr-alkuetaisyys 3
                             :tr-alkuosa 2
                             :tr-kaista 1
                             :tr-loppuetaisyys 5
                             :tr-loppuosa 4
                             :tr-numero 666
                             :tunnus nil
                             :tyomenetelma 12}]
                :tyot [{:tilattu-maara 100
                        :toteutunut-maara 200
                        :tyo "AB 16/100 LTA"
                        :tyyppi :ajoradan-paallyste
                        :yksikko "km"
                        :yksikkohinta 5}]}))

        ; Lukittu, ei voi enää päivittää
        (log/debug "Tarkistetaan, ettei voi muokata lukittua ilmoitusta.")
        (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                       :tallenna-paallystysilmoitus +kayttaja-jvh+
                                                       {:urakka-id urakka-id
                                                        :sopimus-id sopimus-id
                                                        :paallystysilmoitus paallystysilmoitus})))

        (u (str "UPDATE paallystysilmoitus SET
                      tila = NULL,
                      paatos_tekninen_osa = NULL,
                      paatos_taloudellinen_osa = NULL,
                      perustelu_tekninen_osa = NULL
                  WHERE paallystyskohde =" paallystyskohde-id ";"))))))

(deftest lisaa-kohdeosa
  (let [paallystyskohde-id (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
          kohteita #(count (q (str "SELECT * FROM yllapitokohdeosa"
                                   " WHERE poistettu IS NOT TRUE "
                                   " AND yllapitokohde = " paallystyskohde-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :sopimus-id sopimus-id
                       :paallystysilmoitus paallystysilmoitus})

      (let [kohteita-ennen-lisaysta (kohteita)
            paallystysilmoitus (update-in paallystysilmoitus [:ilmoitustiedot :osoitteet]
                                          conj {:nimi "Tie 4242"
                                                :tr-numero 4242
                                                :tr-alkuosa 5
                                                :tr-alkuetaisyys 6
                                                :tr-loppuosa 7
                                                :tr-loppuetaisyys 8
                                                :tr-ajorata 1
                                                :tr-kaista 1
                                                :paallystetyyppi 1
                                                :raekoko 1
                                                :kokonaismassamaara 2
                                                :rc% 3
                                                :tyomenetelma 12
                                                :leveys 5
                                                :massamenekki 7
                                                :pinta-ala 8
                                                :edellinen-paallystetyyppi 1
                                                :esiintyma "asd"
                                                :km-arvo "asd"
                                                :muotoarvo "asd"
                                                :sideainetyyppi 1
                                                :pitoisuus 54
                                                :lisaaineet "asd"})]

        (is (= 1 kohteita-ennen-lisaysta))

        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-paallystysilmoitus +kayttaja-jvh+
                        {:urakka-id urakka-id
                         :sopimus-id sopimus-id
                         :paallystysilmoitus paallystysilmoitus})

        (is (= (inc kohteita-ennen-lisaysta) (kohteita)) "Kohteita on nyt 1 enemmän")))))

(deftest ala-paivita-paallystysilmoitukselle-paatostiedot-jos-ei-oikeuksia
  (let [paallystyskohde-id (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:taloudellinen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:tekninen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:tekninen-osa :perustelu]
                                           "Yritän saada ilmoituksen hyväksytyksi ilman oikeuksia."))]

      (is (thrown? RuntimeException
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-paallystysilmoitus +kayttaja-tero+
                                   {:urakka-id urakka-id
                                    :sopimus-id sopimus-id
                                    :paallystysilmoitus paallystysilmoitus}))))))
