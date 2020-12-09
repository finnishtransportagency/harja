(ns harja.palvelin.palvelut.yllapitokohteet.paallystys-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [namespacefy.core :refer [namespacefy]]
            [taoensso.timbre :as log]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]
            [cheshire.core :as cheshire]
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
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet.pot2 :as pot2]
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
                                      [:http-palvelin :db :fim :sonja-sahkoposti])
                        :pot2 (component/using
                                (pot2/->POT2)
                                [:http-palvelin :db :fim :sonja-sahkoposti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each
              urakkatieto-fixture
              jarjestelma-fixture)

(def pot-testidata
  {:pot-versio 1
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

(def pot2-testidata (-> pot-testidata
                        (assoc :pot-versio 2)
                        ;; (assoc-in [:perustiedot :aloituspvm] (pvm/luo-pvm 2021 9 1))
                        ;; (assoc-in [:perustiedot :valmispvm-kohde] (pvm/luo-pvm 2021 9 2))
                        ;; (assoc-in [:perustiedot :valmispvm-paallystys] (pvm/luo-pvm 2021 9 2))
                        ;; (assoc-in [:perustiedot :takuupvm] (pvm/luo-pvm 2021 9 3))
                        ))

(deftest skeemavalidointi-toimii
  (let [paallystyskohde-id (hae-utajarven-yllapitokohde-jolla-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id (hae-utajarven-paallystysurakan-id)
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

    (let [urakka-id (hae-utajarven-paallystysurakan-id)
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
          paallystysilmoitus (-> (assoc pot-testidata :paallystyskohde-id paallystyskohde-id)
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

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus
                      +kayttaja-jvh+ {:urakka-id urakka-id
                                      :sopimus-id sopimus-id
                                      :vuosi 2019
                                      :paallystysilmoitus paallystysilmoitus}))))

(deftest testidata-on-validia
  ;; On kiva jos testaamme näkymää ja meidän testidata menee validoinnista läpi
  (let [ilmoitustiedot (q "SELECT pi.ilmoitustiedot
                           FROM paallystysilmoitus pi
                           JOIN yllapitokohde yk ON yk.id = pi.paallystyskohde
                           WHERE yk.vuodet[1] >= 2019
                             AND pi.versio = 1")]
    (doseq [[ilmoitusosa] ilmoitustiedot]
      (is (skeema/validoi paallystysilmoitus-domain/+paallystysilmoitus+
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
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        paallystysilmoitukset (kutsu-palvelua (:http-palvelin jarjestelma)
                                              :urakan-paallystysilmoitukset +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :vuosi paallystysilmoitus-domain/pot2-vuodesta-eteenpain})
        tarkea-kohde (first (filter #(= (:nimi %) "Tärkeä kohde mt20") paallystysilmoitukset))]
    (is (= (count paallystysilmoitukset) 2) "Päällystysilmoituksia löytyi vuodelle 2021")
    (is (= (:pot-versio tarkea-kohde) 2))

  (println "petar prosao je test verzije")
    (is (= :aloitettu (:tila tarkea-kohde)) "Tila")
    (is (= false (:lahetys-onnistunut tarkea-kohde)) "Lähetys")
    (is (= "L42" (:kohdenumero tarkea-kohde)) "Kohdenumero")
    (is (nil? (:paatos-tekninen-osa tarkea-kohde)) "Päätös")
    (is (nil? (:ilmoitustiedot tarkea-kohde)))
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
    (is (= (:pot-versio paallystysilmoitus-kannassa) 1))
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

(deftest hae-yllapitokohteen-olemassa-oleva-pot2-paallystysilmoitus ; petar todo
  (let [urakka-id @muhoksen-paallystysurakan-id
        sopimus-id @muhoksen-paallystysurakan-paasopimuksen-id
        paallystyskohde-id (hae-yllapitokohde-tarkea-kohde-pot2)
        paallystysilmoitus-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                                    :urakan-paallystysilmoitus-paallystyskohteella
                                                    +kayttaja-jvh+ {:urakka-id urakka-id
                                                                    :sopimus-id sopimus-id
                                                                    :paallystyskohde-id paallystyskohde-id})
        - (println "petar testiram ovo " (pr-str paallystysilmoitus-kannassa))
        kohdeosat (get-in paallystysilmoitus-kannassa [:ilmoitustiedot :osoitteet])]
    (is (not (nil? paallystysilmoitus-kannassa)))
    (is (= (:pot-versio paallystysilmoitus-kannassa) 2))
    (is (= 234234 (count kohdeosat)))))   ; petar ehkä myös varmista että data tuli pot2_ tauluista

(defn- hae-yllapitokohdeosadata [nimi tr-numero]
  (let [[tr_alkuosa paallystetyyppi raekoko tyomenetelma massamaara toimenpide id]
        (first (q (str "SELECT tr_alkuosa, paallystetyyppi, raekoko, tyomenetelma, massamaara, toimenpide, id
                        FROM yllapitokohdeosa WHERE nimi = '" nimi "' AND tr_numero = " tr-numero)))]
    {:tr_alkuosa      tr_alkuosa
     :paallystetyyppi paallystetyyppi
     :raekoko         raekoko
     :tyomenetelma    tyomenetelma
     :massamaara      massamaara
     :toimenpide      toimenpide
     :id              id}))

(defn- tallenna-testipaallystysilmoitus
  [paallystysilmoitus, vuosi]
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                             :sopimus-id         sopimus-id
                                                                             :vuosi              vuosi
                                                                             :paallystysilmoitus paallystysilmoitus})
        yllapitokohdeosadata (hae-yllapitokohdeosadata "Tie 22" 22)]
    [urakka-id sopimus-id vastaus yllapitokohdeosadata]))

(deftest tallenna-uusi-paallystysilmoitus-kantaan
  (let [;; Ei saa olla POT ilmoitusta
        paallystyskohde-id (hae-yllapitokohde-kirkkotie)]
    (is (not (nil? paallystyskohde-id)))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi ilmoitus")
    (let [paallystysilmoitus (-> pot-testidata
                                 (assoc :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))
          maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))
          [urakka-id sopimus-id vastaus yllapitokohdeosadata] (tallenna-testipaallystysilmoitus paallystysilmoitus 2020)]

      ;; Vastauksena saadaan annetun vuoden ylläpitokohteet ja päällystysilmoitukset. Poistetun kohteen ei pitäisi tulla.

      (is (= (count (:yllapitokohteet vastaus)) 4))
      (is (= (count (:paallystysilmoitukset vastaus)) 4))

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
        (println "petar skoro na kraju testkejsa")
        (is (= yllapitokohdeosadata
               {:tr_alkuosa      3
                :paallystetyyppi 1
                :raekoko         1
                :tyomenetelma    12
                :massamaara      2.00M
                :toimenpide      "Wut"
                :id              48}))
        (poista-paallystysilmoitus-paallystyskohtella paallystyskohde-id)))))

(deftest paivittaa-paallystysilmoitus-muokkaa-yllapitokohdeosaa
  (let [paallystyskohde-id (hae-yllapitokohde-kirkkotie)
        _ (is (not (nil? paallystyskohde-id)))
        alkuperainen-paallystysilmoitus (-> pot-testidata
                                            (assoc :paallystyskohde-id paallystyskohde-id)
                                            (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))]
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " alkuperäinen ilmoitus")
    (let [[_ _ _ yllapitokohdeosadata] (tallenna-testipaallystysilmoitus alkuperainen-paallystysilmoitus 2020)
          kohdeosa-id (:id yllapitokohdeosadata)]
      (is (= yllapitokohdeosadata
             {:tr_alkuosa      3
              :paallystetyyppi 1
              :raekoko         1
              :tyomenetelma    12
              :massamaara      2.00M
              :toimenpide      "Wut"
              :id              48}))
      (let [uusi-paallystysilmoitus (-> alkuperainen-paallystysilmoitus
                                        (assoc-in [:ilmoitustiedot :osoitteet 0 :tr-alkuosa] 3) ;; petar täytyy keksia minimi validi muuttos
                                        (assoc-in [:ilmoitustiedot :osoitteet 0 :toimenpide] "Freude")
                                        (assoc-in [:ilmoitustiedot :osoitteet 0 :kohdeosa-id] kohdeosa-id))
            - (println "petar novi ilmoitus " (pr-str uusi-paallystysilmoitus))
            [_ _ vastaus paivitetty-yllapitokohdeosadata] (tallenna-testipaallystysilmoitus uusi-paallystysilmoitus 2020)]
        (println "petar izasao je iz apdejta")
        (is (nil? (:virhe vastaus)))
        (is (= paivitetty-yllapitokohdeosadata
               {:tr_alkuosa      3
                :paallystetyyppi 1
                :raekoko         1
                :tyomenetelma    12
                :massamaara      2.00M
                :toimenpide      "Freude"
                :id              48}))))))

(deftest tallenna-uusi-pot2-paallystysilmoitus-kantaan      ; petar todo
  (let [;; Ei saa olla POT ilmoitusta
        paallystyskohde-id (ffirst (q "SELECT id FROM yllapitokohde WHERE nimi = 'Kirkkotie'"))]
    (is (not (nil? paallystyskohde-id)))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi pot2 ilmoitus")
    (let [urakka-id (hae-utajarven-paallystysurakan-id)
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
          paallystysilmoitus (-> pot2-testidata
                                 (assoc :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))
          maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                               :sopimus-id sopimus-id
                                                                               :vuosi paallystysilmoitus-domain/pot2-vuodesta-eteenpain
                                                                               :paallystysilmoitus paallystysilmoitus})]

      ;; Vastauksena saadaan annetun vuoden ylläpitokohteet ja päällystysilmoitukset. Poistetun kohteen ei pitäisi tulla.
      (is (= (count (:yllapitokohteet vastaus)) 2))
      (is (= (count (:paallystysilmoitukset vastaus)) 2))

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
        (is (= (:kokonaishinta-ilman-maaramuutoksia paallystysilmoitus-kannassa) 4753.95M))
        (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";"))))))


(deftest ei-saa-paivittaa-jos-on-vaara-versio
  (let [paallystyskohde-vanha-pot-id (ffirst (q "SELECT id FROM yllapitokohde WHERE nimi = 'Ouluntie'"))]
    (is (not (nil? paallystyskohde-vanha-pot-id)))
    (let [urakka-id (hae-utajarven-paallystysurakan-id)
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
          paallystysilmoitus-pot2 (-> pot-testidata
                                      (assoc :pot-versio 2)
                                      (assoc :paallystyskohde-id paallystyskohde-vanha-pot-id)
                                      (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))
          _ (println "petar urakkaId=" urakka-id)]
      (is (thrown-with-msg? IllegalArgumentException #"Väärä POT versio 2. Ota yhteyttä Harjan tukeen."
                            (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          urakka-id
                                                                                         :sopimus-id         sopimus-id
                                                                                         :vuosi              paallystysilmoitus-domain/pot2-vuodesta-eteenpain
                                                                                         :paallystysilmoitus paallystysilmoitus-pot2}))))))

(deftest ei-saa-paivittaa-jos-ei-ole-versiota               ; petar tämä on valmis testi, implementaatio puuttuu
    (let [paallystysilmoitus-pot2 (-> pot-testidata
                                      (dissoc :pot-versio)
                                      (assoc :paallystyskohde-id 123))]
      (is (thrown-with-msg? IllegalArgumentException #"Pyynnöstä puuttuu versio. Ota yhteyttä Harjan tukeen."
                            (kutsu-palvelua (:http-palvelin jarjestelma)
                                            :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id          1
                                                                                         :sopimus-id         1
                                                                                         :vuosi              2020
                                                                                         :paallystysilmoitus paallystysilmoitus-pot2})))))

(deftest paivittaa-paallystysilmoitus-pot2-ei-saa-paivittaa-kaikki-ylapitokohdeosa-kentat-FIXME ; petar
  (let [;; Ei saa olla POT ilmoitusta
        paallystyskohde-id (ffirst (q "SELECT id FROM yllapitokohde WHERE nimi = 'Ouluntie'"))]
    (is (not (nil? paallystyskohde-id)))
    (log/debug "Tallennetaan päällystyskohteelle " paallystyskohde-id " uusi ilmoitus")
    (let [urakka-id (hae-utajarven-paallystysurakan-id)
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
          ylapitokohdeosa-id 123
          paallystysilmoitus (-> pot-testidata
                                 (assoc :pot-versio 2)
                                 (assoc :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :valmis-kasiteltavaksi] true)
                                 (assoc-in [:ilmoitustiedot :osoitteet 2]
                                           {;; tämä täytyy päivittää
                                            :id ylapitokohdeosa-id
                                            :poistettu false
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
                                            :lisaaineet "asd"}


                                           ))
          maara-ennen-lisaysta (ffirst (q (str "SELECT count(*) FROM paallystysilmoitus;")))
          _ (println "petar urakkaId=" urakka-id (pr-str paallystysilmoitus))
          vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :tallenna-paallystysilmoitus +kayttaja-jvh+ {:urakka-id urakka-id
                                                                               :sopimus-id sopimus-id
                                                                               :vuosi paallystysilmoitus-domain/pot2-vuodesta-eteenpain
                                                                               :paallystysilmoitus paallystysilmoitus})]

      ;; Vastauksena saadaan annetun vuoden ylläpitokohteet ja päällystysilmoitukset. Poistetun kohteen ei pitäisi tulla.
      (is (= (count (:yllapitokohteet vastaus)) 123123))
      (is (= (count (:paallystysilmoitukset vastaus)) 4))

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
        (u (str "DELETE FROM paallystysilmoitus WHERE paallystyskohde = " paallystyskohde-id ";"))))))

