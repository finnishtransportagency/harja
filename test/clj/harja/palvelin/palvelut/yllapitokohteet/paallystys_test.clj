(ns harja.palvelin.palvelut.yllapitokohteet.paallystys-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [namespacefy.core :refer [namespacefy]]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.asetukset :as a]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [cheshire.core :as cheshire]
            [harja.integraatio :as integraatio]
            [harja.domain.urakka :as urakka-domain]
            [harja.domain.sopimus :as sopimus-domain]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.paallystyksen-maksuerat :as paallystyksen-maksuerat-domain]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus-domain]
            [harja.domain.pot2 :as pot2-domain]
            [harja.pvm :as pvm]
            [harja.domain.skeema :as skeema]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [harja.jms-test :refer [feikki-jms]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.kyselyt.integraatiot :as integraatio-kyselyt]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet.pot2 :as pot2]
            [harja.palvelin.palvelut.yllapitokohteet-test :as yllapitokohteet-test]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as integraatiopiste-http]
            [harja.tyokalut.xml :as xml]
            [harja.domain.paallystysilmoitus :as pot-domain]
            [harja.domain.yllapitokohde :as yllapitokohteet-domain])
  (:import (java.util UUID))
  (:use org.httpkit.fake))

(def ehdon-timeout 20000)


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :fim (component/using
                               (fim/->FIM {:url +testi-fim+})
                               [:db :integraatioloki])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :itmf (feikki-jms "itmf")
                        :api-sahkoposti (component/using
                                          (sahkoposti-api/->ApiSahkoposti {:api-sahkoposti integraatio/api-sahkoposti-asetukset
                                                                           :tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                                          [:http-palvelin :db :integraatioloki :itmf])
                        :paallystys (component/using
                                      (paallystys/->Paallystys)
                                      [:http-palvelin :db :fim :api-sahkoposti])
                        :pot2 (component/using
                                (pot2/->POT2)
                                [:http-palvelin :db :fim :api-sahkoposti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each
              urakkatieto-fixture
              jarjestelma-fixture)

(def pot-testidata
  {:versio 1
   :perustiedot {:aloituspvm (pvm/luo-pvm 2019 9 1)
                 :valmispvm-kohde (pvm/luo-pvm 2019 9 2)
                 :valmispvm-paallystys (pvm/luo-pvm 2019 9 2)
                 :takuupvm (pvm/luo-pvm 2019 9 3)
                 :tr-osoite {:tr-numero 22
                             :tr-alkuosa 3
                             :tr-alkuetaisyys 1
                             :tr-loppuosa 4
                             :tr-loppuetaisyys 5}
                 :tr-numero 22
                 :tr-alkuosa 3
                 :tr-alkuetaisyys 1
                 :tr-loppuosa 4
                 :tr-loppuetaisyys 5}
   :ilmoitustiedot {:osoitteet [{;; Alikohteen tiedot
                                 :nimi "Tie 22"
                                 :tr-numero 22
                                 :tr-alkuosa 3
                                 :tr-alkuetaisyys 3
                                 :tr-loppuosa 3
                                 :tr-loppuetaisyys 5
                                 :tr-ajorata 1
                                 :tr-kaista 11
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
                                 :poistettu true            ;; HUOMAA POISTETTU, EI SAA TALLENTUA!
                                 :nimi "Tie 20"
                                 :tr-numero 20
                                 :tr-alkuosa 3
                                 :tr-alkuetaisyys 3
                                 :tr-loppuosa 3
                                 :tr-loppuetaisyys 5
                                 :tr-ajorata 1
                                 :tr-kaista 11
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

                    :alustatoimet [{:tr-numero 22
                                    :tr-kaista 11
                                    :tr-ajorata 1
                                    :tr-alkuosa 3
                                    :tr-alkuetaisyys 3
                                    :tr-loppuosa 3
                                    :tr-loppuetaisyys 5
                                    :kasittelymenetelma 1
                                    :paksuus 1234
                                    :verkkotyyppi 1
                                    :verkon-sijainti 1
                                    :verkon-tarkoitus 1
                                    :tekninen-toimenpide 1}]}})

(def pot2-testidata
  {:paallystyskohde-id 28,
   :versio 2,
   :lisatiedot "POT2 lisätieto"
   :perustiedot {:tila nil,
                 :tr-kaista nil,
                 :kohdenimi "Aloittamaton kohde mt20",
                 :kohdenumero "L43",
                 :tr-ajorata nil,
                 :kommentit [],
                 :tr-loppuosa 3,
                 :valmispvm-kohde #inst "2021-06-20T21:00:00.000-00:00",
                 :tunnus nil,
                 :tr-alkuosa 3,
                 :tr-loppuetaisyys 5000,
                 :aloituspvm #inst "2021-06-18T21:00:00.000-00:00",
                 :takuupvm nil,
                 :tr-osoite {:tr-kaista nil,
                             :tr-ajorata nil,
                             :tr-loppuosa 3,
                             :tr-alkuosa 3,
                             :tr-loppuetaisyys 5000,
                             :tr-alkuetaisyys 1,
                             :tr-numero 20},
                 :asiatarkastus {:lisatiedot nil,
                                 :hyvaksytty nil,
                                 :tarkastusaika nil,
                                 :tarkastaja nil},
                 :tr-alkuetaisyys 1,
                 :tr-numero 20,
                 :tekninen-osa {:paatos nil,
                                :kasittelyaika nil,
                                :perustelu nil},
                 :valmispvm-paallystys nil},
   :ilmoitustiedot nil,
   :paallystekerros [{:kohdeosa-id 13,
                    :tr-kaista 11,
                    :tr-ajorata 1,
                    :jarjestysnro 1,
                    :tr-loppuosa 3,
                    :tr-alkuosa 3,
                    :tr-loppuetaisyys 5000,
                    :nimi "Kohdeosa kaista 11",
                    :tr-alkuetaisyys 3,
                    :tr-numero 20,
                    :toimenpide 12,
                    :leveys 3,
                    :kokonaismassamaara 2,
                    :pinta_ala 1,
                    :massamenekki 2,
                    :materiaali 1}
                   {:kohdeosa-id 14,
                    :tr-kaista 12,
                    :tr-ajorata 1,
                    :jarjestysnro 1,
                    :tr-loppuosa 3,
                    :tr-alkuosa 3,
                    :tr-loppuetaisyys 5000,
                    :nimi "Kohdeosa kaista 12",
                    :tr-alkuetaisyys 3,
                    :tr-numero 20,
                    :toimenpide 21,
                    :leveys 3,
                    :kokonaismassamaara 2,
                    :pinta_ala 1,
                    :massamenekki 2,
                    :materiaali 1}]})

(defn- tallenna-pot2-testi-paallystysilmoitus
  [urakka-id sopimus-id paallystyskohde-id paallystysilmoitus]
  (let [paallystysilmoitus-kannassa-ennen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                          :urakan-paallystysilmoitus-paallystyskohteella
                                                          +kayttaja-jvh+ {:urakka-id urakka-id
                                                                          :sopimus-id sopimus-id
                                                                          :paallystyskohde-id paallystyskohde-id})
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-utajarven-paallystysurakan-kayttajat.xml"))
        viesti-id (str (UUID/randomUUID))

        _ (with-fake-http
            [+testi-fim+ fim-vastaus
             {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
            (kutsu-palvelua (:http-palvelin jarjestelma)
             :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                          :sopimus-id sopimus-id
                                                          :vuosi pot-domain/pot2-vuodesta-eteenpain
                                                          :paallystysilmoitus paallystysilmoitus}))
        paallystysilmoitus-kannassa-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                            :urakan-paallystysilmoitus-paallystyskohteella
                                                            +kayttaja-jvh+ {:urakka-id urakka-id
                                                                            :sopimus-id sopimus-id
                                                                            :paallystyskohde-id paallystyskohde-id})]
    [paallystysilmoitus-kannassa-ennen paallystysilmoitus-kannassa-jalkeen]))

(deftest skeemavalidointi-toimii
  (let [paallystyskohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
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
                                                   :vuosi 2019
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
  (let [paallystyskohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :tila] :aloitettu)
                                 (assoc-in [:perustiedot :valmis-kasiteltavaksi] true)
                                 (assoc :ilmoitustiedot
                                        {:osoitteet [{;; Alikohteen tiedot
                                                      :nimi "Tie 666"
                                                      :tr-numero 666
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

                                         :alustatoimet [{:tr-numero 666
                                                         :tr-kaista 1
                                                         :tr-ajorata 1
                                                         :tr-alkuosa 2
                                                         :tr-alkuetaisyys 3
                                                         :tr-loppuosa 4
                                                         :tr-loppuetaisyys 5
                                                         :kasittelymenetelma 1
                                                         :paksuus 1234
                                                         :tekninen-toimenpide 1
                                                         ;; Verkkoon liittyvät avaimet puuttuu kokonaan, arvoja ei siis annettu
                                                         }]}))]

      (is (thrown? IllegalArgumentException
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-paallystysilmoitus
                                   +kayttaja-jvh+ {:urakka-id urakka-id
                                                   :sopimus-id sopimus-id
                                                   :vuosi 2019
                                                   :paallystysilmoitus paallystysilmoitus}))))))

(deftest testidata-on-validia
  ;; On kiva jos testaamme näkymää ja meidän testidata menee validoinnista läpi
  (let [ilmoitustiedot (q "SELECT pi.ilmoitustiedot
                           FROM paallystysilmoitus pi
                           JOIN yllapitokohde yk ON yk.id = pi.paallystyskohde
                           WHERE yk.vuodet[1] >= 2019
                             AND pi.versio = 1")]
    (doseq [[ilmoitusosa] ilmoitustiedot]
      (is (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus-ilmoitustiedot+
                          (konv/jsonb->clojuremap ilmoitusosa))))))

(deftest hae-paallystysilmoitukset-1
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        paallystysilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-paallystysilmoitukset +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id})]
    (is (= (count paallystysilmoitukset) 12) "Päällystysilmoituksia löytyi")))

(deftest hae-paallystysilmoitukset-2
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        paallystysilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-paallystysilmoitukset +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :vuosi 2015})]
    (is (= (count paallystysilmoitukset) 0) "Päällystysilmoituksia ei löydy vuodelle 2015")))

