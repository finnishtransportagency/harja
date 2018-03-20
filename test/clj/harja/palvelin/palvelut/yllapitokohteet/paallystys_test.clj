(ns harja.palvelin.palvelut.yllapitokohteet.paallystys-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [cheshire.core :as cheshire]
            [harja.domain.urakka :as urakka-domain]
            [harja.domain.sopimus :as sopimus-domain]
            [harja.domain.paallystysilmoitus :as pot-domain]
            [harja.domain.paallystyksen-maksuerat :as paallystyksen-maksuerat-domain]
            [harja.pvm :as pvm]
            [harja.domain.skeema :as skeema]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet-test :as yllapitokohteet-test]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.tyokalut.xml :as xml])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pois-kytketyt-ominaisuudet testi-pois-kytketyt-ominaisuudet
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :sonja (feikki-sonja)
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :paallystys (component/using
                                      (paallystys/->Paallystys)
                                      [:http-palvelin :db :fim :sonja-sahkoposti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each
              urakkatieto-fixture
              jarjestelma-fixture)

(def pot-testidata
  {:aloituspvm (pvm/luo-pvm 2005 9 1)
   :valmispvm-kohde (pvm/luo-pvm 2005 9 2)
   :valmispvm-paallystys (pvm/luo-pvm 2005 9 2)
   :takuupvm (pvm/luo-pvm 2005 9 3)
   :ilmoitustiedot {:osoitteet [{;; Alikohteen tiedot
                                 :nimi "Tie 20"
                                 :tr-numero 20
                                 :tr-alkuosa 26
                                 :tr-alkuetaisyys 3
                                 :tr-loppuosa 27
                                 :tr-loppuetaisyys 5
                                 :tr-ajorata 1
                                 :tr-kaista 1
                                 :paallystetyyppi 1
                                 :raekoko 1
                                 :tyomenetelma 12
                                 :massamaara 2
                                 :toimenpide "Wut"
                                 ;; Päällystetoimenpiteen tiedot
                                 :toimenpide-paallystetyyppi 1
                                 :toimenpide-raekoko 1
                                 :kokonaismassamaara 2
                                 :rc% 3
                                 :toimenpide-tyomenetelma 12
                                 :leveys 5
                                 :massamenekki 7
                                 :pinta-ala 8
                                 ;; Kiviaines- ja sideainetiedot
                                 :esiintyma "asd"
                                 :km-arvo "asd"
                                 :muotoarvo "asd"
                                 :sideainetyyppi 1
                                 :pitoisuus 54
                                 :lisaaineet "asd"}
                                {;; Alikohteen tiedot
                                 :poistettu true ;; HUOMAA POISTETTU, EI SAA TALLENTUA!
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
                                 :tyomenetelma 12
                                 :massamaara 2
                                 :toimenpide "Emt"
                                 ;; Päällystetoimenpiteen tiedot
                                 :toimenpide-paallystetyyppi 1
                                 :toimenpide-raekoko 1
                                 :kokonaismassamaara 2
                                 :rc% 3
                                 :toimenpide-tyomenetelma 12
                                 :leveys 5
                                 :massamenekki 7
                                 :pinta-ala 8
                                 ;; Kiviaines- ja sideainetiedot
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

(deftest skeemavalidointi-toimii-ilman-kaikkia-avaimia
  (let [paallystyskohde-id (hae-muhoksen-yllapitokohde-jolla-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc :ilmoitustiedot
                                        {:osoitteet [{;; Alikohteen tiedot
                                                      :nimi "Tie 20"
                                                      :tr-numero 20
                                                      :tr-alkuosa 26
                                                      :tr-alkuetaisyys 3
                                                      :tr-loppuosa 27
                                                      :tr-loppuetaisyys 5
                                                      :tr-ajorata 1
                                                      :tr-kaista 1
                                                      :paallystetyyppi 1
                                                      :raekoko 1
                                                      :tyomenetelma 12
                                                      :massamaara 2
                                                      :toimenpide "Wut"
                                                      ;; Päällystetoimenpiteen tiedot
                                                      :toimenpide-paallystetyyppi 1
                                                      :toimenpide-raekoko 1
                                                      :kokonaismassamaara 2
                                                      :rc% 3
                                                      :toimenpide-tyomenetelma 12
                                                      :leveys 5
                                                      :massamenekki 7
                                                      :pinta-ala 8
                                                      ;; Kiviaines- ja sideainetiedot
                                                      :esiintyma "asd"
                                                      :km-arvo "asd"
                                                      :muotoarvo "asd"
                                                      :sideainetyyppi nil ;; Sideainetyyppi on annettu, mutta arvo nil (ei sideainetta)
                                                      :pitoisuus 54
                                                      :lisaaineet "asd"}]

                                         :alustatoimet [{:tr-alkuosa 2
                                                         :tr-alkuetaisyys 3
                                                         :tr-loppuosa 4
                                                         :tr-loppuetaisyys 5
                                                         :kasittelymenetelma 1
                                                         :paksuus 1234
                                                         :tekninen-toimenpide 1
                                                         ;; Verkkoon liittyvät avaimet puuttuu kokonaan, arvoja ei siis annettu
                                                         }]}))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus
                      +kayttaja-jvh+ {:urakka-id urakka-id
                                      :sopimus-id sopimus-id
                                      :paallystysilmoitus paallystysilmoitus}))))

(deftest testidata-on-validia
  ;; On kiva jos testaamme näkymää ja meidän testidata menee validoinnista läpi
  (let [ilmoitustiedot (q "SELECT ilmoitustiedot FROM paallystysilmoitus")]
    (doseq [[ilmoitusosa] ilmoitustiedot]
      (is (skeema/validoi pot-domain/+paallystysilmoitus+
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
    (is (== (:kokonaishinta-ilman-maaramuutoksia paallystysilmoitus-kannassa) 7043.95))
    (is (= (:maaramuutokset-ennustettu? paallystysilmoitus-kannassa) true))
    (is (= (:kohdenimi paallystysilmoitus-kannassa) "Leppäjärven ramppi"))
    (is (= (:kohdenumero paallystysilmoitus-kannassa) "L03"))
    ;; Kohdeosat on OK
    (is (= (count kohdeosat) 2))
    (is (= (first kohdeosat)
           {;; Alikohteen tiedot
            :kohdeosa-id 666
            :hyppy? false
            :nimi "Leppäjärven kohdeosa"
            :tr-ajorata 1
            :tr-alkuetaisyys 0
            :tr-alkuosa 1
            :tr-kaista 1
            :tr-loppuetaisyys 0
            :tr-loppuosa 3
            :tr-numero 20
            :paallystetyyppi nil
            :raekoko nil
            :tyomenetelma nil
            :massamaara nil
            :toimenpide nil
            ;; Päällystetoimenpiteen tiedot
            :toimenpide-paallystetyyppi 2
            :toimenpide-raekoko 1
            :toimenpide-tyomenetelma 21
            :kokonaismassamaara 12
            :kuulamylly 2
            :leveys 12
            :massamenekki 1
            :pinta-ala 12
            :rc% 12
            ;; Kiviaines- ja sideainetiedot
            :esiintyma "12"
            :km-arvo "12"
            :lisaaineet "12"
            :muotoarvo "12"
            :pitoisuus 12
            :sideainetyyppi 2}))
    (is (= (second kohdeosat)
           {:kohdeosa-id 1024
            :hyppy? true
            :massamaara nil
            :nimi "Leppäjärven hyppy"
            :paallystetyyppi nil
            :raekoko nil
            :toimenpide nil
            :tr-ajorata 1
            :tr-alkuetaisyys 0
            :tr-alkuosa 3
            :tr-kaista 1
            :tr-loppuetaisyys 0
            :tr-loppuosa 3
            :tr-numero 20
            :tyomenetelma nil}))
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
        (is (== (:kokonaishinta-ilman-maaramuutoksia paallystysilmoitus-kannassa) 3968))
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
                :osoitteet [{;; Alikohteen tiedot
                             :nimi "Tie 20"
                             :kohdeosa-id 14
                             :hyppy? false
                             :tr-numero 20
                             :tr-alkuosa 26
                             :tr-alkuetaisyys 3
                             :tr-loppuosa 27
                             :tr-loppuetaisyys 5
                             :tr-ajorata 1
                             :tr-kaista 1
                             :paallystetyyppi 1
                             :raekoko 1
                             :tyomenetelma 12
                             :massamaara 2.00M
                             :toimenpide "Wut"
                             ;; Päällystetoimenpiteen tiedot
                             :toimenpide-paallystetyyppi 1
                             :toimenpide-raekoko 1
                             :kokonaismassamaara 2
                             :rc% 3
                             :toimenpide-tyomenetelma 12
                             :leveys 5
                             :massamenekki 7
                             :pinta-ala 8
                             ;; Kiviaines- ja sideainetiedot
                             :esiintyma "asd"
                             :km-arvo "asd"
                             :muotoarvo "asd"
                             :sideainetyyppi 1
                             :pitoisuus 54
                             :lisaaineet "asd"}]}))
        (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";"))))))

(deftest tallenna-uusi-hypyllinen-paallystysilmoitus-kantaan
  (let [paallystyskohde-id (hae-yllapitokohde-kuusamontien-testi-jolta-puuttuu-paallystysilmoitus)]
    (is (not (nil? paallystyskohde-id)))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi ilmoitus, jossa hyppyjä")
    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id
                                                      :valmis-kasiteltavaksi true)
                                 ;; Muuta eka osoite hypyksi
                                 (assoc-in [:ilmoitustiedot :osoitteet]
                                           [(assoc (first (get-in pot-testidata [:ilmoitustiedot :osoitteet]))
                                              :hyppy? true)
                                            (second (get-in pot-testidata [:ilmoitustiedot :osoitteet]))]))
          _ (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                         :sopimus-id sopimus-id
                                                                         :paallystysilmoitus paallystysilmoitus})
          paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :urakan-paallystysilmoitus-paallystyskohteella
                                                      +kayttaja-jvh+ {:urakka-id urakka-id
                                                                      :sopimus-id sopimus-id
                                                                      :paallystyskohde-id paallystyskohde-id})]
      (log/debug "Testitallennus valmis. POTTI kannassa: " (pr-str paallystysilmoitus-kannassa))
      (is (not (nil? paallystysilmoitus-kannassa)))
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
              :osoitteet [{;; Alikohteen tiedot
                           :nimi "Tie 20"
                           :kohdeosa-id 14
                           :hyppy? true
                           :tr-numero 20
                           :tr-alkuosa 26
                           :tr-alkuetaisyys 3
                           :tr-loppuosa 27
                           :tr-loppuetaisyys 5
                           :tr-ajorata 1
                           :tr-kaista 1
                           :paallystetyyppi 1
                           :raekoko 1
                           :tyomenetelma 12
                           :massamaara 2.00M
                           :toimenpide "Wut"
                           ;; Päällystetoimenpiteen tiedot
                           :toimenpide-paallystetyyppi 1
                           :toimenpide-raekoko 1
                           :kokonaismassamaara 2
                           :rc% 3
                           :toimenpide-tyomenetelma 12
                           :leveys 5
                           :massamenekki 7
                           :pinta-ala 8
                           ;; Kiviaines- ja sideainetiedot
                           :esiintyma "asd"
                           :km-arvo "asd"
                           :muotoarvo "asd"
                           :sideainetyyppi 1
                           :pitoisuus 54
                           :lisaaineet "asd"}]}))
      (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";")))))

