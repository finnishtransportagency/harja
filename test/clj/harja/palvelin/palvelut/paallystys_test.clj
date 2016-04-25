(ns harja.palvelin.palvelut.paallystys-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapito :refer :all]
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
                        :urakan-yllapitokohteet (component/using
                                                    (->Yllapito)
                                                    [:http-palvelin :db])
                        :urakan-paallystysilmoitus-paallystyskohteella (component/using
                                                                         (->Yllapito)
                                                                         [:http-palvelin :db])
                        :tallenna-paallystysilmoitus (component/using
                                                       (->Yllapito)
                                                       [:http-palvelin :db])
                        :tallenna-paallystyskohde (component/using
                                                    (->Yllapito)
                                                    [:http-palvelin :db])
                        :tallenna-yllapitokohdeosat (component/using
                                                        (->Yllapito)
                                                        [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(def pot-testidata
  {:aloituspvm     (pvm/luo-pvm 2005 9 1)
   :valmispvm_kohde (pvm/luo-pvm 2005 9 2)
   :valmispvm_paallystys (pvm/luo-pvm 2005 9 2)
   :takuupvm       (pvm/luo-pvm 2005 9 3)
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
                                    :edellinen-paallystetyyppi 1}
                                   {:tie                       1
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
                                    :edellinen-paallystetyyppi 1
                                    :poistettu                 true}]

                    :kiviaines    [{:esiintyma      "asd"
                                    :km-arvo        "asd"
                                    :muotoarvo      "asd"
                                    :sideainetyyppi "asd"
                                    :pitoisuus      54
                                    :lisaaineet     "asd"}]

                    :alustatoimet [{:aosa                2
                                    :aet                 3
                                    :losa                4
                                    :let                 5
                                    :kasittelymenetelma  1
                                    :paksuus             1234
                                    :verkkotyyppi        1
                                    :tekninen-toimenpide 1
                                    }]

                    :tyot         [{:tyyppi           :ajoradan-paallyste
                                    :tyo              "AB 16/100 LTA"
                                    :tilattu-maara    100
                                    :toteutunut-maara 200
                                    :yksikko          "km"
                                    :yksikkohinta     5}]
                    }})

(def paallystyskohde-testidata {:kohdenumero              999
                                :nimi                     "Testiramppi4564ddf"
                                :sopimuksen_mukaiset_tyot 400
                                :lisatyo                  false
                                :bitumi_indeksi           123
                                :kaasuindeksi             123})

(def paallystyskohdeosa-testidata {:nimi               "Testiosa123456"
                                   :tr_numero          1234
                                   :tr_alkuosa         3
                                   :tr_alkuetaisyys    1
                                   :tr_loppuosa        123
                                   :tr_loppuetaisyys   1
                                   :sijainti {:type :multiline :lines [{:type :line :points [[1 2] [3 4]]}]}
                                   :kvl                4
                                   :nykyinen_paallyste 2
                                   :toimenpide         "Ei tehdä mitään"})