(deftest hae-paallystysilmoitukset-3
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        paallystysilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-paallystysilmoitukset +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :vuosi 2017})]
    (is (= (count paallystysilmoitukset) 5) "Päällystysilmoituksia löytyi vuodelle 2017")))

(deftest hae-paallystysilmoitukset-utajarvi-2021-pot2
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystysilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-paallystysilmoitukset +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :vuosi paallystysilmoitus-domain/pot2-vuodesta-eteenpain})
        tarkea-kohde (first (filter #(= (:nimi %) "Tärkeä kohde mt20") paallystysilmoitukset))]
    (is (= 3 (count paallystysilmoitukset)) "Päällystysilmoituksia löytyi vuodelle 2021")
    (is (= 2 (:versio tarkea-kohde)))
    (is (= :aloitettu (:tila tarkea-kohde)) "Tila")
    (is (= false (:lahetys-onnistunut tarkea-kohde)) "Lähetys")
    (is (= "L42" (:kohdenumero tarkea-kohde)) "Kohdenumero")
    (is (nil? (:paatos-tekninen-osa tarkea-kohde)) "Päätös")
    (is (nil? (:ilmoitustiedot tarkea-kohde)))
    (is (= {:numero 20, :alkuosa 1, :alkuetaisyys 1066, :loppuosa 1, :loppuetaisyys 3827}
           (:yha-tr-osoite tarkea-kohde)))
    (is (= 2 (count (:kohdeosat tarkea-kohde))) "Kohdeosien lkm")))

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
    (is (= (:versio paallystysilmoitus-kannassa) 1))
    (is (= (:tila paallystysilmoitus-kannassa) :aloitettu) "Päällystysilmoituksen tila on aloitttu")
    (is (== (:maaramuutokset paallystysilmoitus-kannassa) 205))
    (is (== (:kokonaishinta-ilman-maaramuutoksia paallystysilmoitus-kannassa) 7043.95))
    (is (= (:maaramuutokset-ennustettu? paallystysilmoitus-kannassa) true))
    (is (= (:kohdenimi paallystysilmoitus-kannassa) "Leppäjärven ramppi"))
    (is (= (:kohdenumero paallystysilmoitus-kannassa) "L03"))
    ;; Kohdeosat on OK
    (is (= (count kohdeosat) 2))
    (is (= (first (filter #(= (:nimi %) "Leppäjärven kohdeosa") kohdeosat))
           {;; Alikohteen tiedot
            :kohdeosa-id 666
            :nimi "Leppäjärven kohdeosa"
            :tr-ajorata 1
            :tr-alkuetaisyys 0
            :tr-alkuosa 1
            :tr-kaista 11
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
    (is (every? #(number? (:kohdeosa-id %)) kohdeosat))))

(deftest hae-yllapitokohteen-olemassa-oleva-pot2-paallystysilmoitus
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :urakan-paallystysilmoitus-paallystyskohteella
                                                    +kayttaja-jvh+ {:urakka-id urakka-id
                                                                    :sopimus-id sopimus-id
                                                                    :paallystyskohde-id paallystyskohde-id})
        kohdeosat (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet])]
    (is (not (nil? paallystysilmoitus-kannassa)))
    (is (= (:versio paallystysilmoitus-kannassa) 2))
    (is (= {:numero 20, :alkuosa 1, :alkuetaisyys 1066, :loppuosa 1, :loppuetaisyys 3827}
           (:yha-tr-osoite paallystysilmoitus-kannassa)))
    (is (= 2 (count kohdeosat)))))

(defn- hae-yllapitokohdeosadata [yllapitokohde-id]
  (set (q-map (str "SELECT nimi, paallystetyyppi, raekoko, tyomenetelma, massamaara, toimenpide
                        FROM yllapitokohdeosa WHERE NOT poistettu AND yllapitokohde = " yllapitokohde-id))))

(defn- tallenna-testipaallystysilmoitus
  [paallystysilmoitus, vuosi]
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-utajarven-paallystysurakan-kayttajat.xml"))
        viesti-id (str (UUID/randomUUID))
        vastaus (with-fake-http
                  [+testi-fim+ fim-vastaus
                   {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                 :sopimus-id sopimus-id
                                                                 :vuosi vuosi
                                                                 :paallystysilmoitus paallystysilmoitus}))
        yllapitokohdeosadata (hae-yllapitokohdeosadata (:paallystyskohde-id paallystysilmoitus))]
    [urakka-id sopimus-id vastaus yllapitokohdeosadata]))

(defn- tallenna-vaara-paallystysilmoitus
  [paallystyskohde-id paallystysilmoiuts vuosi odotettu]
  ;(log/debug "Yritän tallentaa väärä päällystysilmotus " paallystyskohde-id)
  (is (some? paallystyskohde-id))
  (let [paallystysilmoitus (-> paallystysilmoiuts
                               (assoc :paallystyskohde-id paallystyskohde-id)
                               (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))
        maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))]

    ;; Toistaiseki tuetaan että jos odotettu arvo onkin map, vain että oikeanlainen expection heitetään
    (if (map? odotettu)
      (is (thrown? IllegalArgumentException
                   (tallenna-testipaallystysilmoitus paallystysilmoitus vuosi)))
      (is (thrown-with-msg? IllegalArgumentException (re-pattern odotettu)
                            (tallenna-testipaallystysilmoitus paallystysilmoitus vuosi))))

    (let [maara-lisayksen-jalkeen (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))]
      (is (= maara-ennen-lisaysta maara-lisayksen-jalkeen) "Ei saa olla mitään uutta kannassa"))
    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

(deftest tallenna-uusi-paallystysilmoitus-kantaan
  (let [;; Ei saa olla POT ilmoitusta
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Kirkkotie")]
    (is (some? paallystyskohde-id))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi ilmoitus")
    (let [paallystysilmoitus (-> pot-testidata
                                 (assoc :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))
          maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))
          [urakka-id sopimus-id vastaus yllapitokohdeosadata] (tallenna-testipaallystysilmoitus paallystysilmoitus 2020)]

      ;; Vastauksena saadaan annetun vuoden ylläpitokohteet ja päällystysilmoitukset. Poistetun kohteen ei pitäisi tulla.

      (is (= (count (:yllapitokohteet vastaus)) 2))
      (is (= (count (:paallystysilmoitukset vastaus)) 2))

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
        (is (= (:kokonaishinta-ilman-maaramuutoksia paallystysilmoitus-kannassa) 4753.95M))
        (is (= (update-in (:ilmoitustiedot paallystysilmoitus-kannassa) [:osoitteet 0] (fn [osoite]
                                                                                         (dissoc osoite :kohdeosa-id)))
               {:alustatoimet [{:kasittelymenetelma 1
                                :paksuus 1234
                                :tekninen-toimenpide 1
                                :tr-numero 22
                                :tr-kaista 11
                                :tr-ajorata 1
                                :tr-alkuetaisyys 3
                                :tr-alkuosa 3
                                :tr-loppuetaisyys 5
                                :tr-loppuosa 3
                                :verkkotyyppi 1
                                :verkon-sijainti 1
                                :verkon-tarkoitus 1}]
                :osoitteet [{;; Alikohteen tiedot
                             :nimi "Tie 22"
                             :tr-numero 22
                             :tr-alkuosa 3
                             :tr-alkuetaisyys 3
                             :tr-loppuosa 3
                             :tr-loppuetaisyys 5
                             :tr-ajorata 1
                             :tr-kaista 11
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
        (is (= yllapitokohdeosadata
               #{{:nimi            "Tie 22"
                  :paallystetyyppi 1
                  :raekoko         1
                  :tyomenetelma    12
                  :massamaara      2.00M
                  :toimenpide      "Wut"}} ))
        (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))))

(deftest ei-saa-tallenna-paallystysilmoitus-jos-feilaa-validointi-alkuosa
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Kirkkotie")
        paallystysilmoitus (-> pot-testidata
                               (assoc-in [:ilmoitustiedot :osoitteet 0 :tr-alkuosa] 2))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2020
                                       "Kaista 11 ajoradalla 1 ei kata koko osaa 3")))

(deftest ei-saa-tallenna-paallystysilmoitus-jos-feilaa-validointi-loppuosa
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Kirkkotie")
        paallystysilmoitus (-> pot-testidata
                               (assoc-in [:ilmoitustiedot :osoitteet 0 :tr-loppuetaisyys] 6000))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2020
                                       "(aet: 3, let: 6000)")))

(deftest ei-saa-tallenna-paallystysilmoitus-jos-paallekkain
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Kirkkotie")
        paallystysilmoitus (-> pot-testidata
                               (assoc-in [:ilmoitustiedot :osoitteet 2] {;; Alikohteen tiedot
                                                                         :nimi "Tie 22 tosi pieni pätkä"
                                                                         :tr-numero 22
                                                                         :tr-alkuosa 3
                                                                         :tr-alkuetaisyys 4
                                                                         :tr-loppuosa 3
                                                                         :tr-loppuetaisyys 5
                                                                         :tr-ajorata 1
                                                                         :tr-kaista 11
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
                                                                         :lisaaineet "asd"}))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2020
                                       "Kohteenosa on päällekkäin toisen osan")))

(deftest ei-saa-tallenna-pot2-paallystysilmoitus-jos-feilaa-validointi-alkuosa
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        paallystysilmoitus (-> pot2-testidata
                               (assoc-in [:paallystekerros 0 :tr-alkuosa] 2))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2021
                                       "Alikohde ei voi olla pääkohteen ulkopuolella")))