(deftest uuden-paallystysilmoituksen-tallennus-eri-urakkaan-ei-onnistu
  (let [paallystyskohde-id (hae-yllapitokohde-kuusamontien-testi-jolta-puuttuu-paallystysilmoitus)]
    (is (not (nil? paallystyskohde-id)))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi ilmoitus")
    (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
          sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)]
      (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                          :sopimus-id sopimus-id
                                                                                          :paallystysilmoitus paallystysilmoitus}))))))

(deftest paivita-paallystysilmoitukselle-paatostiedot
  (let [paallystyskohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata
                                   :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:tekninen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:tekninen-osa :perustelu] "Hyvä ilmoitus!"))
          paallystysilmoitus (update-in paallystysilmoitus [:ilmoitustiedot :osoitteet]
                                        (fn [osoitteet]
                                          [(assoc (first osoitteet)
                                             :tr-numero 20
                                             :tr-alkuosa 1
                                             :tr-alkuetaisyys 1
                                             :tr-loppuosa 1
                                             :tr-loppuetaisyys 100)
                                           (assoc (second osoitteet)
                                             :tr-numero 20
                                             :tr-alkuosa 1
                                             :tr-alkuetaisyys 100
                                             :tr-loppuosa 3
                                             :tr-loppuetaisyys 0)]))]
      
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

        ;; Tie 20 tiedot tallentuivat kantaan, mutta tie 555 ei koska oli poistettu
        (is (some #(= (:nimi %) "Tie 20")
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
                :osoitteet [{;; Alikohteen tiedot
                             :nimi "Tie 20"
                             :kohdeosa-id 14
                             :hyppy? false
                             :tr-numero 20
                             :tr-alkuosa 1
                             :tr-alkuetaisyys 1
                             :tr-loppuosa 1
                             :tr-loppuetaisyys 100
                             :tr-ajorata 1
                             :tr-kaista 1
                             :paallystetyyppi 1
                             :raekoko 1
                             :tyomenetelma 12
                             :massamaara 2.00M
                             :toimenpide "Wut"
                             ;; Päällystetoimenpiteen tiedot
                             :toimenpide-paallystetyyppi 1
                             :toimenpide-raekoko 1
                             :kokonaismassamaara 2
                             :rc% 3
                             :toimenpide-tyomenetelma 12
                             :leveys 5
                             :massamenekki 7
                             :pinta-ala 8
                             ;; Kiviaines- ja sideainetiedot
                             :esiintyma "asd"
                             :km-arvo "asd"
                             :muotoarvo "asd"
                             :sideainetyyppi 1
                             :pitoisuus 54
                             :lisaaineet "asd"}]}))

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
                                          conj {;; Alikohteen tiedot
                                                :nimi "Tie 123"
                                                :tr-numero 20
                                                :tr-alkuosa 26
                                                :tr-alkuetaisyys 3
                                                :tr-loppuosa 27
                                                :tr-loppuetaisyys 5
                                                :tr-ajorata 1
                                                :tr-kaista 1
                                                :paallystetyyppi 1
                                                :raekoko 1
                                                :tyomenetelma 12
                                                :massamaara 2
                                                :toimenpide "Wut"
                                                ;; Päällystetoimenpiteen tiedot
                                                :toimenpide-paallystetyyppi 1
                                                :toimenpide-raekoko 1
                                                :kokonaismassamaara 2
                                                :rc% 3
                                                :toimenpide-tyomenetelma 12
                                                :leveys 5
                                                :massamenekki 7
                                                :pinta-ala 8
                                                ;; Kiviaines- ja sideainetiedot
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


(deftest yllapitokohteiden-maksuerien-haku-toimii
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-paallystyksen-maksuerat
                                +kayttaja-jvh+
                                {::urakka-domain/id urakka-id
                                 ::sopimus-domain/id sopimus-id
                                 ::urakka-domain/vuosi 2017})
        leppajarven-ramppi (yllapitokohteet-test/kohde-nimella vastaus "Leppäjärven ramppi")]
    (is (= (count vastaus) 6) "Kaikki kohteet palautuu")
    (is (== (:kokonaishinta leppajarven-ramppi) 7248.95))
    (is (= (count (:maksuerat leppajarven-ramppi)) 2))))

