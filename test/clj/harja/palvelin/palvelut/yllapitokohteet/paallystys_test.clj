(ns harja.palvelin.palvelut.yllapitokohteet.paallystys-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]
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
                                    :tekninen-toimenpide 1}]}})

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

(deftest hae-paallystysilmoitukset
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        paallystysilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-paallystysilmoitukset +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id})]
    (is (= (count paallystysilmoitukset) 5) "Päällystysilmoituksia löytyi")))

(deftest hae-paallystysilmoitukset
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        paallystysilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-paallystysilmoitukset +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :vuosi 2015})]
    (is (= (count paallystysilmoitukset) 0) "Päällystysilmoituksia ei löydy vuodelle 2015")))

(deftest hae-paallystysilmoitukset
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        paallystysilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-paallystysilmoitukset +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :vuosi 2017})]
    (is (= (count paallystysilmoitukset) 6) "Päällystysilmoituksia löytyi vuodelle 2017")))

(deftest hae-yllapitokohteen-puuttuva-paallystysilmoitus
  ;; Testattavalla ylläpitokohteella ei ole päällystysilmoitusta, mutta palvelu lupaa palauttaa
  ;; silti minimaalisen päällystysilmoituksen, jota käyttäjä voi frontissa lähteä täyttämään
  ;; (erityisesti kohdeosien esitäyttö on tärkeää)
  ;; Testataan, että tällainen "ilmoituspohja" on mahdollista hakea.
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        paallystyskohde-id (hae-yllapitokohde-kuusamontien-testi-jolta-puuttuu-paallystysilmoitus)
        paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :urakan-paallystysilmoitus-paallystyskohteella
                                                    +kayttaja-jvh+ {:urakka-id urakka-id
                                                                    :sopimus-id sopimus-id
                                                                    :paallystyskohde-id paallystyskohde-id})
        kohdeosat (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet])]
    (is (not (nil? paallystysilmoitus-kannassa)))
    (is (nil? (:tila paallystysilmoitus-kannassa)) "Päällystysilmoituksen tila on tyhjä")
    ;; Puuttuvan päällystysilmoituksen kohdeosat esitäytettiin oikein
    (is (= (count kohdeosat) 1))
    (is (every? #(number? (:kohdeosa-id %)) kohdeosat))))

(deftest hae-yllapitokohteen-olemassa-oleva-paallystysilmoitus
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        paallystyskohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :urakan-paallystysilmoitus-paallystyskohteella
                                                    +kayttaja-jvh+ {:urakka-id urakka-id
                                                                    :sopimus-id sopimus-id
                                                                    :paallystyskohde-id paallystyskohde-id})
        kohdeosat (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet])]
    ;; Päällystysilmoituksen perustiedot OK
    (is (not (nil? paallystysilmoitus-kannassa)))
    (is (= (:tila paallystysilmoitus-kannassa) :aloitettu) "Päällystysilmoituksen tila on aloitttu")
    (is (== (:maaramuutokset paallystysilmoitus-kannassa) 205))
    (is (== (:kokonaishinta paallystysilmoitus-kannassa) 5043.95))
    (is (= (:maaramuutokset-ennustettu? paallystysilmoitus-kannassa) true))
    (is (= (:kohdenimi paallystysilmoitus-kannassa) "Leppäjärven ramppi"))
    (is (= (:kohdenumero paallystysilmoitus-kannassa) "L03"))
    ;; Kohdeosat on OK
    (is (= (count kohdeosat) 1))
    (is (= kohdeosat
           [{:kohdeosa-id 666, :tr-kaista 1, :edellinen-paallystetyyppi 2, :lisaaineet "12",
             :leveys 12, :kokonaismassamaara 12, :tr-ajorata 1, :sideainetyyppi 2, :muotoarvo "12",
             :esiintyma "12", :pitoisuus 12, :tr-loppuosa 3, :tunnus nil, :tr-alkuosa 1, :pinta-ala 12,
             :massamenekki 1, :kuulamylly 2, :tr-loppuetaisyys 0, :nimi "Leppäjärven kohdeosa", :raekoko 1,
             :tyomenetelma 21, :rc% 12, :paallystetyyppi 2, :tr-alkuetaisyys 0, :tr-numero 20, :toimenpide nil,
             :km-arvo "12"}]))
    (is (every? #(number? (:kohdeosa-id %)) kohdeosat))))

(deftest tallenna-uusi-paallystysilmoitus-kantaan
  (let [paallystyskohde-id (hae-yllapitokohde-kuusamontien-testi-jolta-puuttuu-paallystysilmoitus)]
    (is (not (nil? paallystyskohde-id)))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi ilmoitus")
    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id
                                                  :valmis-kasiteltavaksi true)
          maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                   :sopimus-id sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus})
      (let [maara-lisayksen-jalkeen (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))
            paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paallystysilmoitus-paallystyskohteella
                                                        +kayttaja-jvh+ {:urakka-id urakka-id
                                                                        :sopimus-id sopimus-id
                                                                        :paallystyskohde-id paallystyskohde-id})]
        (log/debug "Testitallennus valmis. POTTI kannassa: " (pr-str paallystysilmoitus-kannassa))
        (is (not (nil? paallystysilmoitus-kannassa)))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen) "Tallennuksen jälkeen päällystysilmoituksien määrä")
        (is (= (:tila paallystysilmoitus-kannassa) :valmis))
        (is (== (:kokonaishinta paallystysilmoitus-kannassa) 4968))
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
                ;; Päällystysilmoituksen myötä kohteen kohdeosat päivitettiin vastaamaan
                ;; päällystysilmoituksessa mainittuja osia
                :osoitteet [{:edellinen-paallystetyyppi 1
                             :esiintyma "asd"
                             :km-arvo "asd"
                             :kohdeosa-id 14
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
                             :tyomenetelma 12}]}))
        (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";"))))))


(deftest paivita-paallystysilmoitukselle-paatostiedot
  (let [paallystyskohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata
                                   :paallystyskohde-id paallystyskohde-id)
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
                             :kohdeosa-id 14
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
                             :tyomenetelma 12}]}))

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
                      perustelu_tekninen_osa = NULL
                  WHERE paallystyskohde =" paallystyskohde-id ";"))))))

(deftest lisaa-kohdeosa
  (let [paallystyskohde-id (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
          hae-kohteiden-maara #(count (q (str "SELECT * FROM yllapitokohdeosa"
                                   " WHERE poistettu IS NOT TRUE "
                                   " AND yllapitokohde = " paallystyskohde-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :sopimus-id sopimus-id
                       :paallystysilmoitus paallystysilmoitus})

      (let [kohteita-ennen-lisaysta (hae-kohteiden-maara)
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

        (is (= (inc kohteita-ennen-lisaysta) (hae-kohteiden-maara)) "Kohteita on nyt 1 enemmän")))))

(deftest ala-paivita-paallystysilmoitukselle-paatostiedot-jos-ei-oikeuksia
  (let [paallystyskohde-id (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:tekninen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:tekninen-osa :perustelu]
                                           "Yritän saada ilmoituksen hyväksytyksi ilman oikeuksia."))]

      (is (thrown? RuntimeException
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-paallystysilmoitus +kayttaja-tero+
                                   {:urakka-id urakka-id
                                    :sopimus-id sopimus-id
                                    :paallystysilmoitus paallystysilmoitus}))))))