(deftest ei-saa-tallenna-pot2-paallystysilmoitus-jos-feilaa-validointi-loppuosa
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        paallystysilmoitus (-> pot2-testidata
                               (assoc-in [:paallystekerros 0 :tr-loppuetaisyys] 6000))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2021
                                       "Alikohde ei voi olla pääkohteen ulkopuolella")))

(deftest ei-saa-tallenna-pot2-paallystysilmoitus-jos-paallekkain
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        paallystysilmoitus (-> pot2-testidata
                               (assoc-in [:paallystekerros 2] {:kohdeosa-id 14,
                                                             :tr-kaista 12,
                                                             :tr-ajorata 1,
                                                             :jarjestysnro 1,
                                                             :tr-loppuosa 3,
                                                             :tr-alkuosa 3,
                                                             :tr-loppuetaisyys 5000000,
                                                             :nimi "Kohdeosa kaista 12",
                                                             :tr-alkuetaisyys 3,
                                                             :tr-numero 20,
                                                             :toimenpide 21,
                                                             :leveys 3,
                                                             :kokonaismassamaara 2,
                                                             :pinta_ala 1,
                                                             :massamenekki 2,
                                                             :materiaali 1}))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2021
                                       "Alikohde ei voi olla pääkohteen ulkopuolella")))

(deftest tallenna-pot2-paallystysilmoitus-jos-paallekkain-mutta-eri-jarjestysnro
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        paallystysilmoitus (-> pot2-testidata
                               (assoc-in [:paallystekerros 2] {:kohdeosa-id 14,
                                                             :tr-kaista 12,
                                                             :tr-ajorata 1,
                                                             :jarjestysnro 2,
                                                             :tr-loppuosa 3,
                                                             :tr-alkuosa 3,
                                                             :tr-loppuetaisyys 500,
                                                             :nimi "Kohdeosa kaista 12",
                                                             :tr-alkuetaisyys 3,
                                                             :tr-numero 20,
                                                             :toimenpide 21,
                                                             :leveys 3,
                                                             :kokonaismassamaara 2,
                                                             :pinta_ala 1,
                                                             :massamenekki 2,
                                                             :materiaali 1}))
        maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))
        [urakka-id sopimus-id vastaus yllapitokohdeosadata] (tallenna-testipaallystysilmoitus
                                                              paallystysilmoitus
                                                              paallystysilmoitus-domain/pot2-vuodesta-eteenpain)]
    (let [maara-lisayksen-jalkeen (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))]
      (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen) "Tallennuksen jälkeen päällystysilmoituksien määrä")
      (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id))))

(deftest tallenna-pot2-paallystysilmoitus-jossa-alikohde-muulla-tiella
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        muu-tr-numero 5555
        paallystysilmoitus (-> pot2-testidata
                               (assoc-in [:paallystekerros 1 :tr-numero] muu-tr-numero))
        maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))
        [urakka-id sopimus-id _ _] (tallenna-testipaallystysilmoitus
                                                              paallystysilmoitus
                                                              paallystysilmoitus-domain/pot2-vuodesta-eteenpain)]
    (let [maara-lisayksen-jalkeen (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))]
      (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen) "Tallennuksen jälkeen päällystysilmoituksien määrä"))
    (let [paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                      :urakan-paallystysilmoitus-paallystyskohteella
                                                      +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                      :sopimus-id         sopimus-id
                                                                      :paallystyskohde-id paallystyskohde-id})
          kulutuskerros (:paallystekerros paallystysilmoitus-kannassa)]
      (is (= 2 (count kulutuskerros)))
      (is (= #{20 muu-tr-numero} (set (map #(:tr-numero %) kulutuskerros)))))
    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

(deftest paivittaa-paallystysilmoitus-muokkaa-yllapitokohdeosaa
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Kirkkotie")
        _ (is (some? paallystyskohde-id))
        alkuperainen-paallystysilmoitus (-> pot-testidata
                                            (assoc :paallystyskohde-id paallystyskohde-id)
                                            (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))]
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " alkuperäinen ilmoitus")
    (let [[_ _ _ yllapitokohdeosadata] (tallenna-testipaallystysilmoitus alkuperainen-paallystysilmoitus 2020)
          kohdeosa-id (:id yllapitokohdeosadata)]
      (is (= yllapitokohdeosadata
             #{{:nimi            "Tie 22"
                :paallystetyyppi 1
                :raekoko         1
                :tyomenetelma    12
                :massamaara      2.00M
                :toimenpide      "Wut"}}))
      (let [uusi-paallystysilmoitus (-> alkuperainen-paallystysilmoitus
                                        (assoc-in [:ilmoitustiedot :osoitteet 0 :nimi] "Uusi Tie 22")
                                        (assoc-in [:ilmoitustiedot :osoitteet 0 :toimenpide] "Freude")
                                        (assoc-in [:ilmoitustiedot :osoitteet 0 :kohdeosa-id] kohdeosa-id))
            [_ _ vastaus paivitetty-yllapitokohdeosadata] (tallenna-testipaallystysilmoitus uusi-paallystysilmoitus 2020)]
        (is (nil? (:virhe vastaus)))
        (is (= paivitetty-yllapitokohdeosadata
               #{{:nimi            "Uusi Tie 22"
                  :paallystetyyppi 1
                  :raekoko         1
                  :tyomenetelma    12
                  :massamaara      2.00M
                  :toimenpide      "Freude"}}))))
    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

(deftest tallenna-uusi-pot2-paallystysilmoitus-kantaan
  (let [;; Ei saa olla POT ilmoitusta
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")]
    (is (some? paallystyskohde-id))
    (is (= 28 paallystyskohde-id))
    (u (str "UPDATE yllapitokohdeosa SET toimenpide = 'Wut' WHERE yllapitokohde = 28"))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi pot2 ilmoitus")
    (let [paallystysilmoitus (-> pot2-testidata
                                 (assoc :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))
          maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))
          [urakka-id sopimus-id vastaus yllapitokohdeosadata] (tallenna-testipaallystysilmoitus
                                                                paallystysilmoitus
                                                                paallystysilmoitus-domain/pot2-vuodesta-eteenpain)]
      ;; Vastauksena saadaan annetun vuoden ylläpitokohteet ja päällystysilmoitukset. Poistetun kohteen ei pitäisi tulla.
      (is (= 3 (count (:yllapitokohteet vastaus))) "Ylläpitokohteiden määrä vuonna 2021")
      (is (= 3 (count (:paallystysilmoitukset vastaus))) "Ylläpitokohteiden määrä vuonna 2021")

      (is (= #{{:nimi "Kohdeosa kaista 12",
                :paallystetyyppi nil,
                :raekoko nil,
                :tyomenetelma nil,
                :massamaara nil,
                :toimenpide "21"}
               {:nimi "Kohdeosa kaista 11",
                :paallystetyyppi nil,
                :raekoko nil,
                :tyomenetelma nil,
                :massamaara nil,
                :toimenpide "12"}}
             yllapitokohdeosadata))
      (let [[tallennettu-versio ilmoitustiedot] (first (q "SELECT versio, ilmoitustiedot FROM paallystysilmoitus
                                                           WHERE paallystyskohde = " paallystyskohde-id))]
        (is (= tallennettu-versio 2))
        (is (nil? ilmoitustiedot) "POT2:ssa kirjoittamme ne omille tauluille"))

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
        (is (= "POT2 lisätieto" (:lisatiedot paallystysilmoitus-kannassa)) "Tallenna lisätiedot")
        (is (= (:kokonaishinta-ilman-maaramuutoksia paallystysilmoitus-kannassa) 0M))
        (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))))

(deftest tallenna-uusi-pot2-paallystysilmoitus-kantaan-vaikka-paallystyskerros-ei-validi
  (let [;; Ei saa olla POT ilmoitusta
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")]
    (is (some? paallystyskohde-id))
    (is (= 28 paallystyskohde-id))
    (u (str "UPDATE yllapitokohdeosa SET toimenpide = 'Wut' WHERE yllapitokohde = 28"))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi pot2 ilmoitus")
    (let [paallystysilmoitus (-> pot2-testidata
                               (assoc :paallystyskohde-id paallystyskohde-id)
                               ;; Aseta virheellinen tr-loppuetaisyys (5000 ylittyy)
                               (assoc-in [:paallystekerros 0 :tr-loppuetaisyys] 5001)
                               (assoc-in [:perustiedot :valmis-kasiteltavaksi] false))
          [urakka-id sopimus-id vastaus yllapitokohdeosadata] (tallenna-testipaallystysilmoitus
                                                                paallystysilmoitus
                                                                paallystysilmoitus-domain/pot2-vuodesta-eteenpain)]
      ;; Vastauksena saadaan annetun vuoden ylläpitokohteet ja päällystysilmoitukset. Poistetun kohteen ei pitäisi tulla.
      (is (= 3 (count (:yllapitokohteet vastaus))) "Ylläpitokohteiden määrä vuonna 2021")
      (is (= 3 (count (:paallystysilmoitukset vastaus))) "Ylläpitokohteiden määrä vuonna 2021")

      (let [paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                          :urakan-paallystysilmoitus-paallystyskohteella
                                          +kayttaja-jvh+ {:urakka-id urakka-id
                                                          :sopimus-id sopimus-id
                                                          :paallystyskohde-id paallystyskohde-id})]
        (log/debug "Testitallennus valmis. POTTI kannassa: " (pr-str paallystysilmoitus-kannassa))
        (is (not (nil? paallystysilmoitus-kannassa)))
        (is (= (:tila paallystysilmoitus-kannassa) :aloitettu))

        (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))))


(deftest paivittaa-pot2-paallystysilmoitus-luonnostilassa-vaikka-epavalidi
  (let [;; Ei saa olla POT ilmoitusta
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")]
    (is (= 28 paallystyskohde-id))
    (u (str "UPDATE yllapitokohdeosa SET toimenpide = 'Freude' WHERE yllapitokohde = 28"))
    (let [alkuperainen-paallystysilmoitus (-> pot2-testidata
                                            (assoc :paallystyskohde-id paallystyskohde-id)
                                            (assoc-in [:perustiedot :valmis-kasiteltavaksi] false))
          _ (tallenna-testipaallystysilmoitus
              alkuperainen-paallystysilmoitus
              paallystysilmoitus-domain/pot2-vuodesta-eteenpain)
          uusi-paallystysilmoitus (-> alkuperainen-paallystysilmoitus
                                    (assoc-in [:paallystekerros 0 :nimi] "Uusi uudistettu tie 22")
                                    ;; Aseta virheellinen tr-loppuetaisyys (5000 ylittyy)
                                    (assoc-in [:paallystekerros 0 :tr-loppuetaisyys] 5001))

          ;; Päivitä päällystysilmoitus käyttäen epävalidia dataa
          _  (tallenna-testipaallystysilmoitus uusi-paallystysilmoitus 2020)]

    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id))))

(deftest tallenna-pot2-paallystysilmoitus-ei-salli-null-materiaali-paallystyskerroksessa
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystysilmoitus (-> pot2-testidata
                               (assoc :paallystyskohde-id paallystyskohde-id)
                               (assoc-in [:perustiedot :valmis-kasiteltavaksi] true)
                               (update-in [:paallystekerros 0] dissoc :materiaali))]
    (is (thrown-with-msg? IllegalArgumentException #"Materiaali on valinnainen vain jos toimenpide on KAR"
                          (tallenna-pot2-testi-paallystysilmoitus urakka-id sopimus-id paallystyskohde-id paallystysilmoitus)))))