(deftest yllapitokohteen-uusien-maksuerien-tallennus-toimii
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        leppajarven-ramppi (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        testidata (gen/sample (s/gen ::paallystyksen-maksuerat-domain/tallennettava-yllapitokohde-maksuerineen))]

    (doseq [yllapitokohde testidata]
      (u "DELETE FROM yllapitokohteen_maksuera WHERE yllapitokohde = " leppajarven-ramppi ";")
      (u "DELETE FROM yllapitokohteen_maksueratunnus WHERE yllapitokohde = " leppajarven-ramppi ";")
      (let [maksuerat (:maksuerat yllapitokohde)
            maksueranumerot (map :maksueranumero maksuerat)
            ;; Generointi generoi useita samoja maksueränumeroita, mutta palvelu tallentaa vain viimeisimmän.
            ;; Otetaan viimeiset talteen ja tarkistetaan, että tallennus menee oikein
            tallennettavat-maksuerat (if (empty? maksueranumerot)
                                       []
                                       (keep (fn [maksueranumero]
                                               (-> (filter #(= (:maksueranumero %) maksueranumero) maksuerat)
                                                   last
                                                   (dissoc :id)))
                                             (range (apply min maksueranumerot)
                                                    (inc (apply max maksueranumerot)))))
            payload {::urakka-domain/id urakka-id
                     ::sopimus-domain/id sopimus-id
                     ::urakka-domain/vuosi 2017
                     :yllapitokohteet [(assoc yllapitokohde :id leppajarven-ramppi)]}
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-paallystyksen-maksuerat
                                    +kayttaja-jvh+
                                    payload)
            leppajarven-ramppi (yllapitokohteet-test/kohde-nimella vastaus "Leppäjärven ramppi")]

        (is (= (count vastaus) 6) "Kaikki kohteet palautuu")
        (is (= (map #(dissoc % :id) (:maksuerat leppajarven-ramppi))
               tallennettavat-maksuerat))))))