(def paallystyskohde-id-jolla-ei-ilmoitusta (ffirst (q (str "
                                                           SELECT paallystyskohde.id as paallystyskohde_id
                                                           FROM paallystyskohde
                                                           FULL OUTER JOIN paallystysilmoitus ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                                           WHERE paallystysilmoitus.id IS NULL
                                                           AND urakka = " (hae-muhoksen-paallystysurakan-id) "
                                                           AND sopimus = " (hae-muhoksen-paallystysurakan-paasopimuksen-id) ";"))))
(log/debug "Päällystyskohde id ilman ilmoitusta: " paallystyskohde-id-jolla-ei-ilmoitusta)

(def paallystyskohde-id-jolla-on-ilmoitus (ffirst (q (str "
                                                           SELECT paallystyskohde.id as paallystyskohde_id
                                                           FROM paallystyskohde
                                                           JOIN paallystysilmoitus ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                                           WHERE urakka = " (hae-muhoksen-paallystysurakan-id) " AND sopimus = " (hae-muhoksen-paallystysurakan-paasopimuksen-id) ";"))))
(log/debug "Päällystyskohde id jolla on ilmoitus: " paallystyskohde-id-jolla-on-ilmoitus)

(deftest paallystyskohteet-haettu-oikein
  (let [res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-yllapitokohteet +kayttaja-jvh+
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
                                 (assoc-in [:ilmoitustiedot :ylimaarainen-keyword] "Huonoa dataa, jota ei saa päästää kantaan."))
          maara-ennen-pyyntoa (ffirst (q
                                        (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :tallenna-paallystysilmoitus
                                                    +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                    :sopimus-id         sopimus-id
                                                                    :paallystysilmoitus paallystysilmoitus})))
      (let [maara-pyynnon-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
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
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                   :sopimus-id         sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus})
      (let [maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*) FROM paallystysilmoitus
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystysilmoitus.paallystyskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))
            paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paallystysilmoitus-paallystyskohteella
                                                        +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                        :sopimus-id         sopimus-id
                                                                        :paallystyskohde-id paallystyskohde-id})]
        (log/debug "POTTI kannassa: " (pr-str paallystysilmoitus-kannassa))
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
                                 (assoc :perustelu_tekninen_osa "Hyvä ilmoitus!"))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                   :sopimus-id         sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus})
      (let [paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-paallystysilmoitus-paallystyskohteella
                                                        +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                        :sopimus-id         sopimus-id
                                                                        :paallystyskohde-id paallystyskohde-id})]
        (log/debug "POTTI kannassa: " (pr-str paallystysilmoitus-kannassa))
        (is (not (nil? paallystysilmoitus-kannassa)))
        (is (= (:tila paallystysilmoitus-kannassa) :lukittu))
        (is (= (:paatos_tekninen_osa paallystysilmoitus-kannassa) :hyvaksytty))
        (is (= (:paatos_taloudellinen_osa paallystysilmoitus-kannassa) :hyvaksytty))
        (is (= (:perustelu_tekninen_osa paallystysilmoitus-kannassa) (:perustelu_tekninen_osa paallystysilmoitus)))
        (is (= (:ilmoitustiedot paallystysilmoitus-kannassa) (:ilmoitustiedot paallystysilmoitus)))

        ; Lukittu, ei voi enää päivittää
        (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                                                   :sopimus-id         sopimus-id
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
                                 (assoc :paatos_taloudellinen_osa :hyvaksytty)
                                 (assoc :paatos_tekninen_osa :hyvaksytty)
                                 (assoc :perustelu_tekninen_osa "Yritän saada ilmoituksen hyväksytyksi ilman oikeuksia."))]

      (is (thrown? RuntimeException (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-tero+ {:urakka-id          urakka-id
                                                                   :sopimus-id         sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus}))))))

(deftest tallenna-paallystyskohde-kantaan
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM paallystyskohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-paallystyskohteet +kayttaja-jvh+ {:urakka-id  urakka-id
                                                                :sopimus-id sopimus-id
                                                                :kohteet    [paallystyskohde-testidata]})
    (let [maara-lisayksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM paallystyskohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
          kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :urakan-yllapitokohteet
                                         +kayttaja-jvh+ {:urakka-id  urakka-id
                                                         :sopimus-id sopimus-id})]
      (log/debug "Kohteet kannassa: " (pr-str kohteet-kannassa))
      (is (not (nil? kohteet-kannassa)))
      (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))
      (u (str "DELETE FROM paallystyskohde WHERE nimi = 'Testiramppi4564ddf';")))))