(deftest tallenna-pot2-paallystysilmoitus-salli-null-materiaali-vain-jos-on-kar-toimenpide
  (let [kar-toimenpide pot2-domain/+kulutuskerros-toimenpide-karhinta+
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystysilmoitus (-> pot2-testidata
                             (assoc :paallystyskohde-id paallystyskohde-id)
                             (assoc-in [:perustiedot :valmis-kasiteltavaksi] true)
                             (update-in [:paallystekerros 0] dissoc :materiaali)
                             (assoc-in [:paallystekerros 0 :toimenpide] kar-toimenpide))
        paallystysilmoitus-kannassa-jalkeen (nth (tallenna-pot2-testi-paallystysilmoitus
                                                   urakka-id sopimus-id paallystyskohde-id paallystysilmoitus) 1 nil)]
    (is (nil? (get-in paallystysilmoitus-kannassa-jalkeen [:paallystekerros 0 :materiaali])))
    (is (= kar-toimenpide (get-in paallystysilmoitus-kannassa-jalkeen [:paallystekerros 0 :toimenpide])))))

(deftest tallenna-pot2-paallystysilmoitus-testaa-paallystys-hypyt
  (let [toimenpide pot2-domain/+masuunikuonan-sideainetyyppi-koodi+
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        aet 1067
        paallystysilmoitus (-> pot2-testidata
                             (assoc :paallystyskohde-id paallystyskohde-id)
                             (assoc-in [:paallystekerros 0 :toimenpide] toimenpide)

                             ;; Tehdään manuaalisesti hyppy
                             (assoc-in [:paallystekerros 0 :tr-alkuosa] 1)
                             (assoc-in [:paallystekerros 0 :tr-loppuosa] 1)
                             (assoc-in [:paallystekerros 0 :tr-loppuetaisyys] aet)
                             (assoc-in [:paallystekerros 0 :tr-alkuetaisyys] 1066)
                             (assoc-in [:paallystekerros 0 :tr-numero] 20)
                             (assoc-in [:paallystekerros 0 :tr-kaista] 12)
                             (assoc-in [:paallystekerros 0 :tr-ajorata] 1)
                             (assoc-in [:paallystekerros 0 :tr-ajorata] 1)

                             (assoc-in [:paallystekerros 1 :tr-alkuosa] 1)
                             (assoc-in [:paallystekerros 1 :tr-loppuosa] 1)
                             (assoc-in [:paallystekerros 1 :tr-loppuetaisyys] 3827)
                             (assoc-in [:paallystekerros 1 :tr-alkuetaisyys] (+ aet 100))
                             (assoc-in [:paallystekerros 1 :tr-numero] 20)
                             (assoc-in [:paallystekerros 1 :tr-kaista] 12)
                             (assoc-in [:paallystekerros 1 :tr-ajorata] 1))

        paallystys-hyppy-olemassa (nth (tallenna-pot2-testi-paallystysilmoitus
                                         urakka-id sopimus-id paallystyskohde-id paallystysilmoitus) 1 nil)

        paallystysilmoitus (-> pot2-testidata
                             (assoc :paallystyskohde-id paallystyskohde-id)
                             (assoc-in [:paallystekerros 0 :toimenpide] toimenpide)

                             (assoc-in [:paallystekerros 0 :tr-alkuosa] 1)
                             (assoc-in [:paallystekerros 0 :tr-loppuosa] 1)
                             (assoc-in [:paallystekerros 0 :tr-loppuetaisyys] aet)
                             (assoc-in [:paallystekerros 0 :tr-alkuetaisyys] 1066)
                             (assoc-in [:paallystekerros 0 :tr-numero] 20)
                             (assoc-in [:paallystekerros 0 :tr-kaista] 12)
                             (assoc-in [:paallystekerros 0 :tr-ajorata] 1)
                             (assoc-in [:paallystekerros 0 :tr-ajorata] 1)

                             (assoc-in [:paallystekerros 1 :tr-alkuosa] 1)
                             (assoc-in [:paallystekerros 1 :tr-loppuosa] 1)
                             (assoc-in [:paallystekerros 1 :tr-loppuetaisyys] 3827)
                             (assoc-in [:paallystekerros 1 :tr-alkuetaisyys] aet)
                             (assoc-in [:paallystekerros 1 :tr-numero] 20)
                             (assoc-in [:paallystekerros 1 :tr-kaista] 12)
                             (assoc-in [:paallystekerros 1 :tr-ajorata] 1))

        paallystys-ei-hyppyja (nth (tallenna-pot2-testi-paallystysilmoitus
                                     urakka-id sopimus-id paallystyskohde-id paallystysilmoitus) 1 nil)]

    ;; Hyppy seuraavalla rivillä, korostetaan let
    (is (true? (get-in paallystys-hyppy-olemassa [:paallystekerros 0 :let-hyppy?])) "Korostetaan loppuetäisyys")
    (is (nil? (get-in paallystys-hyppy-olemassa [:paallystekerros 0 :aet-hyppy?])) "Ei korosteta alkuetäisyytt'")
    (is (= (get-in paallystys-hyppy-olemassa [:paallystekerros 0 :hyppyjen-maara]) 1) "Hyppyjen määrä löytyy")

    ;; Seuraavalla korostetaan aet
    (is (nil? (get-in paallystys-hyppy-olemassa [:paallystekerros 1 :let-hyppy?])) "Ei korosteta loppuetäisyyttä")
    (is (true? (get-in paallystys-hyppy-olemassa [:paallystekerros 1 :aet-hyppy?])) "Korostetaan alkuetäisyys")
    (is (= (get-in paallystys-hyppy-olemassa [:paallystekerros 1 :hyppyjen-maara]) 1) "Hyppyjen määrä löytyy")

    ;; Hyppyjä ei ole
    (is (nil? (get-in paallystys-ei-hyppyja [:paallystekerros 0 :let-hyppy?])) "Loppuetäisyyttä ei korosteta")
    (is (nil? (get-in paallystys-ei-hyppyja [:paallystekerros 0 :aet-hyppy?])) "Alkuetäisyyttä ei korosteta")
    (is (= (get-in paallystys-ei-hyppyja [:paallystekerros 0 :hyppyjen-maara]) 0) "Hyppyjä ei ole")))