(deftest uuden-paallystysilmoituksen-tallennus-eri-urakkaan-ei-onnistu
  (let [paallystyskohde-id (hae-utajarven-yllapitokohde-jolla-ei-ole-paallystysilmoitusta)]
    (is (not (nil? paallystyskohde-id)))
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
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id (hae-utajarven-paallystysurakan-id)
          sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
          paallystysilmoitus (-> pot-testidata
                                 (assoc :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :tekninen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:perustiedot :tekninen-osa :perustelu] "Hyvä ilmoitus!"))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-paallystysilmoitus +kayttaja-jvh+
                      {:urakka-id urakka-id
                       :sopimus-id sopimus-id
                       :vuosi 2019
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
    (is (not (nil? paallystyskohde-id)))

    (let [urakka-id (hae-utajarven-paallystysurakan-id)
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
    (is (not (nil? paallystyskohde-id)))

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
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
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

        (is (= (count vastaus) 5) "Kaikki kohteet palautuu")
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
  (let [kohde-id (hae-yllapitokohde-oulun-ohitusramppi)
        payload {::urakka-domain/id (hae-muhoksen-paallystysurakan-id)
                 ::paallystysilmoitus-domain/paallystyskohde-id kohde-id
                 ::paallystysilmoitus-domain/tila :valmis}
        tila-ennen-kutsua (keyword (second (first (q (str "SELECT id, tila FROM paallystysilmoitus WHERE paallystyskohde = " kohde-id)))))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :aseta-paallystysilmoituksen-tila +kayttaja-jvh+ payload)]
    (is (= :lukittu tila-ennen-kutsua))
    (is (= :valmis (:tila vastaus)))))

(deftest avaa-lukitun-potin-lukitus-ei-sallittu-koska-tero-ei-tassa-urakanvalvojana
  (let [kohde-id (hae-yllapitokohde-oulun-ohitusramppi)
        payload {::urakka-domain/id (hae-muhoksen-paallystysurakan-id)
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
        paallystysilmoitus (-> pot-testidata
                               (assoc :paallystyskohde-id paallystyskohde-id)
                               (assoc-in [:perustiedot :valmis-kasiteltavaksi] true))
        sahkoposti-valitetty (atom false)
        sahkopostin-vastaanottaja (atom nil)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))]
    (sonja/kuuntele! (:sonja jarjestelma) "harja-to-email" (fn [lahteva-viesti]
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
                                      :vuosi 2018
                                      :paallystysilmoitus paallystysilmoitus}))

    (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 10000)
    (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")
    (is @sahkopostin-vastaanottaja "ELY_Urakanvalvoja@example.com")
    (reset! sahkoposti-valitetty false)
    (reset! sahkopostin-vastaanottaja nil)
    (let [;;Hyväksytään ilmoitus ja lähetetään tästä urakan valvojalle sähköposti
          paallystysilmoitus (-> (assoc pot-testidata
                                   :paallystyskohde-id paallystyskohde-id)
                                 (assoc-in [:perustiedot :tekninen-osa :paatos] :hyvaksytty)
                                 (assoc-in [:perustiedot :tekninen-osa :perustelu] "Hyvä ilmoitus!"))]
      (with-fake-http
        [+testi-fim+ fim-vastaus]
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-paallystysilmoitus
                        +kayttaja-jvh+ {:urakka-id urakka-id
                                        :sopimus-id sopimus-id
                                        :vuosi 2018
                                        :paallystysilmoitus paallystysilmoitus}))
      (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 10000)
      (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")
      (is @sahkopostin-vastaanottaja "vastuuhenkilo@example.com"))))

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