(deftest tallenna-paallystyskohdeosa-kantaan
  (let [paallystyskohde-id paallystyskohde-id-jolla-on-ilmoitus]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
          maara-ennen-lisaysta (ffirst (q
                                         (str "SELECT count(*) FROM paallystyskohdeosa
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystyskohdeosa.paallystyskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-yllapitokohdeosat +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                   :sopimus-id         sopimus-id
                                                                   :paallystysilmoitus paallystysilmoitus
                                                                   :paallystyskohde-id paallystyskohde-id
                                                                   :osat [paallystyskohdeosa-testidata]})
      (let [maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*) FROM paallystyskohdeosa
                                            LEFT JOIN paallystyskohde ON paallystyskohde.id = paallystyskohdeosa.paallystyskohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))
            kohdeosat-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :urakan-yllapitokohdeosat
                                                        +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                        :sopimus-id         sopimus-id
                                                                        :paallystyskohde-id paallystyskohde-id})]
        (log/debug "Kohdeosa kannassa: " (pr-str kohdeosat-kannassa))
        (is (not (nil? kohdeosat-kannassa)))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))
        (u (str "DELETE FROM paallystyskohdeosa WHERE nimi = 'Testiosa123456';"))))))

(deftest tallenna-paallystysurakan-aikataulut
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM paallystyskohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        kohteet [{:kohdenumero                 "L03", :aikataulu_paallystys_alku (pvm/->pvm-aika "19.5.2016 12:00") :aikataulu_muokkaaja 2, :urakka 5,
                  :aikataulu_kohde_valmis      (pvm/->pvm "29.5.2016"), :nimi "Leppäjärven ramppi",
                  :valmis_tiemerkintaan        (pvm/->pvm-aika "23.5.2016 12:00"), :aikataulu_paallystys_loppu (pvm/->pvm-aika "20.5.2016 12:00"),
                  :id                          1, :sopimus 8, :aikataulu_muokattu (pvm/->pvm-aika "29.5.2016 12:00"), :aikataulu_tiemerkinta_alku nil,
                  :aikataulu_tiemerkinta_loppu (pvm/->pvm "26.5.2016")}]
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-paallystyskohteiden-aikataulu +kayttaja-jvh+ {:urakka-id  urakka-id
                                                                                        :sopimus-id sopimus-id
                                                                                        :kohteet    kohteet})
        maara-paivityksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM paallystyskohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        vastaus-leppajarven-ramppi (first (filter #(= "L03" (:kohdenumero %)) vastaus))
        odotettu {:aikataulu_kohde_valmis       (pvm/->pvm "29.5.2016")
                  :aikataulu_muokkaaja         2
                  :aikataulu_paallystys_alku   (pvm/->pvm-aika "19.5.2016 12:00")
                  :aikataulu_paallystys_loppu   (pvm/->pvm-aika "20.5.2016 12:00")
                  :aikataulu_tiemerkinta_alku  nil
                  :aikataulu_tiemerkinta_loppu (pvm/->pvm "26.5.2016")
                  :id                          1
                  :kohdenumero                 "L03"
                  :nimi                        "Leppäjärven ramppi"
                  :sopimus                     8
                  :urakka                      5
                  :valmis_tiemerkintaan       (pvm/->pvm-aika "23.5.2016 12:00")}]
    (is (= maara-ennen-lisaysta maara-paivityksen-jalkeen (count vastaus)))
    (is (= (:aikataulu_paallystys_alku odotettu) (:aikataulu_paallystys_alku vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_paallystys_alku")
    (is (= (:aikataulu_paallystys_loppu odotettu) (:aikataulu_paallystys_loppu vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_paallystys_loppu")
    (is (= (:aikataulu_tiemerkinta_alku odotettu) (:aikataulu_tiemerkinta_alku vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_tiemerkinta_alku")
    (is (= (:aikataulu_tiemerkinta_loppu odotettu) (:aikataulu_tiemerkinta_loppu vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_tiemerkinta_loppu")
    (is (= (:aikataulu_kohde_valmis odotettu) (:aikataulu_kohde_valmis vastaus-leppajarven-ramppi) ) "päällystyskohteen :aikataulu_kohde_valmis")))