(deftest tallenna-pot2-paallystysilmoitus-kohteen-alku-ja-loppupvm-muuttuvat
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystysilmoitus (-> pot2-testidata
                               (assoc :paallystyskohde-id paallystyskohde-id)
                               (assoc-in [:perustiedot :aloituspvm] #inst "2021-06-15T21:00:00.000-00:00")
                               (assoc-in [:perustiedot :valmispvm-kohde] #inst "2021-06-19T21:00:00.000-00:00"))
        [paallystysilmoitus-kannassa-ennen paallystysilmoitus-kannassa-jalkeen] (tallenna-pot2-testi-paallystysilmoitus
                                                                                  urakka-id sopimus-id paallystyskohde-id paallystysilmoitus)]
    (is (= (:aloituspvm paallystysilmoitus-kannassa-ennen) #inst "2021-06-18T21:00:00.000-00:00") "Kohteen aloituspvm ennen muutosta")
    (is (= (:aloituspvm paallystysilmoitus-kannassa-jalkeen) #inst "2021-06-15T21:00:00.000-00:00") "Kohteen aloituspvm muutoksen jälkeen")
    (is (nil? (:valmispvm-kohde paallystysilmoitus-kannassa-ennen)) "Kohteen valmispvm-kohde ennen muutosta")
    (is (= (:valmispvm-kohde paallystysilmoitus-kannassa-jalkeen) #inst "2021-06-19T21:00:00.000-00:00") "Kohteen valmispvm-kohde muutoksen jälkeen")))

(deftest tallenna-pot2-paallystysilmoitus-throwaa-jos-loppupvm-puuttuu
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Aloittamaton kohde mt20")
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystysilmoitus (-> pot2-testidata
                               (assoc :paallystyskohde-id paallystyskohde-id)
                               (assoc-in [:perustiedot :aloituspvm] #inst "2021-06-15T21:00:00.000-00:00")
                               (assoc-in [:perustiedot :valmispvm-kohde] nil))]
    (is (thrown? IllegalArgumentException
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                              :sopimus-id         sopimus-id
                                                                              :vuosi              2021
                                                                              :paallystysilmoitus paallystysilmoitus})))))

(deftest ei-saa-paivittaa-jos-on-vaara-versio
  (let [paallystyskohde-vanha-pot-id (ffirst (q "SELECT id FROM yllapitokohde WHERE nimi = 'Ouluntie'"))]
    (is (not (nil? paallystyskohde-vanha-pot-id)))
    (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
          paallystysilmoitus-pot2 (-> pot2-testidata
                                      (assoc :paallystyskohde-id paallystyskohde-vanha-pot-id)
                                      (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))]
      (is (thrown-with-msg? IllegalArgumentException #"Väärä POT versio. Pyynnössä on 2, pitäisi olla 1. Ota yhteyttä Harjan tukeen."
                            (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                                         :sopimus-id         sopimus-id
                                                                                         :vuosi              paallystysilmoitus-domain/pot2-vuodesta-eteenpain
                                                                                         :paallystysilmoitus paallystysilmoitus-pot2}))))))

(deftest ei-saa-paivittaa-jos-ei-ole-versiota
    (let [paallystysilmoitus-pot2 (-> pot-testidata
                                      (dissoc :versio)
                                      (assoc :paallystyskohde-id 123))]
      (is (thrown-with-msg? IllegalArgumentException #"Pyynnöstä puuttuu versio. Ota yhteyttä Harjan tukeen."
                            (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          1
                                                                                         :sopimus-id         1
                                                                                         :vuosi              2020
                                                                                         :paallystysilmoitus paallystysilmoitus-pot2})))))

(defn- alustarivi-idlla-loytyy?
  "Palauttaa booleanin, löytyykö annetulla pot2a_id:llä riviä"
  [alustarivit id]
  (boolean (some? (first (filter #(= id (:pot2a_id %)) alustarivit)))))

(defn- alustarivi-idlla
  "Palauttaa rivi, annetulla pot2a_id:llä"
  [alustarivit id]
  (first (filter #(= id (:pot2a_id %)) alustarivit)))

(def pot2-alustatestien-ilmoitus
  {:perustiedot {:tila :aloitettu,
                 :tr-kaista nil,
                 :kohdenimi "Tärkeä kohde mt20",
                 :kohdenumero "L42",
                 :tr-ajorata nil,
                 :kommentit [],
                 :tr-loppuosa 1,
                 :valmispvm-kohde #inst "2021-06-23T21:00:00.000-00:00",
                 :tunnus nil,
                 :tr-alkuosa 1,
                 :versio 2,
                 :tr-loppuetaisyys 3827,
                 :aloituspvm #inst "2021-06-18T21:00:00.000-00:00",
                 :takuupvm #inst "2024-12-30T22:00:00.000-00:00",
                 :tr-osoite {:tr-kaista nil, :tr-ajorata nil, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 3827,
                             :tr-alkuetaisyys 1066, :tr-numero 20},
                 :asiatarkastus {:lisatiedot nil, :hyvaksytty nil, :tarkastusaika nil, :tarkastaja nil},
                 :tr-alkuetaisyys 1066, :tr-numero 20,
                 :tekninen-osa {:paatos nil, :kasittelyaika nil, :perustelu nil},
                 :valmispvm-paallystys #inst "2021-06-20T21:00:00.000-00:00"},
   :paallystyskohde-id 27,
   :versio 2,
   :lisatiedot "POT2 alustatesti ilmoitus"
   :ilmoitustiedot nil,
   :paallystekerros [{:kohdeosa-id 11, :tr-kaista 11, :leveys 3, :kokonaismassamaara 5000,
                    :tr-ajorata 1, :pinta_ala 15000, :tr-loppuosa 1, :jarjestysnro 1,
                    :tr-alkuosa 1, :massamenekki 333, :tr-loppuetaisyys 1500, :nimi "Tärkeä kohdeosa kaista 11",
                    :materiaali 1, :tr-alkuetaisyys 1066, :piennar true, :tr-numero 20, :toimenpide 22, :pot2p_id 1}
                   {:kohdeosa-id 12, :tr-kaista 12, :leveys 3, :kokonaismassamaara 5000,
                    :tr-ajorata 1, :pinta_ala 15000, :tr-loppuosa 1, :jarjestysnro 1,
                    :tr-alkuosa 1, :massamenekki 333, :tr-loppuetaisyys 3827, :nimi "Tärkeä kohdeosa kaista 12",
                    :materiaali 2, :tr-alkuetaisyys 1066, :piennar false, :tr-numero 20, :toimenpide 23, :pot2p_id 2}],
   :alusta [{:tr-kaista 11, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 1500, :materiaali 1,
             :tr-alkuetaisyys 1066, :tr-numero 20, :toimenpide 32, :pot2a_id 1,
             :massa 1, :kokonaismassamaara 100, :massamenekki 5}]})

(def pot2-alusta-esimerkki
  [{:tr-kaista       11, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 1500,
    :materiaali      1, :pituus 434,
    :tr-alkuetaisyys 1066, :tr-numero 20, :toimenpide 32,
    :massa           1, :massamenekki 100}
   {:tr-kaista       12, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 1500,
    :materiaali      1, :pituus 434,
    :tr-alkuetaisyys (inc 1066), :tr-numero 20, :toimenpide 32,
    :massa           1, :massamenekki 100}
   {:tr-kaista       12, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys (- 2000 1),
    :materiaali      1, :pituus 500,
    :tr-alkuetaisyys 1500, :tr-numero 20, :toimenpide 11,
    :kasittelysyvyys 55, :sideaine 1, :sideainepitoisuus 10.0M, :murske 1}
   {:tr-kaista       12, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 2500,
    :materiaali      1, :pituus 500,
    :tr-alkuetaisyys 2000, :tr-numero 20, :toimenpide 3,
    :verkon-tyyppi 1 :verkon-tarkoitus 2 :verkon-sijainti 3}])

(deftest tallenna-pot2-poista-alustarivi
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        [paallystysilmoitus-kannassa-ennen paallystysilmoitus-kannassa-jalkeen] (tallenna-pot2-testi-paallystysilmoitus
                                                                                  urakka-id sopimus-id paallystyskohde-id pot2-alustatestien-ilmoitus)
        alustarivit-ennen (:alusta paallystysilmoitus-kannassa-ennen)
        alustarivit-jalkeen (:alusta paallystysilmoitus-kannassa-jalkeen)]
    (is (not (nil? paallystysilmoitus-kannassa-ennen)))
    (is (= (:versio paallystysilmoitus-kannassa-ennen) 2))
    (is (= 6 (count alustarivit-ennen)))
    (is (= 1 (count alustarivit-jalkeen)))
    (is (alustarivi-idlla-loytyy? alustarivit-ennen 1) "alusta id:llä 1 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-ennen 2) "alusta id:llä 2 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-jalkeen 1) "alusta id:llä 1 löytyy")
    (is (not (alustarivi-idlla-loytyy? alustarivit-jalkeen 2)) "alusta id:llä 2 ei saa löytyä")
    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

(deftest tallenna-pot2-lisaa-alustarivi-ja-verkko-tiedot
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        paallystysilmoitus (assoc pot2-alustatestien-ilmoitus :alusta pot2-alusta-esimerkki)
        ;; Tehdään tallennus joka lisää kaksi alustariviä
        [paallystysilmoitus-kannassa-ennen paallystysilmoitus-kannassa-jalkeen] (tallenna-pot2-testi-paallystysilmoitus
                                                                                  urakka-id sopimus-id paallystyskohde-id paallystysilmoitus)
        alustarivit-ennen (:alusta paallystysilmoitus-kannassa-ennen)
        alustarivit-jalkeen (:alusta paallystysilmoitus-kannassa-jalkeen)
        alustarivi-15 (alustarivi-idlla alustarivit-jalkeen 15)]
    (is (not (nil? paallystysilmoitus-kannassa-ennen)))
    (is (= (:versio paallystysilmoitus-kannassa-ennen) 2))
    (is (= 6 (count alustarivit-ennen)))
    (is (= 4 (count alustarivit-jalkeen)))
    (is (alustarivi-idlla-loytyy? alustarivit-ennen 1) "alusta id:llä 1 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-ennen 2) "alusta id:llä 2 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-ennen 3) "alusta id:llä 3 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-ennen 4) "alusta id:llä 4 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-ennen 5) "alusta id:llä 5 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-ennen 6) "alusta id:llä 6 löytyy")
    (is (not (alustarivi-idlla-loytyy? alustarivit-jalkeen 7)) "alusta id:llä 7 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-jalkeen 12) "alusta id:llä 12 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-jalkeen 13) "alusta id:llä 13 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-jalkeen 14) "alusta id:llä 14 löytyy")
    (is (alustarivi-idlla-loytyy? alustarivit-jalkeen 15) "alusta id:llä 15 löytyy")
    (is (= {:verkon-tyyppi 1 :verkon-tarkoitus 2 :verkon-sijainti 3}
           (select-keys alustarivi-15 [:verkon-tyyppi :verkon-tarkoitus :verkon-sijainti])))
    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

(deftest tallenna-pot2-lisaa-alustarivi-ja-vain-pakolliset-verkko-tiedot
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                               (assoc :alusta pot2-alusta-esimerkki)
                               (update-in [:alusta 3] dissoc :verkon-tarkoitus))
        [_ paallystysilmoitus-kannassa-jalkeen] (tallenna-pot2-testi-paallystysilmoitus
                                                                                  urakka-id sopimus-id paallystyskohde-id paallystysilmoitus)
        alustarivit-jalkeen (:alusta paallystysilmoitus-kannassa-jalkeen)
        alustarivi-15 (alustarivi-idlla alustarivit-jalkeen 15)]
    (is (= {:verkon-tyyppi 1 :verkon-tarkoitus nil :verkon-sijainti 3}
           (select-keys alustarivi-15 [:verkon-tyyppi :verkon-tarkoitus :verkon-sijainti])))
    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

(deftest tallenna-pot2-lisaa-alustarivi-ja-vain-pakolliset-tas-tiedot
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                               (assoc :alusta pot2-alusta-esimerkki)
                               (update-in [:alusta 2] dissoc :murske :massamenekki))
        [_ paallystysilmoitus-kannassa-jalkeen] (tallenna-pot2-testi-paallystysilmoitus
                                                  urakka-id sopimus-id paallystyskohde-id paallystysilmoitus)
        alustarivit-jalkeen (:alusta paallystysilmoitus-kannassa-jalkeen)
        alustarivi-9 (alustarivi-idlla alustarivit-jalkeen 14)]
    (is (= {:kasittelysyvyys 55, :sideaine 1, :sideainepitoisuus 10.0M, :murske nil, :massamenekki nil}
           (select-keys alustarivi-9 [:kasittelysyvyys :sideaine :sideainepitoisuus :murske :massamenekki])))
    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

(deftest tallenna-pot2-jossa-on-alikohde-muulla-tiella-lisaa-alustarivi
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        muu-tr-numero 7777
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                               (assoc-in [:paallystekerros 2]
                                         {:kohdeosa-id 13, :tr-kaista 11, :leveys 3, :kokonaismassamaara 5000,
                                          :tr-ajorata 1, :pinta_ala 15000, :tr-loppuosa 10, :jarjestysnro 1,
                                          :tr-alkuosa 10, :massamenekki 333, :tr-loppuetaisyys 1500, :nimi "Muu tie",
                                          :materiaali 2, :tr-alkuetaisyys 1066, :piennar false,
                                          :tr-numero muu-tr-numero, :toimenpide 23, :pot2p_id 3})
                               (assoc :alusta pot2-alusta-esimerkki)
                               (assoc-in [:alusta 4]
                                         {:tr-kaista 11, :tr-ajorata 1, :tr-loppuosa 10, :tr-alkuosa 10, :tr-loppuetaisyys 1500,
                                          :materiaali 1, :pituus 434,
                                          :tr-alkuetaisyys 1066, :tr-numero muu-tr-numero, :toimenpide 32,
                                          :kokonaismassamaara 100, :massa 1, :massamenekki 20}))
        ;; Tehdään tallennus joka lisää kaksi alustariviä
        [_ paallystysilmoitus-kannassa-jalkeen] (tallenna-pot2-testi-paallystysilmoitus
                                                                                  urakka-id sopimus-id paallystyskohde-id paallystysilmoitus)
        alustarivit-jalkeen (:alusta paallystysilmoitus-kannassa-jalkeen)]
    (is (= 5 (count alustarivit-jalkeen)))
    (is (= #{{:pot2a_id 14
              :tr-numero 20}
             {:pot2a_id 15
              :tr-numero 20}
             {:pot2a_id 16
              :tr-numero 7777}
             {:pot2a_id 12
              :tr-numero 20}
             {:pot2a_id 13
              :tr-numero 20}}
           (clojure.set/project alustarivit-jalkeen [:pot2a_id :tr-numero])))
    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

;; Jossain tilanteessa heitettiin virhe Liian herkästi muun kohteen päällekkäisyydestä vaikka ei ollut
(deftest tallenna-pot2-jossa-on-alikohde-muulla-tiella-validointi-ottaa-tie-huomioon.
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        muu-tr-numero 837
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                               (dissoc :alusta)
                               (dissoc :paallystekerros)
                               (assoc :paallystekerros
                                      [{:kohdeosa-id 13, :tr-kaista 11, :leveys 3, :kokonaismassamaara 5000,
                                        :tr-ajorata 0, :pinta_ala 15000, :tr-loppuosa 2, :jarjestysnro 1,
                                        :tr-alkuosa 2, :massamenekki 333, :tr-loppuetaisyys 1100, :nimi "Muu tie 1",
                                        :materiaali 2, :tr-alkuetaisyys 1001, :piennar false,
                                        :tr-numero muu-tr-numero, :toimenpide 23}
                                       {:kohdeosa-id nil, :tr-kaista 11, :leveys 3, :kokonaismassamaara 5000,
                                        :tr-ajorata 0, :pinta_ala 15000, :tr-loppuosa 2, :jarjestysnro 1,
                                        :tr-alkuosa 2, :massamenekki 333, :tr-loppuetaisyys 1110, :nimi "Muu tie 2",
                                        :materiaali 2, :tr-alkuetaisyys 1100, :piennar false,
                                        :tr-numero muu-tr-numero, :toimenpide 23}]))
        ;; Tehdään tallennus joka lisää kaksi alustariviä
        [_ paallystysilmoitus-kannassa-jalkeen] (tallenna-pot2-testi-paallystysilmoitus
                                                  urakka-id sopimus-id paallystyskohde-id paallystysilmoitus)
        paallystekerrokset-jalkeen (:paallystekerros paallystysilmoitus-kannassa-jalkeen)]
    #_(poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

(deftest ei-saa-tallenna-pot2-paallystysilmoitus-jos-alustarivi-on-tiella-joka-ei-loydy-kulutuskerroksesta
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        muu-tr-numero 7777
        vaara-tr-numero 5555
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                               (assoc-in [:paallystekerros 2]
                                         {:kohdeosa-id 13, :tr-kaista 11, :leveys 3, :kokonaismassamaara 5000,
                                          :tr-ajorata 1, :pinta_ala 15000, :tr-loppuosa 10, :jarjestysnro 1,
                                          :tr-alkuosa 10, :massamenekki 333, :tr-loppuetaisyys 1500, :nimi "Muu tie",
                                          :materiaali 2, :tr-alkuetaisyys 1066, :piennar false,
                                          :tr-numero muu-tr-numero, :toimenpide 23, :pot2p_id 3})
                               (assoc :alusta pot2-alusta-esimerkki)
                               (assoc-in [:alusta 4]
                                         {:tr-kaista 11, :tr-ajorata 1, :tr-loppuosa 10, :tr-alkuosa 10, :tr-loppuetaisyys 1500,
                                          :materiaali 1, :pituus 434,
                                          :tr-alkuetaisyys 1066, :tr-numero vaara-tr-numero, :toimenpide 32}))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2021
                                       "Alustatoimenpiteen täytyy olla samalla tiellä kuin jokin alikohteista. Tienumero 5555 ei ole.")))

(deftest tallenna-pot2-paivittaa-alustarivi-jossa-on-verkko-tiedot
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        alkuperaiset-verkon-tiedot {:verkon-tyyppi 1 :verkon-tarkoitus 2 :verkon-sijainti 3}
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                               (assoc :alusta pot2-alusta-esimerkki)
                               (update-in [:alusta 3] merge alkuperaiset-verkon-tiedot))
        [_ paallystysilmoitus-kannassa-uusi] (tallenna-pot2-testi-paallystysilmoitus
                                                                                  urakka-id sopimus-id paallystyskohde-id paallystysilmoitus)
        alustarivit-uudet (:alusta paallystysilmoitus-kannassa-uusi)
        paivitetyt-verkon-tiedot {:verkon-tyyppi 9 :verkon-tarkoitus 1 :verkon-sijainti 2}
        paivitetty-paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                                          (assoc :alusta alustarivit-uudet)
                                          (update-in [:alusta 3] merge paivitetyt-verkon-tiedot))
        [_ paallystysilmoitus-kannassa-paivitetty] (tallenna-pot2-testi-paallystysilmoitus
                                                  urakka-id sopimus-id paallystyskohde-id paivitetty-paallystysilmoitus)
        alustarivit-paivitetyt (:alusta paallystysilmoitus-kannassa-paivitetty)
        paivitetty-alustarivi-15 (alustarivi-idlla alustarivit-paivitetyt 15)]
    (is (some? paivitetty-alustarivi-15) "alusta id:llä 15 löytyy")
    (is (= paivitetyt-verkon-tiedot (select-keys paivitetty-alustarivi-15 [:verkon-tyyppi :verkon-tarkoitus :verkon-sijainti])))
    (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))