(deftest yllapitokohteen-maksuerien-paivitys-toimii
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        leppajarven-ramppi (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)]

    (let [tallennettavat-maksuerat [{:maksueranumero 1 :sisalto "Päivitetäänpäs tämä"}
                                    {:maksueranumero 4 :sisalto nil}
                                    {:maksueranumero 5 :sisalto "Uusi maksuerä"}]
          payload {::urakka-domain/id urakka-id
                   ::sopimus-domain/id sopimus-id
                   ::urakka-domain/vuosi 2017
                   :yllapitokohteet [{:id leppajarven-ramppi
                                      :maksuerat tallennettavat-maksuerat
                                      :maksueratunnus "Uusi maksuerätunnus"}]}
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-paallystyksen-maksuerat
                                  +kayttaja-jvh+
                                  payload)
          leppajarven-ramppi (yllapitokohteet-test/kohde-nimella vastaus "Leppäjärven ramppi")]

      (is (= (count vastaus) 6) "Kaikki kohteet palautuu")
      (is (= (:maksueratunnus leppajarven-ramppi) "Uusi maksuerätunnus"))
      (is (= (:maksuerat leppajarven-ramppi)
             [{:id 1
               :maksueranumero 1
               :sisalto "Päivitetäänpäs tämä"}
              {:id 2
               :maksueranumero 2
               :sisalto "Puolet"}
              {:id 6
               :maksueranumero 4
               :sisalto nil}
              {:id 7
               :maksueranumero 5
               :sisalto "Uusi maksuerä"}])
          "Uudet maksuerät ilmestyivät kantaan, vanhat päivitettiin ja payloadissa olemattomiin ei koskettu"))))