(deftest ei-saa-tallenna-pot2-paallystysilmoitus-jos-alustarivilla-ei-ole-kaikki-pakolliset-verkontiedot
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                               (assoc :alusta pot2-alusta-esimerkki)
                               (update-in [:alusta 3] dissoc :verkon-sijainti))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2021
                                       "Alustassa väärät lisätiedot.")))

(deftest saa-tallentaa-pot2-jos-sama-tr-osoite-samalla-alustatoimenpiteella
  (let [kohteen-nimi "Tärkeä kohde mt20"
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        vuosi 2021
        paallystyskohde-id (hae-yllapitokohteen-id-nimella kohteen-nimi)
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                             (assoc :alusta pot2-alusta-esimerkki)
                             (update-in [:alusta 1] merge {:tr-kaista 11}))
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
            :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                         :sopimus-id sopimus-id
                                                         :vuosi vuosi
                                                         :paallystysilmoitus paallystysilmoitus})
        eka-odotettu-alustarivi {:tr-kaista 11, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 1500,
                                 :tr-alkuetaisyys 1066, :tr-numero 20, :toimenpide 32,
                                 :massa 1, :massamenekki 100M}
        toka-odotettu-alustarivi {:tr-kaista 11, :tr-ajorata 1, :tr-loppuosa 1, :tr-alkuosa 1, :tr-loppuetaisyys 1500,
                                  :tr-alkuetaisyys (inc 1066), :tr-numero 20, :toimenpide 32,
                                  :massa 1, :massamenekki 100M}
        tallennuksen-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                               :urakan-paallystysilmoitus-paallystyskohteella +kayttaja-jvh+
                               {:urakka-id urakka-id
                                :sopimus-id sopimus-id
                                :paallystyskohde-id paallystyskohde-id})
        nimi-tallennuksen-jalkeen (:kohdenimi tallennuksen-jalkeen)
        alusta-tallennuksen-jalkeen (:alusta tallennuksen-jalkeen)
        paallystekerros-tallennuksen-jalkeen (:paallystekerros tallennuksen-jalkeen)
        normalisoidut-alustarivit (map #(select-keys % [:tr-kaista
                                                        :tr-ajorata
                                                        :tr-loppuosa
                                                        :tr-alkuosa
                                                        :tr-loppuetaisyys
                                                        :materiaali
                                                        :pituus
                                                        :tr-alkuetaisyys
                                                        :tr-numero
                                                        :toimenpide
                                                        :massa
                                                        :massamenekki])
                                    alusta-tallennuksen-jalkeen)]
    (is (= nimi-tallennuksen-jalkeen kohteen-nimi) "Kohteen nimi")
    (is (= 2 (count paallystekerros-tallennuksen-jalkeen)) "paallystekerros-tallennuksen-jalkeen rivien määrä")
    (is (some #(= eka-odotettu-alustarivi %) normalisoidut-alustarivit) "Ensimmäinen päällekkäisistä alustariveistä")
    (is (some #(= toka-odotettu-alustarivi %) normalisoidut-alustarivit) "Toinen päällekkäisistä alustariveistä")))

(deftest ei-saa-tallenna-pot2-paallystysilmoitus-jos-alustarivilla-on-vaarat-lisatiedot
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        verkon-tiedot {:verkon-tyyppi 1 :verkon-sijainti 3 :verkon-tarkoitus 1 :massamenekki 1}
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                               (assoc :alusta pot2-alusta-esimerkki)
                               (update-in [:alusta 3] merge verkon-tiedot))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2021
                                       "Alustassa väärät lisätiedot.")))

(deftest ei-saa-tallenna-pot2-paallystysilmoitus-jos-alustarivilla-on-vaarat-verkontiedot
  (let [paallystyskohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        verkon-tiedot {:verkon-tyyppi 1 :verkon-tarkoitus 333 :verkon-sijainti 3}
        paallystysilmoitus (-> pot2-alustatestien-ilmoitus
                               (assoc :alusta pot2-alusta-esimerkki)
                               (update-in [:alusta 3] merge verkon-tiedot))]
    (tallenna-vaara-paallystysilmoitus paallystyskohde-id paallystysilmoitus 2021
                                       {"alustatoimenpide""ERROR: insert or update on table \"pot2_alusta\" violates foreign key constraint \"pot2_alusta_verkon_tarkoitus_fkey\"\n  Detail: Key (verkon_tarkoitus)=(333) is not present in table \"pot2_verkon_tarkoitus\"."}
                                       )))

(deftest uuden-paallystysilmoituksen-tallennus-eri-urakkaan-ei-onnistu
  (let [paallystyskohde-id (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)]
    (is (some? paallystyskohde-id))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi ilmoitus")
    (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
          sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)]
      (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                          :sopimus-id sopimus-id
                                                                                          :vuosi 2019
                                                                                          :paallystysilmoitus paallystysilmoitus}))))))

(deftest paivita-paallystysilmoitukselle-paatostiedot
  (let [paallystyskohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)]
    (is (some? paallystyskohde-id))

    (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
          paallystysilmoitus (-> pot-testidata
                                 (assoc :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :tekninen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:perustiedot :tekninen-osa :perustelu] "Hyvä ilmoitus!"))
          fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-utajarven-paallystysurakan-kayttajat.xml"))
          viesti-id (str (UUID/randomUUID))]

      (with-fake-http
        [+testi-fim+ fim-vastaus
         {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
        (kutsu-palvelua (:http-palvelin jarjestelma)
         :tallenna-paallystysilmoitus +kayttaja-jvh+
         {:urakka-id urakka-id
          :sopimus-id sopimus-id
          :vuosi 2019
          :paallystysilmoitus paallystysilmoitus}))
      (let [paallystysilmoitus-kannassa
            (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-paallystysilmoitus-paallystyskohteella +kayttaja-jvh+
                            {:urakka-id urakka-id
                             :sopimus-id sopimus-id
                             :paallystyskohde-id paallystyskohde-id})]
        (is (some? paallystysilmoitus-kannassa))
        (is (= (:tila paallystysilmoitus-kannassa) :lukittu))
        (is (= (get-in paallystysilmoitus-kannassa [:tekninen-osa :paatos]) :hyvaksytty))
        (is (= (get-in paallystysilmoitus-kannassa [:tekninen-osa :perustelu])
               (get-in paallystysilmoitus [:perustiedot :tekninen-osa :perustelu])))

        ;; Tie 22 tiedot tallentuivat kantaan, mutta tie 20 ei koska oli poistettu
        (is (some #(= (:nimi %) "Tie 22")
                  (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet])))
        (is (not (some #(= (:nimi %) "Tie 20")
                       (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet]))))
        (is (= (update-in (:ilmoitustiedot paallystysilmoitus-kannassa) [:osoitteet 0] (fn [osoite]
                                                                                         (dissoc osoite :kohdeosa-id)))
               {:alustatoimet [{:kasittelymenetelma 1
                                :paksuus 1234
                                :tekninen-toimenpide 1
                                :tr-numero 22
                                :tr-kaista 11
                                :tr-ajorata 1
                                :tr-alkuetaisyys 3
                                :tr-alkuosa 3
                                :tr-loppuetaisyys 5
                                :tr-loppuosa 3
                                :verkkotyyppi 1
                                :verkon-sijainti 1
                                :verkon-tarkoitus 1}]
                :osoitteet [{;; Alikohteen tiedot
                             :nimi "Tie 22"
                             :tr-numero 22
                             :tr-alkuosa 3
                             :tr-alkuetaisyys 3
                             :tr-loppuosa 3
                             :tr-loppuetaisyys 5
                             :tr-ajorata 1
                             :tr-kaista 11
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
                                                        :vuosi 2019
                                                        :paallystysilmoitus paallystysilmoitus})))

        (u (str "UPDATE paallystysilmoitus SET
                      tila = NULL,
                      paatos_tekninen_osa = NULL,
                      perustelu_tekninen_osa = NULL
                  WHERE paallystyskohde =" paallystyskohde-id ";"))))))

(deftest lisaa-kohdeosa
  (let [paallystyskohde-id (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)]
    (is (some? paallystyskohde-id))

    (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
          paallystysilmoitus (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
          hae-kohteiden-maara #(count (q (str "SELECT * FROM yllapitokohdeosa"
                                              " WHERE poistettu IS NOT TRUE "
                                              " AND yllapitokohde = " paallystyskohde-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :sopimus-id sopimus-id
                       :vuosi 2019
                       :paallystysilmoitus paallystysilmoitus})

      (let [kohteita-ennen-lisaysta (hae-kohteiden-maara)
            paallystysilmoitus (update-in paallystysilmoitus [:ilmoitustiedot :osoitteet]
                                          conj {;; Alikohteen tiedot
                                                :nimi "Tie 123"
                                                :tr-numero 666
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
                         :vuosi 2019
                         :paallystysilmoitus paallystysilmoitus})

        (is (= (inc kohteita-ennen-lisaysta) (hae-kohteiden-maara)) "Kohteita on nyt 1 enemmän")))))

(deftest ala-paivita-paallystysilmoitukselle-paatostiedot-jos-ei-oikeuksia
  (let [paallystyskohde-id (hae-muhoksen-yllapitokohde-ilman-paallystysilmoitusta)]
    (is (some? paallystyskohde-id))

    (let [urakka-id @muhoksen-paallystysurakan-id
          sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :tekninen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:perustiedot :tekninen-osa :perustelu]
                                           "Yritän saada ilmoituksen hyväksytyksi ilman oikeuksia."))]

      (is (thrown? RuntimeException
                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :tallenna-paallystysilmoitus +kayttaja-tero+
                                   {:urakka-id urakka-id
                                    :sopimus-id sopimus-id
                                    :vuosi 2018
                                    :paallystysilmoitus paallystysilmoitus}))))))


(deftest yllapitokohteiden-maksuerien-haku-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-paallystyksen-maksuerat
                                +kayttaja-jvh+
                                {::urakka-domain/id urakka-id
                                 ::sopimus-domain/id sopimus-id
                                 ::urakka-domain/vuosi 2017})
        leppajarven-ramppi (yllapitokohteet-test/kohde-nimella vastaus "Leppäjärven ramppi")]
    (is (= (count vastaus) 5) "Kaikki kohteet palautuu")
    (is (== (:kokonaishinta leppajarven-ramppi) 7248.95))
    (is (= (count (:maksuerat leppajarven-ramppi)) 2))))

(deftest yllapitokohteen-uusien-maksuerien-tallennus-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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

        (is (= (count vastaus) 5) "Kaikki kohteet palautuu")
        (is (= (map #(dissoc % :id) (:maksuerat leppajarven-ramppi))
               tallennettavat-maksuerat))))))

(deftest yllapitokohteen-maksuerien-paivitys-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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

      (is (= (count vastaus) 5) "Kaikki kohteet palautuu")
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
  (let [kohde-id (hae-yllapitokohteen-id-nimella "Oulun ohitusramppi")
        payload {::urakka-domain/id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                 ::paallystysilmoitus-domain/paallystyskohde-id kohde-id
                 ::paallystysilmoitus-domain/tila :valmis}
        tila-ennen-kutsua (keyword (second (first (q (str "SELECT id, tila FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id)))))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :aseta-paallystysilmoituksen-tila +kayttaja-jvh+ payload)]
    (is (= :lukittu tila-ennen-kutsua))
    (is (= :valmis (:tila vastaus)))))

(deftest avaa-lukitun-potin-lukitus-ei-sallittu-koska-tero-ei-tassa-urakanvalvojana
  (let [kohde-id (hae-yllapitokohteen-id-nimella "Oulun ohitusramppi")
        payload {::urakka-domain/id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                 ::paallystysilmoitus-domain/paallystyskohde-id kohde-id
                 ::paallystysilmoitus-domain/tila :valmis}]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :aseta-paallystysilmoituksen-tila
                                           +kayttaja-tero+
                                           payload)))
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :aseta-paallystysilmoituksen-tila
                                           +kayttaja-vastuuhlo-muhos+
                                           payload)))))