(deftest avaa-lukitun-potin-lukitus
  (let [kohde-id (hae-yllapitokohde-oulun-ohitusramppi)
        payload {::urakka-domain/id (hae-muhoksen-paallystysurakan-id)
                 ::pot-domain/paallystyskohde-id kohde-id
                 ::pot-domain/tila :valmis}
        tila-ennen-kutsua (keyword (second (first (q (str "SELECT id, tila FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id)))))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :aseta-paallystysilmoituksen-tila +kayttaja-jvh+ payload)]
    (is (= :lukittu tila-ennen-kutsua))
    (is (= :valmis (:tila vastaus)))))

(deftest avaa-lukitun-potin-lukitus-ei-sallittu-koska-tero-ei-tassa-urakanvalvojana
  (let [kohde-id (hae-yllapitokohde-oulun-ohitusramppi)
        payload {::urakka-domain/id (hae-muhoksen-paallystysurakan-id)
                 ::pot-domain/paallystyskohde-id kohde-id
                 ::pot-domain/tila :valmis}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :aseta-paallystysilmoituksen-tila
                                           +kayttaja-tero+
                                           payload)))
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :aseta-paallystysilmoituksen-tila
                                           +kayttaja-vastuuhlo-muhos+
                                           payload)))))

(deftest sahkopostien-lahetys
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        paallystyskohde-id (:paallystyskohde (first (q-map (str "SELECT paallystyskohde "
                                                                "FROM paallystysilmoitus pi "
                                                                "JOIN yllapitokohde yk ON yk.id=pi.paallystyskohde "
                                                                "WHERE (pi.paatos_tekninen_osa IS NULL OR "
                                                                "pi.paatos_tekninen_osa='hylatty'::paallystysilmoituksen_paatostyyppi) AND "
                                                                "pi.tila!='valmis'::paallystystila AND "
                                                                "yk.urakka=" urakka-id " "
                                                                "LIMIT 1"))))
        ;; Tehdään ensin sellainen päällystysilmoitus, joka on valmis tarkastettavaksi
        ;; ja lähetetään paallystysilmoituksen valmistumisesta sähköposti ely valvojalle
        paallystysilmoitus (assoc pot-testidata
                             :paallystyskohde-id paallystyskohde-id
                             :valmis-kasiteltavaksi true)
        sahkoposti-valitetty (atom false)
        sahkopostin-vastaanottaja (atom nil)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))]
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [lahteva-viesti]
                                                            (reset! sahkopostin-vastaanottaja (->> lahteva-viesti
                                                                                                   .getText
                                                                                                   xml/lue
                                                                                                   first
                                                                                                   :content
                                                                                                   (some #(when (= :vastaanottajat (:tag %))
                                                                                                            (:content %)))
                                                                                                   first
                                                                                                   :content
                                                                                                   first))
                                                            (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus
                      +kayttaja-jvh+ {:urakka-id urakka-id
                                      :sopimus-id sopimus-id
                                      :paallystysilmoitus paallystysilmoitus}))

    (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 10000)
    (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")
    (is @sahkopostin-vastaanottaja "ELY_Urakanvalvoja@example.com")
    (reset! sahkoposti-valitetty false)
    (reset! sahkopostin-vastaanottaja nil)
    (let [;;Hyväksytään ilmoitus ja lähetetään tästä urakan valvojalle sähköposti
          paallystysilmoitus (-> (assoc pot-testidata
                                   :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:tekninen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:tekninen-osa :perustelu] "Hyvä ilmoitus!"))]
      (with-fake-http
        [+testi-fim+ fim-vastaus]
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-paallystysilmoitus
                        +kayttaja-jvh+ {:urakka-id urakka-id
                                        :sopimus-id sopimus-id
                                        :paallystysilmoitus paallystysilmoitus}))
      (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 10000)
      (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")
      (is @sahkopostin-vastaanottaja "vastuuhenkilo@example.com"))))