(deftest sahkopostien-lahetys
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
        paallystysilmoitus (-> pot-testidata
                             (assoc :paallystyskohde-id paallystyskohde-id)
                             (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [{:url +testi-fim+ :method :get} fim-vastaus
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]


    (let [vastaus (future (kutsu-palvelua (:http-palvelin jarjestelma)
                            :tallenna-paallystysilmoitus
                            +kayttaja-jvh+ {:urakka-id urakka-id
                                            :sopimus-id sopimus-id
                                            :vuosi 2018
                                            :paallystysilmoitus paallystysilmoitus}))
          _ (odota-ehdon-tayttymista #(realized? vastaus) "Saatiin vastaus :tallenna-paallystysilmoitus" ehdon-timeout)
          _ (Thread/sleep 1000)
          integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))
          integraatiotapahtumat (q-map (str "select id, integraatio, alkanut, paattynyt, lisatietoja, onnistunut, ulkoinenid FROM integraatiotapahtuma"))
          ;;Hyväksytään ilmoitus ja lähetetään tästä urakan valvojalle sähköposti
          paallystysilmoitus (-> (assoc pot-testidata
                                   :paallystyskohde-id paallystyskohde-id)
                               (assoc-in [:perustiedot :tekninen-osa :paatos] :hyvaksytty)
                               (assoc-in [:perustiedot :tekninen-osa :perustelu] "Hyvä ilmoitus!"))]

      ;; Ensimmäinen integraatioviesti sisältää tiedot haetuista FIM käyttäjistä, joten toisessa pitäisi olla sähköposti, johon viesti lähetettiin
      (is (clojure.string/includes? (:sisalto (second integraatioviestit)) "ELY_Urakanvalvoja@example.com"))
      (is (= (integraatio-kyselyt/integraation-id (:db jarjestelma) "fim" "hae-urakan-kayttajat") (:integraatio (first integraatiotapahtumat))))
      (is (= (integraatio-kyselyt/integraation-id (:db jarjestelma) "api" "sahkoposti-lahetys") (:integraatio (second integraatiotapahtumat))))))

      #_ (with-fake-http
        [+testi-fim+ fim-vastaus
         {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus (str (UUID/randomUUID)))]


      (let [_ (println "************************************************** uusi tallennnus ********************************' ")
            vastaus (future (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-paallystysilmoitus
                              +kayttaja-jvh+ {:urakka-id urakka-id
                                              :sopimus-id sopimus-id
                                              :vuosi 2018
                                              :paallystysilmoitus paallystysilmoitus}))
            _ (odota-ehdon-tayttymista #(realized? vastaus) "Saatiin vastaus :tallenna-paallystysilmoitus" ehdon-timeout)
            integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))
            _ (println "***************************** integraatioviestit" (pr-str integraatioviestit))
            integraatiotapahtumat (q-map (str "select id, integraatio, alkanut, paattynyt, lisatietoja, onnistunut, ulkoinenid FROM integraatiotapahtuma"))]
        ;; Viides integraatioviesti sisältää tiedot haetuista FIM käyttäjistä, joten kuudennessa pitäisi olla sähköposti, johon viesti lähetettiin
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 5)) "vastuuhenkilo@example.com"))))))

(deftest lisaa-paallystysilmoitukseen-kohdeosien-id
  (let [paallystysilmoitus {:ilmoitustiedot {:osoitteet [{:kohdeosa-id 1
                                                          :nimi "1 A"
                                                          :toimenpide-paallystetyyppi 1
                                                          :tr-ajorata 1
                                                          :tr-alkuetaisyys 1
                                                          :tr-alkuosa 1
                                                          :tr-kaista 1
                                                          :tr-loppuetaisyys 1000
                                                          :tr-loppuosa 1
                                                          :tr-numero 20}
                                                         {:kohdeosa-id 2
                                                          :nimi "1 B"
                                                          :toimenpide-paallystetyyppi 1
                                                          :tr-ajorata 1
                                                          :tr-alkuetaisyys 1500
                                                          :tr-alkuosa 1
                                                          :tr-kaista 1
                                                          :tr-loppuetaisyys 0
                                                          :tr-loppuosa 3
                                                          :tr-numero 20}
                                                         {:kohdeosa-id 3
                                                          :nimi "2 A"
                                                          :toimenpide-paallystetyyppi 1
                                                          :tr-ajorata 2
                                                          :tr-alkuetaisyys 1
                                                          :tr-alkuosa 1
                                                          :tr-kaista 21
                                                          :tr-loppuetaisyys 1000
                                                          :tr-loppuosa 1
                                                          :tr-numero 20}
                                                         {:kohdeosa-id 4
                                                          :nimi "2 B"
                                                          :toimenpide-paallystetyyppi 1
                                                          :tr-ajorata 2
                                                          :tr-alkuetaisyys 1500
                                                          :tr-alkuosa 1
                                                          :tr-kaista 21
                                                          :tr-loppuetaisyys 0
                                                          :tr-loppuosa 3
                                                          :tr-numero 20}]}}
        kohdeosat [{:id 1
                    :nimi "1 A"
                    :tr-ajorata 1
                    :tr-alkuetaisyys 1
                    :tr-alkuosa 1
                    :tr-kaista 1
                    :tr-loppuetaisyys 1000
                    :tr-loppuosa 1
                    :tr-numero 20}
                   {:id 2
                    :nimi "1 B"
                    :tr-ajorata 1
                    :tr-alkuetaisyys 1500
                    :tr-alkuosa 1
                    :tr-kaista 1
                    :tr-loppuetaisyys 0
                    :tr-loppuosa 3
                    :tr-numero 20}
                   {:id 3
                    :nimi "2 A"
                    :tr-ajorata 2
                    :tr-alkuetaisyys 1
                    :tr-alkuosa 1
                    :tr-kaista 21
                    :tr-loppuetaisyys 1000
                    :tr-loppuosa 1
                    :tr-numero 20}
                   {:id 4
                    :nimi "2 B"
                    :tr-ajorata 2
                    :tr-alkuetaisyys 1500
                    :tr-alkuosa 1
                    :tr-kaista 21
                    :tr-loppuetaisyys 0
                    :tr-loppuosa 3
                    :tr-numero 20}]]
    (is (= {:ilmoitustiedot {:osoitteet [{:kohdeosa-id 1, :nimi "1 A",
                                          :toimenpide-paallystetyyppi 1, :tr-ajorata 1,
                                          :tr-alkuetaisyys 1, :tr-alkuosa 1, :tr-kaista 1,
                                          :tr-loppuetaisyys 1000, :tr-loppuosa 1,
                                          :tr-numero 20}
                                         {:kohdeosa-id 2, :nimi "1 B",
                                          :toimenpide-paallystetyyppi 1, :tr-ajorata 1,
                                          :tr-alkuetaisyys 1500, :tr-alkuosa 1,
                                          :tr-kaista 1, :tr-loppuetaisyys 0,
                                          :tr-loppuosa 3, :tr-numero 20}
                                         {:kohdeosa-id 3, :nimi "2 A",
                                          :toimenpide-paallystetyyppi 1, :tr-ajorata 2,
                                          :tr-alkuetaisyys 1, :tr-alkuosa 1,
                                          :tr-kaista 21, :tr-loppuetaisyys 1000,
                                          :tr-loppuosa 1, :tr-numero 20}
                                         {:kohdeosa-id 4, :nimi "2 B",
                                          :toimenpide-paallystetyyppi 1, :tr-ajorata 2,
                                          :tr-alkuetaisyys 1500, :tr-alkuosa 1,
                                          :tr-kaista 21, :tr-loppuetaisyys 0,
                                          :tr-loppuosa 3,
                                          :tr-numero 20}]}}
           (paallystys/lisaa-paallystysilmoitukseen-kohdeosien-idt paallystysilmoitus kohdeosat))
        "Kohdeosille on lisätty id:t oikein, kun ajorata ja kaista ovat paikoillaan"))

  (let [paallystysilmoitus {:ilmoitustiedot {:osoitteet [{:kohdeosa-id 1
                                                          :nimi "1"
                                                          :toimenpide-paallystetyyppi 1
                                                          :tr-alkuetaisyys 1
                                                          :tr-alkuosa 1
                                                          :tr-loppuetaisyys 1000
                                                          :tr-loppuosa 1
                                                          :tr-numero 20}
                                                         {:kohdeosa-id 3
                                                          :nimi "2"
                                                          :toimenpide-paallystetyyppi 1
                                                          :tr-alkuetaisyys 1
                                                          :tr-alkuosa 1
                                                          :tr-loppuetaisyys 1000
                                                          :tr-loppuosa 1
                                                          :tr-numero 20}]}}
        kohdeosat [{:id 1
                    :nimi "1"
                    :tr-alkuetaisyys 1
                    :tr-alkuosa 1
                    :tr-loppuetaisyys 1000
                    :tr-loppuosa 1
                    :tr-numero 20}
                   {:id 2
                    :nimi "2"
                    :tr-alkuetaisyys 1500
                    :tr-alkuosa 1
                    :tr-loppuetaisyys 0
                    :tr-loppuosa 3
                    :tr-numero 20}]]
    (is (= {:ilmoitustiedot {:osoitteet [{:kohdeosa-id 1, :nimi "1",
                                          :toimenpide-paallystetyyppi 1,
                                          :tr-alkuetaisyys 1, :tr-alkuosa 1,
                                          :tr-loppuetaisyys 1000, :tr-loppuosa 1,
                                          :tr-numero 20}
                                         {:kohdeosa-id 1, :nimi "2",
                                          :toimenpide-paallystetyyppi 1,
                                          :tr-alkuetaisyys 1, :tr-alkuosa 1,
                                          :tr-loppuetaisyys 1000, :tr-loppuosa 1,
                                          :tr-numero 20}]}}
           (paallystys/lisaa-paallystysilmoitukseen-kohdeosien-idt paallystysilmoitus kohdeosat))
        "Kohdeosille on lisätty id:t oikein, kun ajorataa ja kaistaa ei ole")))
