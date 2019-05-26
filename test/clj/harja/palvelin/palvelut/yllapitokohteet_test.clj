(ns harja.palvelin.palvelut.yllapitokohteet-test
  (:require [clojure.test :refer :all]
            [clojure.core.match :refer [match]]
            [clojure.core.async :refer [<!! timeout]]
            [clojure.java.io :as io]
            [clojure.string :as clj-str]

            [clj-time.coerce :as c]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit
             [tietokanta :as tietokanta]
             [fim-test :refer [+testi-fim+]]
             [sonja :as sonja]
             [fim :as fim]]
            [harja.palvelin.palvelut
             [yllapitokohteet :refer :all]]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]

            [harja.testi :refer :all]

            [harja.jms-test :refer [feikki-sonja]]
            [harja.pvm :as pvm]

            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti.sanomat :as sanomat]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.yha
             [tyokalut :as yha-tyokalut]
             [yha-komponentti :as yha]]

            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.paneeliapurit :as paneeli]

            [harja.kyselyt.konversio :as konv])
  (:use org.httpkit.fake))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :sonja (feikki-sonja)
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :http-palvelin (testi-http-palvelin)
                        :yllapitokohteet (component/using
                                           (->Yllapitokohteet {})
                                           [:http-palvelin :db :fim :sonja-sahkoposti :yha-integraatio])
                        :yha-integraatio (component/using
                                           (yha/->Yha {:url yha-tyokalut/+yha-url+})
                                           [:db :http-palvelin :integraatioloki])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(def yllapitokohde-testidata {:kohdenumero 999
                              :nimi "Testiramppi4564ddf"
                              :yllapitokohdetyotyyppi :paallystys
                              :sopimuksen_mukaiset_tyot 400
                              :tr-numero 20
                              :tr-ajorata 1
                              :tr-kaista 11
                              :tr-alkuosa 1
                              :tr-alkuetaisyys 1
                              :tr-loppuosa 2
                              :tr-loppuetaisyys 2
                              :bitumi_indeksi 123
                              :kaasuindeks 123})

(def yllapitokohdeosa-testidata {:nimi "Testiosa123456"
                                 :tr-numero 20
                                 :tr-alkuosa 1
                                 :tr-alkuetaisyys 1
                                 :tr-loppuosa 2
                                 :tr-loppuetaisyys 2
                                 :kvl 4
                                 :nykyinen_paallyste 2
                                 :toimenpide "Ei tehdä mitään"})

(defn kohde-nimella [kohteet nimi]
  (first (filter #(= (:nimi %) nimi) kohteet)))

(defn yllapitokohde-id-jolla-on-paallystysilmoitus []
  (ffirst (q (str "SELECT yllapitokohde.id as paallystyskohde_id
                   FROM yllapitokohde
                   JOIN paallystysilmoitus ON yllapitokohde.id = paallystysilmoitus.paallystyskohde
                   WHERE urakka = " (hae-muhoksen-paallystysurakan-id) " AND sopimus = " (hae-muhoksen-paallystysurakan-paasopimuksen-id) ";"))))


(deftest paallystyskohteet-haettu-oikein
  (let [kohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                :urakan-yllapitokohteet +kayttaja-jvh+
                                {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                 :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " (hae-muhoksen-paallystysurakan-id) ")
                                      AND poistettu IS NOT TRUE;")))
        leppajarven-ramppi (kohde-nimella kohteet "Leppäjärven ramppi")]
    (is (= (count kohteet) kohteiden-lkm) "Päällystyskohteiden määrä")
    (is (== (:maaramuutokset leppajarven-ramppi) 205)
        "Leppäjärven rampin määrämuutos laskettu oikein")))

(deftest paallystyskohteiden-tila-paatellaan-oikein-kun-kesa-menossa
  (with-redefs [pvm/nyt #(pvm/luo-pvm 2017 4 25)] ;; 25.5.2017
    (let [kohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :urakan-yllapitokohteet +kayttaja-jvh+
                                  {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                   :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)})
          nakkilan-ramppi (kohde-nimella kohteet "Nakkilan ramppi")
          leppajarven-ramppi (kohde-nimella kohteet "Leppäjärven ramppi")
          kuusamontien-testi (kohde-nimella kohteet "Kuusamontien testi")
          oulaisten-ohitusramppi (kohde-nimella kohteet "Oulaisten ohitusramppi")
          oulun-ohitusramppi (kohde-nimella kohteet "Oulun ohitusramppi")]

      (is (and nakkilan-ramppi leppajarven-ramppi kuusamontien-testi oulaisten-ohitusramppi oulun-ohitusramppi)
          "Kaikki kohteet löytyvät vastauksesta")

      ;; Palvelu palauttaa kohteiden tilat oikein
      (is (= (:tila nakkilan-ramppi) :ei-aloitettu))
      (is (= (:tila leppajarven-ramppi) :kohde-valmis))
      (is (= (:tila kuusamontien-testi) :ei-aloitettu))
      (is (= (:tila oulaisten-ohitusramppi) :ei-aloitettu))
      (is (= (:tila oulun-ohitusramppi) :paallystys-aloitettu))

      ;; Tila kartalla -päättely menee myös oikein
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila nakkilan-ramppi)) :ei-aloitettu))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila leppajarven-ramppi)) :valmis))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila kuusamontien-testi)) :ei-aloitettu))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila oulaisten-ohitusramppi)) :ei-aloitettu))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila oulun-ohitusramppi)) :kesken)))))

(deftest yllapitokohteen-tila-paatellaan-oikein
  (with-redefs [pvm/nyt #(pvm/luo-pvm 2017 4 25)] ;; 25.5.2017
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila {})
           :ei-aloitettu))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2018 1 1))})
           :ei-aloitettu))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 1))})
           :kohde-aloitettu))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 1))})
           :kohde-aloitettu))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 1))
              :paallystys-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 2))})
           :paallystys-aloitettu))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 1))
              :paallystys-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 2))
              :paallystys-loppupvm (c/to-timestamp (pvm/luo-pvm 2016 1 3))})
           :paallystys-valmis))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2018 1 1))
              :paallystys-alkupvm (c/to-timestamp (pvm/luo-pvm 2018 1 2))
              :paallystys-loppupvm (c/to-timestamp (pvm/luo-pvm 2018 1 3))})
           :ei-aloitettu))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 1))
              :paallystys-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 2))
              :paallystys-loppupvm (c/to-timestamp (pvm/luo-pvm 2016 1 3))
              :kohde-valmispvm (c/to-timestamp (pvm/luo-pvm 2016 1 3))})
           :kohde-valmis))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 1))
              :paallystys-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 2))
              :paallystys-loppupvm (c/to-timestamp (pvm/luo-pvm 2016 1 3))
              :tiemerkinta-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 4))
              :tiemerkinta-loppupvm (c/to-timestamp (pvm/luo-pvm 2016 1 5))
              :kohde-valmispvm (c/to-timestamp (pvm/luo-pvm 2016 1 6))})
           :kohde-valmis))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 1))
              :paallystys-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 2))
              :paallystys-loppupvm (c/to-timestamp (pvm/luo-pvm 2016 1 3))
              :tiemerkinta-alkupvm (c/to-timestamp (pvm/luo-pvm 2016 1 4))
              :tiemerkinta-loppupvm (c/to-timestamp (pvm/luo-pvm 2016 1 5))
              :kohde-valmispvm nil})
           :tiemerkinta-valmis))
    (is (= (yllapitokohteet-domain/yllapitokohteen-tarkka-tila
             {:kohde-alkupvm (c/to-timestamp (pvm/luo-pvm 2018 1 1))
              :paallystys-alkupvm (c/to-timestamp (pvm/luo-pvm 2018 1 2))
              :paallystys-loppupvm (c/to-timestamp (pvm/luo-pvm 2018 1 3))
              :tiemerkinta-alkupvm (c/to-timestamp (pvm/luo-pvm 2018 1 4))
              :tiemerkinta-loppupvm (c/to-timestamp (pvm/luo-pvm 2018 1 5))
              :kohde-valmispvm nil})
           :ei-aloitettu))))

(deftest paallystyskohteiden-tila-paatellaan-oikein-kun-kesa-tulossa
  (with-redefs [pvm/nyt #(pvm/luo-pvm 2017 0 1)] ;; 1.1.2017
    (let [kohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :urakan-yllapitokohteet +kayttaja-jvh+
                                  {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                   :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)})
          nakkilan-ramppi (kohde-nimella kohteet "Nakkilan ramppi")
          leppajarven-ramppi (kohde-nimella kohteet "Leppäjärven ramppi")
          kuusamontien-testi (kohde-nimella kohteet "Kuusamontien testi")
          oulaisten-ohitusramppi (kohde-nimella kohteet "Oulaisten ohitusramppi")
          oulun-ohitusramppi (kohde-nimella kohteet "Oulun ohitusramppi")]

      (is (and nakkilan-ramppi leppajarven-ramppi kuusamontien-testi oulaisten-ohitusramppi oulun-ohitusramppi)
          "Kaikki kohteet löytyvät vastauksesta")

      ;; Palvelu palauttaa kohteiden tilat oikein
      (is (= (:tila nakkilan-ramppi) :ei-aloitettu))
      (is (= (:tila leppajarven-ramppi) :ei-aloitettu))
      (is (= (:tila kuusamontien-testi) :ei-aloitettu))
      (is (= (:tila oulaisten-ohitusramppi) :ei-aloitettu))
      (is (= (:tila oulun-ohitusramppi) :ei-aloitettu))

      ;; Tila kartalla -päättely menee myös oikein
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila nakkilan-ramppi)) :ei-aloitettu))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila leppajarven-ramppi)) :ei-aloitettu))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila kuusamontien-testi)) :ei-aloitettu))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila oulaisten-ohitusramppi)) :ei-aloitettu))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila oulun-ohitusramppi)) :ei-aloitettu)))))

(deftest paallystyskohteiden-tila-paatellaan-oikein-kun-kesa-ohi
  (with-redefs [pvm/nyt #(pvm/luo-pvm 2017 9 1)] ;; 1.10.2017
    (let [kohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :urakan-yllapitokohteet +kayttaja-jvh+
                                  {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                   :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)})
          nakkilan-ramppi (kohde-nimella kohteet "Nakkilan ramppi")
          leppajarven-ramppi (kohde-nimella kohteet "Leppäjärven ramppi")
          kuusamontien-testi (kohde-nimella kohteet "Kuusamontien testi")
          oulaisten-ohitusramppi (kohde-nimella kohteet "Oulaisten ohitusramppi")
          oulun-ohitusramppi (kohde-nimella kohteet "Oulun ohitusramppi")]

      (is (and nakkilan-ramppi leppajarven-ramppi kuusamontien-testi oulaisten-ohitusramppi oulun-ohitusramppi)
          "Kaikki kohteet löytyvät vastauksesta")

      ;; Palvelu palauttaa kohteiden tilat oikein
      (is (= (:tila nakkilan-ramppi) :ei-aloitettu))
      (is (= (:tila leppajarven-ramppi) :kohde-valmis))
      (is (= (:tila kuusamontien-testi) :kohde-aloitettu))
      (is (= (:tila oulaisten-ohitusramppi) :kohde-aloitettu))
      (is (= (:tila oulun-ohitusramppi) :paallystys-aloitettu))

      ;; Tila kartalla -päättely menee myös oikein
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila nakkilan-ramppi)) :ei-aloitettu))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila leppajarven-ramppi)) :valmis))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila kuusamontien-testi)) :kesken))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila oulaisten-ohitusramppi)) :kesken))
      (is (= (yllapitokohteet-domain/yllapitokohteen-tila-kartalla (:tila oulun-ohitusramppi)) :kesken)))))

(deftest infopaneelin-skeemojen-luonti-yllapitokohteet-palvelulle
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :urakan-yllapitokohteet +kayttaja-jvh+
                                {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                 :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                                 :vuosi 2017})]
    (is (paneeli/skeeman-luonti-onnistuu-kaikille?
          :paallystys
          (into [] yllapitokohteet-domain/yllapitokohde-kartalle-xf vastaus)))))

(deftest paallystysurakan-aikatauluhaku-toimii
  (let [urakan-yllapitokohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :urakan-yllapitokohteet +kayttaja-jvh+
                                               {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                                :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                                                :vuosi 2017})
        aikataulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                  {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                   :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                                   :vuosi 2017})
        leppajarven-ramppi (kohde-nimella aikataulu "Leppäjärven ramppi")
        oulun-ramppi (kohde-nimella aikataulu "Oulun ohitusramppi")
        muut-kohteet (filter #(not= (:nimi %) "Leppäjärven ramppi") aikataulu)]

    (is leppajarven-ramppi)
    (is oulun-ramppi)

    (is (= (count urakan-yllapitokohteet) (count aikataulu))
        "Jokaiselle kohteelle saatiin haettua aikataulu")
    (is (false? (:tiemerkintaurakan-voi-vaihtaa? leppajarven-ramppi))
        "Leppäjärven rampilla on kirjauksia, ei saa vaihtaa suorittavaa tiemerkintäurakkaa")
    (is (every? true? (map :tiemerkintaurakan-voi-vaihtaa? muut-kohteet))
        "Muiden kohteiden tiemerkinnän suorittaja voidaan vaihtaa")
    (is (= (count (:tarkka-aikataulu oulun-ramppi)) 2)
        "Oulun rampille löytyy myös yksityiskohtaisempi aikataulu")
    (is (> (:pituus oulun-ramppi) 3000))
    (is (> (:pituus leppajarven-ramppi) 3000))))

(deftest tiemerkintaurakan-aikatauluhaku-toimii
  (let [aikataulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                  {:urakka-id (hae-oulun-tiemerkintaurakan-id)
                                   :sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
                                   :vuosi 2017})]
    (is (= (count aikataulu) 3) "Löytyi kaikki tiemerkintäurakalle osoitetut ylläpitokohteet")
    (is (not-any? #(contains? % :suorittava-tiemerkintaurakka) aikataulu)
        "Tiemerkinnän aikataulu ei sisällä päällystysurakkaan liittyvää tietoa")))

(deftest paallystyskohteet-haettu-oikein-vuodelle-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :urakan-yllapitokohteet +kayttaja-jvh+
                                {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                 :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                                 :vuosi 2017})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " (hae-muhoksen-paallystysurakan-id) ")
                                      AND vuodet @> ARRAY[2017]::int[]
                                      AND poistettu IS NOT TRUE")))
        ei-yha-kohde (kohde-nimella vastaus "Ei YHA-kohde")
        poistettava-yha-kohde (kohde-nimella vastaus "Kuusamontien testi")
        muut-kohteet (filter #(and (not= (:nimi %) "Ei YHA-kohde")
                                   (not= (:nimi %) "Kuusamontien testi"))
                             vastaus)]
    (is (> (count vastaus) 0) "Päällystyskohteita löytyi")
    (is (= (count vastaus) kohteiden-lkm) "Löytyi oikea määrä kohteita")
    (is (true? (:yllapitokohteen-voi-poistaa? ei-yha-kohde))
        "Ei YHA -kohteen saa poistaa (ei ole mitään kirjauksia)")
    (is (true? (:yllapitokohteen-voi-poistaa? poistettava-yha-kohde))
        "Kuusamontie-kohteen saa poistaa (ei ole mitään kirjauksia)")
    (is (every? false? (map :yllapitokohteen-voi-poistaa? muut-kohteet))
        "Muita kohteita ei saa poistaa (sisältävät kirjauksia)")
    (is (> (:pituus ei-yha-kohde) 3000))))

(deftest paallystyskohteet-haettu-oikein-vuodelle-2016
  (let [res (kutsu-palvelua (:http-palvelin jarjestelma)
                            :urakan-yllapitokohteet +kayttaja-jvh+
                            {:urakka-id (hae-muhoksen-paallystysurakan-id)
                             :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                             :vuosi 2016})]
    (is (= (count res) 0) "Ei päällystyskohteita vuodelle 2016")))

(deftest tallenna-paallystyskohde-kantaan
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        urakan-geometria-ennen-muutosta (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERE id = " urakka-id ";"))
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :sopimus-id sopimus-id
                                                              :vuosi 2018
                                                              :kohteet [yllapitokohde-testidata]})
    (let [maara-lisayksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
          kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :urakan-yllapitokohteet
                                           +kayttaja-jvh+ {:urakka-id urakka-id
                                                           :sopimus-id sopimus-id})
          urakan-geometria-lisayksen-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]
      (is (not (nil? kohteet-kannassa)))
      (is (not= urakan-geometria-ennen-muutosta urakan-geometria-lisayksen-jalkeen "Urakan geometria päivittyi"))
      (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))

      ;; Edelleen jos ylläpitokohde poistetaan, niin myös geometria päivittyy
      (let [lisatty-kohde (kohde-nimella kohteet-kannassa "Testiramppi4564ddf")
            _ (is lisatty-kohde "Lisätty kohde löytyi vastauksesta")
            lisatty-kohde (assoc lisatty-kohde :poistettu true)
            kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                       :sopimus-id sopimus-id
                                                                                       :kohteet [lisatty-kohde]})
            poistettu-kohde (kohde-nimella kohteet-kannassa "Testiramppi4564ddf")
            urakan-geometria-poiston-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]

        (is (nil? poistettu-kohde) "Poistettua kohdetta ei ole enää vastauksessa")
        (is (not= urakan-geometria-lisayksen-jalkeen urakan-geometria-poiston-jalkeen "Geometria päivittyi"))))))

(deftest paivita-paallystyskohde-kantaan
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        urakan-geometria-ennen-muutosta (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERE id = " urakka-id ";"))
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        vanhat-kohdeosat-kannassa (q-map
                                    (str "SELECT
                                      tr_numero,
                                      tr_alkuosa,
                                      tr_alkuetaisyys,
                                      tr_loppuosa,
                                      tr_loppuetaisyys
                                      FROM yllapitokohdeosa
                                         WHERE yllapitokohde = " leppajarven-ramppi-id "
                                         AND poistettu IS NOT TRUE;"))
        vanha-saman-tien-kohdeosa-kannassa (first (filter #(= (:tr_numero %) 20) vanhat-kohdeosat-kannassa))
        vanha-eri-tien-kohdeosa-kannassa (first (filter #(not= (:tr_numero %) 20) vanhat-kohdeosat-kannassa))]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :vuosi 2018
                                                              :sopimus-id sopimus-id
                                                              :kohteet [(assoc yllapitokohde-testidata
                                                                          :tr-numero 20
                                                                          :tr-alkuosa 1
                                                                          :tr-alkuetaisyys 0
                                                                          :tr-loppuosa 1
                                                                          :tr-loppuetaisyys 2
                                                                          :id leppajarven-ramppi-id)]})
    (let [maara-lisayksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM yllapitokohde
                                                  WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
          kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :urakan-yllapitokohteet
                                           +kayttaja-jvh+ {:urakka-id urakka-id
                                                           :sopimus-id sopimus-id})
          paivittyneet-kohdeosat-kannassa (q-map
                                            (str "SELECT
                                                  tr_numero,
                                                  tr_alkuosa,
                                                  tr_alkuetaisyys,
                                                  tr_loppuosa,
                                                  tr_loppuetaisyys
                                                  FROM yllapitokohdeosa
                                                     WHERE yllapitokohde = " leppajarven-ramppi-id "
                                                     AND poistettu IS NOT TRUE;"))
          paivittynyt-saman-tien-kohdeosa-kannassa (first (filter #(= (:tr_numero %) 20) paivittyneet-kohdeosat-kannassa))
          paivittynyt-eri-tien-kohdeosa-kannassa (first (filter #(not= (:tr_numero %) 20) paivittyneet-kohdeosat-kannassa))
          urakan-geometria-lisayksen-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]
      (is (not (nil? kohteet-kannassa)))
      (is (= (count (filter #(= (:nimi %) "Testiramppi4564ddf") kohteet-kannassa)) 1))
      (is (not= urakan-geometria-ennen-muutosta urakan-geometria-lisayksen-jalkeen "Urakan geometria päivittyi"))
      (is (= vanha-saman-tien-kohdeosa-kannassa
             {:tr_numero 20
              :tr_alkuosa 1
              :tr_alkuetaisyys 0
              :tr_loppuosa 3
              :tr_loppuetaisyys 0}))
      (is (= paivittynyt-saman-tien-kohdeosa-kannassa
             {:tr_numero 20
              :tr_alkuosa 1
              :tr_alkuetaisyys 0
              :tr_loppuosa 1
              :tr_loppuetaisyys 2})
          "Pääkohdettä kutistettiin, saman tien kohdeosa kutistuu pääkohteen sisälle")
      (is (some? vanha-eri-tien-kohdeosa-kannassa))
      (is (some? paivittynyt-eri-tien-kohdeosa-kannassa))
      (is (= vanha-eri-tien-kohdeosa-kannassa paivittynyt-eri-tien-kohdeosa-kannassa)
          "Eri tien kohdeosiin ei koskettu")
      (is (= maara-ennen-lisaysta maara-lisayksen-jalkeen)))))

(deftest tallenna-uusi-paallystyskohde-kantaan-vuodelle-2015
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                                          :sopimus-id sopimus-id
                                                                          :vuosi 2015
                                                                          :kohteet [yllapitokohde-testidata]})]

    (is (= (:status vastaus) :ok))
    (is (>= (count (:yllapitokohteet vastaus)) 1))

    (let [kohteet-kannassa (ffirst (q
                                     (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " (hae-muhoksen-paallystysurakan-id) ")
                                      AND vuodet @> ARRAY[2015]::int[]")))]
      (is (= kohteet-kannassa 1) "Kohde tallentui oikein"))))

(deftest tallenna-paallekain-menevat-yllapitokohteet
  (let [kohde-leppajarven-paalle {:kohdenumero 666
                                  :nimi "Erkkipetteri"
                                  :yhaid 666
                                  :yllapitokohdetyotyyppi :paallystys
                                  :tr-numero 20
                                  :tr-alkuosa 1
                                  :tr-alkuetaisyys 0
                                  :tr-loppuosa 3
                                  :tr-loppuetaisyys 0
                                  :tr-ajorata 1
                                  :tr-kaista 11}]

    (testing "Päällekkäin menevät kohteet samana vuonna"
      (let [urakka-id (hae-muhoksen-paallystysurakan-id)
            sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-yllapitokohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi 2017
                                     :kohteet [kohde-leppajarven-paalle]})]

        (is (= (:status vastaus) :ok)
            "Yritetään tallentaa uusi ylläpitokohde, joka menee Leppäjärven rampi päälle. Kohteet saa mennä päällekkäin.")))

    (testing "Kohde ei mene päällekäin Leppäjärven kanssa, koska se päivitetään samalla eri tielle"
      (let [urakka-id (hae-muhoksen-paallystysurakan-id)
            sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
            leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-yllapitokohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi 2017
                                     :kohteet [kohde-leppajarven-paalle
                                               ;; Päivitetään samalla Leppäjärven ramppi eri tielle
                                               {:id leppajarven-ramppi-id
                                                :kohdenumero 123
                                                :nimi "Leppäjärven ramppi (päivitetty)"
                                                :tr-numero 21
                                                :tr-alkuosa 1
                                                :tr-alkuetaisyys 0
                                                :tr-loppuosa 3
                                                :tr-loppuetaisyys 0
                                                :tr-ajorata 1
                                                :tr-kaista 1}]})]

        (is (not= (:status vastaus) :validointiongelma)
            "Yritetään tallentaa uusi ylläpitokohde, joka menee Leppäjärven rampin päälle.
             Samalla tallennetaan kuitenkin myös uusi Leppäjärven ramppi, jossa tieosoite siirtyy. Ei tule Herjaa.")))

    (testing "Päällekkäin menevät kohteet eri vuonna"
      (let [urakka-id (hae-muhoksen-paallystysurakan-id)
            sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-yllapitokohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi 2018
                                     :kohteet [kohde-leppajarven-paalle]})]

        (is (not= (:status vastaus) :validointiongelma)
            "Kohteet menevät päällekkäin, mutta eri vuonna --> ei herjaa")))

    (testing "Päällekkäin menevät osoitteet eri tiellä"
      (let [urakka-id (hae-muhoksen-paallystysurakan-id)
            sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-yllapitokohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi 2017
                                     :kohteet [(assoc
                                                 kohde-leppajarven-paalle
                                                 :tr-numero 8)]})]

        (is (not= (:status vastaus) :validointiongelma)
            "Osoitteet menevät päällekkäin, mutta eri tiellä --> ei herjaa")))

    (testing "Päällekkäin menevät osoitteet eri kaistalla"
      (let [urakka-id (hae-muhoksen-paallystysurakan-id)
            sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-yllapitokohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi 2017
                                     :kohteet [(assoc
                                                 kohde-leppajarven-paalle
                                                 :tr-kaista 11)]})]

        (is (not= (:status vastaus) :validointiongelma)
            "Osoitteet menevät päällekkäin, mutta eri kaistalla --> ei herjaa")))

    (testing "Päällekkäin menevät osoitteet eri ajoradalla"
      (let [urakka-id (hae-muhoksen-paallystysurakan-id)
            sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-yllapitokohteet +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi 2017
                                     :kohteet [(assoc
                                                 kohde-leppajarven-paalle
                                                 :tr-ajorata 2)]})]

        (is (not= (:status vastaus) :validointiongelma)
            "Osoitteet menevät päällekkäin, mutta eri ajoradalla --> ei herjaa")))))

(deftest poista-yha-paallystyskohde-onnistuneesti
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        testidata (map (fn [kohde]
                         (update kohde :yllapitokohdetyotyyppi keyword))
                       (q-map
                         (str "SELECT yk.kohdenumero, yk.nimi, yk.tr_numero AS \"tr-numero\", yk.tr_alkuosa AS \"tr-alkuosa\",
                                      yk.tr_alkuetaisyys AS \"tr-alkuetaisyys\", yk.tr_loppuosa AS \"tr-loppuosa\",
                                      yk.tr_loppuetaisyys AS \"tr-loppuetaisyys\", yk.yllapitokohdetyotyyppi, yk.yhaid,
                                      yk.id "
                              "FROM yllapitokohde yk "
                              "LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde=yk.id "
                              "WHERE yk.urakka=" urakka-id " AND
                                     yk.lahetys_onnistunut IS NOT TRUE AND
                                     yk.yhaid IS NOT NULL AND
                                     pi.paallystyskohde IS NULL;")))
        kohteet [(-> testidata first (assoc :poistettu true))]
        poista-kohde (with-fake-http [{:url (re-pattern (str "^" yha-tyokalut/+yha-url+ "toteumakohde/\\d+$"))
                                       :method :delete} {:status 200
                                                         :body yha-tyokalut/+onnistunut-kohteen-poisto-vastaus+}]
                                     (kutsu-palvelua (:http-palvelin jarjestelma)
                                                     :tallenna-yllapitokohteet
                                                     +kayttaja-jvh+
                                                     {:urakka-id urakka-id
                                                      :sopimus-id sopimus-id
                                                      :vuosi 2019
                                                      :kohteet kohteet}))
        kohde-poistettu? (ffirst (q (str "SELECT poistettu FROM yllapitokohde WHERE yhaid=" (:yhaid (first kohteet)))))]
    (is (= (:status poista-kohde) :ok))
    (is kohde-poistettu?)))

(deftest poista-yha-paallystyskohde-epsonnistuneesti
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        kohteen-avaimet "yk.kohdenumero, yk.nimi, yk.tr_numero AS \"tr-numero\", yk.tr_alkuosa AS \"tr-alkuosa\",
                         yk.tr_alkuetaisyys AS \"tr-alkuetaisyys\", yk.tr_loppuosa AS \"tr-loppuosa\",
                         yk.tr_loppuetaisyys AS \"tr-loppuetaisyys\", yk.yllapitokohdetyotyyppi, yk.yhaid,
                         yk.id, yk.poistettu"
        haku-xf (map (fn [kohde]
                       (update kohde :yllapitokohdetyotyyppi keyword)))
        testidata (into []
                        haku-xf
                       (q-map
                         (str "SELECT " kohteen-avaimet " "
                              "FROM yllapitokohde yk "
                              "LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde=yk.id "
                              "WHERE yk.urakka=" urakka-id " AND
                                     yk.lahetys_onnistunut IS NOT TRUE AND
                                     yk.poistettu IS NOT TRUE AND
                                     yk.yhaid IS NOT NULL AND
                                     pi.paallystyskohde IS NULL;")))
        kohteet (-> (into [] (take 2 testidata))
                    (assoc-in [0 :poistettu] true)
                    (update-in [1 :tr-loppuetaisyys] dec))
        poista-kohde (with-fake-http [{:url (re-pattern (str "^" yha-tyokalut/+yha-url+ "toteumakohde/\\d+$"))
                                       :method :delete} {:body (yha-tyokalut/+epaonnistunut-kohteen-poisto-vastaus+ (-> kohteet first :yhaid))}]
                                     (kutsu-palvelua (:http-palvelin jarjestelma)
                                                     :tallenna-yllapitokohteet
                                                     +kayttaja-jvh+
                                                     {:urakka-id urakka-id
                                                      :sopimus-id sopimus-id
                                                      :vuosi 2019
                                                      :kohteet kohteet}))
        kohde-poistettu? (ffirst (q (str "SELECT poistettu FROM yllapitokohde WHERE yhaid=" (:yhaid (first kohteet)))))
        kohteet-paivityksen-jalkeen (sort-by :yhaid
                                             (into []
                                                   haku-xf
                                                   (q-map (str "SELECT " kohteen-avaimet " "
                                                               "FROM yllapitokohde yk "
                                                               "WHERE yhaid IN(" (apply str (interpose ", "
                                                                                                       (mapv :yhaid kohteet)))
                                                               ");"))))]
    (is (= (:status poista-kohde) :yha-virhe))
    (is (not kohde-poistettu?))
    (is (= (sort-by :yhaid (take 2 testidata)) kohteet-paivityksen-jalkeen))))

(deftest poista-useampi-yha-paallystyskohde-epsonnistuneesti
  (let [urakka-id (hae-utajarven-paallystysurakan-id)
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        kohteen-avaimet "yk.kohdenumero, yk.nimi, yk.tr_numero AS \"tr-numero\", yk.tr_alkuosa AS \"tr-alkuosa\",
                         yk.tr_alkuetaisyys AS \"tr-alkuetaisyys\", yk.tr_loppuosa AS \"tr-loppuosa\",
                         yk.tr_loppuetaisyys AS \"tr-loppuetaisyys\", yk.yllapitokohdetyotyyppi, yk.yhaid,
                         yk.id, yk.poistettu"
        haku-xf (map (fn [kohde]
                       (update kohde :yllapitokohdetyotyyppi keyword)))
        testidata (into []
                        haku-xf
                        (q-map
                          (str "SELECT " kohteen-avaimet " "
                               "FROM yllapitokohde yk "
                               "LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde=yk.id "
                               "WHERE yk.urakka=" urakka-id " AND
                                     yk.lahetys_onnistunut IS NOT TRUE AND
                                     yk.poistettu IS NOT TRUE AND
                                     yk.yhaid IS NOT NULL AND
                                     pi.paallystyskohde IS NULL;")))
        kohteet (-> (into [] (take 3 (sort-by :yhaid testidata)))
                    (assoc-in [0 :poistettu] true)
                    (assoc-in [1 :poistettu] true)
                    (update-in [2 :tr-loppuetaisyys] dec))
        poista-kohde (with-fake-http [{:url (str yha-tyokalut/+yha-url+ "toteumakohde/" (get-in kohteet [0 :yhaid]))
                                       :method :delete} {:body yha-tyokalut/+onnistunut-kohteen-poisto-vastaus+}
                                      {:url (str yha-tyokalut/+yha-url+ "toteumakohde/" (get-in kohteet [1 :yhaid]))
                                       :method :delete} {:body (yha-tyokalut/+epaonnistunut-kohteen-poisto-vastaus+ (-> kohteet second :yhaid))}]
                                     (kutsu-palvelua (:http-palvelin jarjestelma)
                                                     :tallenna-yllapitokohteet
                                                     +kayttaja-jvh+
                                                     {:urakka-id urakka-id
                                                      :sopimus-id sopimus-id
                                                      :vuosi 2019
                                                      :kohteet kohteet}))
        [ensimmainen-kohde-poistettu?
         toinen-kohde-poistettu?] (into []
                                        (map :poistettu)
                                        (sort-by :yhaid
                                                 (q-map (str "SELECT poistettu, yhaid "
                                                             "FROM yllapitokohde "
                                                             "WHERE yhaid IN(" (apply str (interpose ", "
                                                                                                     (mapv :yhaid (take 2 kohteet))))
                                                             ");"))))
        kohteet-paivityksen-jalkeen (sort-by :yhaid
                                             (into []
                                                   haku-xf
                                                   (q-map (str "SELECT " kohteen-avaimet " "
                                                               "FROM yllapitokohde yk "
                                                               "WHERE yhaid IN(" (apply str (interpose ", "
                                                                                                       (mapv :yhaid kohteet)))
                                                               ");"))))]
    (is (= (:status poista-kohde) :yha-virhe))
    ;; Ensimmäinen kohde lähetettiin onnistuneesti YHA:an, joten sen tuloksen
    ;; halutaan olevan synkassa Harjaan
    (is (true? ensimmainen-kohde-poistettu?))
    ;; Toinen kohde epäonnistui, joten sen pitää olla myös Harjassa olemassa
    (is (false? toinen-kohde-poistettu?))
    ;; Muiden päivitysten tekeminen pitäisi epäonnistua myös
    (is (= (drop 1 (sort-by :yhaid testidata)) (drop 1 kohteet-paivityksen-jalkeen)))))


(deftest yllapitokohteen-tallennus-vaaraan-urakkaan-ei-onnistu
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
        leppajarvi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-yllapitokohteet +kayttaja-jvh+
                                           {:urakka-id urakka-id
                                            :sopimus-id sopimus-id
                                            :vuosi 2015
                                            :kohteet [(assoc yllapitokohde-testidata :id leppajarvi-id)]})))))

(deftest ala-poista-paallystyskohdetta-jolla-ilmoitus
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        maara-ennen-testia (ffirst (q
                                     (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        kohteet-ennen-testia (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :urakan-yllapitokohteet +kayttaja-jvh+
                                             {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                              :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)})
        kohde-jolla-ilmoitus (first (filter :paallystysilmoitus-id kohteet-ennen-testia))
        paivitetyt-kohteet (map
                             (fn [kohde] (if (= (:id kohde) (:id kohde-jolla-ilmoitus))
                                           (assoc kohde :poistettu true)
                                           kohde))
                             kohteet-ennen-testia)]

    (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :sopimus-id sopimus-id
                                                              :kohteet paivitetyt-kohteet})
    (let [maara-testin-jalkeen (ffirst (q
                                         (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
          kohteet-testin-jalkeen (kutsu-palvelua (:http-palvelin jarjestelma)
                                                 :urakan-yllapitokohteet
                                                 +kayttaja-jvh+ {:urakka-id urakka-id
                                                                 :sopimus-id sopimus-id})]

      (is (= maara-ennen-testia maara-testin-jalkeen))
      ;; Kohteet ovat kannassa samat kuin aiemmin
      (is (= (sort-by :id (map #(dissoc % :yllapitokohteen-voi-poistaa?
                                        :kohdeosat
                                        :muokattu)
                               kohteet-ennen-testia))
             (sort-by :id (map #(dissoc % :yllapitokohteen-voi-poistaa?
                                        :kohdeosat
                                        :muokattu)
                               kohteet-testin-jalkeen)))))))

(deftest tallenna-yllapitokohdeosa-kantaan
  (let [yllapitokohde-id (yllapitokohde-id-jolla-on-paallystysilmoitus)]
    (is (not (nil? yllapitokohde-id)))

    (let [urakka-id (hae-muhoksen-paallystysurakan-id)
          sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
          urakan-geometria-ennen-muutosta (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))
          maara-ennen-lisaysta (ffirst (q
                                         (str "SELECT count(*) FROM yllapitokohdeosa
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = yllapitokohdeosa.yllapitokohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))]

      (kutsu-palvelua (:http-palvelin jarjestelma)
                      :tallenna-yllapitokohdeosat +kayttaja-jvh+ {:urakka-id urakka-id
                                                                  :sopimus-id sopimus-id
                                                                  :yllapitokohde-id yllapitokohde-id
                                                                  :osat [yllapitokohdeosa-testidata]})
      (let [maara-lisayksen-jalkeen (ffirst (q
                                              (str "SELECT count(*) FROM yllapitokohdeosa
                                            LEFT JOIN yllapitokohde ON yllapitokohde.id = yllapitokohdeosa.yllapitokohde
                                            AND urakka = " urakka-id " AND sopimus = " sopimus-id ";")))
            kohdeosat-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :yllapitokohteen-yllapitokohdeosat
                                               +kayttaja-jvh+ {:urakka-id urakka-id
                                                               :sopimus-id sopimus-id
                                                               :yllapitokohde-id yllapitokohde-id})
            urakan-geometria-muutoksen-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))]
        (is (not (nil? kohdeosat-kannassa)))
        (is (every? :sijainti kohdeosat-kannassa) "Geometria muodostettiin")
        (is (not= urakan-geometria-ennen-muutosta urakan-geometria-muutoksen-jalkeen "Urakan geometria päivittyi"))
        (is (match (first kohdeosat-kannassa)
                   {:tr-kaista nil
                    :sijainti _
                    :tr-ajorata nil
                    :tr-numero 20
                    :tr-alkuosa 1
                    :tr-alkuetaisyys 1
                    :tr-loppuosa 2
                    :tr-loppuetaisyys 2
                    :nimi "Testiosa123456"
                    :id _
                    :toimenpide "Ei tehdä mitään"}
                   true))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))))))

(deftest paivita-paallystysurakan-yllapitokohteen-aikataulu
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        vuosi 2017
        aikataulu-kohde-alku (pvm/->pvm "15.5.2017")
        aikataulu-paallystys-alku (pvm/->pvm "19.5.2017")
        aikataulu-paallystys-loppu (pvm/->pvm "20.5.2017")
        aikataulu-kohde-valmis (pvm/->pvm "29.5.2017")
        maara-ennen-lisaysta (ffirst (q
                                       (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id "
                                         AND poistettu IS NOT TRUE
                                         AND vuodet @> ARRAY [" vuosi "] :: INT [];")))
        kohteet [{:id yllapitokohde-id
                  :nimi "Leppäjärven superramppi"
                  :kohdenumero "L666"
                  :aikataulu-kohde-alku aikataulu-kohde-alku
                  :aikataulu-paallystys-alku aikataulu-paallystys-alku
                  :aikataulu-paallystys-loppu aikataulu-paallystys-loppu
                  :aikataulu-kohde-valmis aikataulu-kohde-valmis}]
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-yllapitokohteiden-aikataulu
                                +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :sopimus-id sopimus-id
                                 :vuosi vuosi
                                 :kohteet kohteet})
        maara-paivityksen-jalkeen (ffirst (q
                                            (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id "
                                         AND poistettu IS NOT TRUE
                                         AND vuodet @> ARRAY [" vuosi "] :: INT [];")))
        vastaus-leppajarven-ramppi (kohde-nimella vastaus "Leppäjärven superramppi")]
    ;; Kohteiden määrä ei muuttunut
    (is (= maara-ennen-lisaysta maara-paivityksen-jalkeen (count vastaus)))
    ;; Muokatut kentät päivittyivät
    (is (= "Leppäjärven superramppi" (:nimi vastaus-leppajarven-ramppi)))
    (is (= "L666" (:kohdenumero vastaus-leppajarven-ramppi)))
    (is (= aikataulu-kohde-alku (:aikataulu-kohde-alku vastaus-leppajarven-ramppi)))
    (is (= aikataulu-paallystys-alku (:aikataulu-paallystys-alku vastaus-leppajarven-ramppi)))
    (is (= aikataulu-paallystys-loppu (:aikataulu-paallystys-loppu vastaus-leppajarven-ramppi)))
    (is (= aikataulu-kohde-valmis (:aikataulu-kohde-valmis vastaus-leppajarven-ramppi)))
    ;; Tiemerkinnän aikatauluun ei koskettu
    (is (= (pvm/->pvm "22.5.2017") (:aikataulu-tiemerkinta-alku vastaus-leppajarven-ramppi)))
    (is (= (pvm/->pvm "23.5.2017") (:aikataulu-tiemerkinta-loppu vastaus-leppajarven-ramppi)))))

(deftest tallenna-yllapitokohteen-tarkka-aikataulu
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        aikataulu-toimenpide :ojankaivuu
        aikataulu-kuvaus "Kaivetaan iso monttu!"
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-yllapitokohteiden-tarkka-aikataulu
                                +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :sopimus-id sopimus-id
                                 :vuosi 2017
                                 :yllapitokohde-id yllapitokohde-id
                                 :aikataulurivit [{:toimenpide aikataulu-toimenpide
                                                   :kuvaus aikataulu-kuvaus
                                                   :alku (pvm/->pvm "15.4.2017")
                                                   :loppu (pvm/->pvm "15.4.2017")}]})]

    ;; Vastauksena ylläpitokohteet, joista jokaisella on :tarkka-aikataulu (vähintään tyhjä)
    (is (every? :kohdenumero vastaus))
    (is (every? :tarkka-aikataulu vastaus))

    (let [paivitetty-aikataulurivi (-> (filter #(= (:id %) yllapitokohde-id) vastaus)
                                       first :tarkka-aikataulu first)]
      (is (= (:toimenpide paivitetty-aikataulurivi) aikataulu-toimenpide))
      (is (= (:kuvaus paivitetty-aikataulurivi) aikataulu-kuvaus)))))

(deftest tallenna-yllapitokohteen-tarkka-aikataulu-ilman-oikeutta
  (is (thrown? Exception
               (kutsu-palvelua (:http-palvelin jarjestelma)
                               :tallenna-yllapitokohteiden-tarkka-aikataulu
                               +kayttaja-tero+
                               {:urakka-id 4
                                :sopimus-id 5
                                :yllapitokohde-id 1
                                :aikataulurivit []}))))

(deftest tallenna-yllapitokohteen-tarkka-aikataulu-vaaraan-urakkaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2005-2010-paasopimuksen-id)
        yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)]
    (is (thrown? SecurityException
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-yllapitokohteiden-tarkka-aikataulu
                                 +kayttaja-jvh+
                                 {:urakka-id urakka-id
                                  :sopimus-id sopimus-id
                                  :vuosi 2017
                                  :yllapitokohde-id yllapitokohde-id
                                  :aikataulurivit []}))))

  ;; Leppäjärven suorittavan tiemerkintäurakan aikataulua saapi muokata
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        yllapitokohde-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)]
    (is (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tallenna-yllapitokohteiden-tarkka-aikataulu
                        +kayttaja-jvh+
                        {:urakka-id urakka-id
                         :sopimus-id sopimus-id
                         :vuosi 2017
                         :yllapitokohde-id yllapitokohde-id
                         :aikataulurivit []}))))

(deftest aikataulun-paivittaminen-vaaraan-urakkaan-kaatuu
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        oulaisten-ohitusramppi-id (hae-yllapitokohde-oulaisten-ohitusramppi)
        vuosi 2017
        kohteet [{:id oulaisten-ohitusramppi-id
                  :aikataulu-tiemerkinta-alku (pvm/->pvm "22.5.2017")
                  :aikataulu-tiemerkinta-loppu (pvm/->pvm "23.5.2017")}]]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-yllapitokohteiden-aikataulu
                                           +kayttaja-jvh+
                                           {:urakka-id urakka-id
                                            :sopimus-id sopimus-id
                                            :vuosi vuosi
                                            :kohteet kohteet}))
        "Oulaisten ohitusramppilla ei ole suorittavaa tiemerkintäurakkaa")))

(deftest paivita-tiemerkintaurakan-yllapitokohteen-aikataulu-ilman-etta-lahtee-maili
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        sahkoposti-valitetty (atom false)]
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
            nakkilan-ramppi-id (hae-yllapitokohde-nakkilan-ramppi)
            vuosi 2017
            leppajarvi-aikataulu-tiemerkinta-alku (pvm/->pvm "22.5.2017")
            leppajarvi-aikataulu-tiemerkinta-loppu (pvm/->pvm "23.5.2017")
            maara-ennen-lisaysta (ffirst (q
                                           (str "SELECT count(*) FROM yllapitokohde
                                         WHERE suorittava_tiemerkintaurakka = " urakka-id
                                                " AND poistettu IS NOT TRUE
                                                  AND vuodet @> ARRAY [" vuosi "] :: INT [];")))
            kohteet [{:id leppajarven-ramppi-id
                      :nimi "Leppäjärven superramppi"
                      :kohdenumero "666"
                      :aikataulu-tiemerkinta-alku leppajarvi-aikataulu-tiemerkinta-alku
                      :aikataulu-tiemerkinta-loppu leppajarvi-aikataulu-tiemerkinta-loppu}
                     {:id nakkilan-ramppi-id
                      :aikataulu-tiemerkinta-alku (pvm/->pvm "20.5.2017")}]
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-yllapitokohteiden-aikataulu
                                    +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi vuosi
                                     :kohteet kohteet})
            maara-paivityksen-jalkeen (ffirst (q
                                                (str "SELECT count(*) FROM yllapitokohde
                                         WHERE suorittava_tiemerkintaurakka = " urakka-id
                                                     " AND poistettu IS NOT TRUE
                                                     AND vuodet @> ARRAY [" vuosi "] :: INT [];")))
            vastaus-leppajarven-ramppi (kohde-nimella vastaus "Leppäjärven ramppi")]
        ;; Kohteiden määrä ei muuttunut
        (is (= maara-ennen-lisaysta maara-paivityksen-jalkeen (count vastaus)))
        ;; Nimi ja kohdenumero eivät muuttuneet, koska näitä ei saa muokata tiemerkintäurakassa
        (is (= "Leppäjärven ramppi" (:nimi vastaus-leppajarven-ramppi)))
        (is (= "L03" (:kohdenumero vastaus-leppajarven-ramppi)))
        ;; Aikataulukentät päivittyivät
        (is (= leppajarvi-aikataulu-tiemerkinta-loppu (:aikataulu-tiemerkinta-loppu vastaus-leppajarven-ramppi)))
        (is (= leppajarvi-aikataulu-tiemerkinta-alku (:aikataulu-tiemerkinta-alku vastaus-leppajarven-ramppi)))

        ;; Odotetaan hetki varmistuaksemme siitä, ettei sähköpostia lähetetä tässä tilanteessa
        ;; Leppäjärven tiemerkintä on jo merkitty valmiiksi ja uusi pvm on sama kuin vanha.
        ;; Nakkilan rampille asetettiin vain aloituspvm, joten siitäkään ei mailia laiteta.
        (<!! (timeout 5000))
        (is (false? @sahkoposti-valitetty) "Maili ei lähde, eikä pidäkään")))))

(deftest paivita-tiemerkintaurakan-yllapitokohteen-aikataulu-niin-etta-maili-lahtee
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-ja-oulun-tiemerkintaurakan-kayttajat.xml"))
        sahkopostien-sisallot (atom [])]
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [viesti]
                                                            (swap! sahkopostien-sisallot conj (sanomat/lue-sahkoposti (.getText viesti)))))
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
            vuosi 2017
            leppajarvi-aikataulu-tiemerkinta-alku (pvm/->pvm "22.5.2017")
            leppajarvi-aikataulu-tiemerkinta-loppu (pvm/->pvm "25.5.2017")
            maara-ennen-lisaysta (ffirst (q
                                           (str "SELECT count(*) FROM yllapitokohde
                                         WHERE suorittava_tiemerkintaurakka = " urakka-id
                                                " AND poistettu IS NOT TRUE
                                                  AND vuodet @> ARRAY [" vuosi "] :: INT [];")))
            saate "Kohteen saateviesti"
            kohteet [{:id leppajarven-ramppi-id
                      :sahkopostitiedot {:kopio-itselle? true
                                         :muut-vastaanottajat #{}
                                         :saate saate}
                      :aikataulu-tiemerkinta-alku leppajarvi-aikataulu-tiemerkinta-alku
                      :aikataulu-tiemerkinta-loppu leppajarvi-aikataulu-tiemerkinta-loppu}]
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-yllapitokohteiden-aikataulu
                                    +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi vuosi
                                     :kohteet kohteet})
            maara-paivityksen-jalkeen (ffirst (q
                                                (str "SELECT count(*) FROM yllapitokohde
                                         WHERE suorittava_tiemerkintaurakka = " urakka-id
                                                     " AND poistettu IS NOT TRUE
                                                     AND vuodet @> ARRAY [" vuosi "] :: INT [];")))
            vastaus-leppajarven-ramppi (kohde-nimella vastaus "Leppäjärven ramppi")]
        ;; Kohteiden määrä ei muuttunut
        (is (= maara-ennen-lisaysta maara-paivityksen-jalkeen (count vastaus)))
        ;; Muokatut kentät päivittyivät
        (is (= leppajarvi-aikataulu-tiemerkinta-loppu (:aikataulu-tiemerkinta-loppu vastaus-leppajarven-ramppi)))
        (is (= leppajarvi-aikataulu-tiemerkinta-alku (:aikataulu-tiemerkinta-alku vastaus-leppajarven-ramppi)))

        ;; Leppäjärven tiemerkintä oli jo merkitty valmiiksi, mutta sitä päivitettiin -> pitäisi lähteä maili
        (odota-ehdon-tayttymista #(= (count @sahkopostien-sisallot) 4) "Sähköpostit lähetettiin" 5000)
        (let [muhoksen-vastuuhenkilo (first (filter #(clj-str/includes? (:vastaanottaja %) "vastuuhenkilo@example.com") @sahkopostien-sisallot))
              muhoksen-urakanvalvoja (first (filter #(clj-str/includes? (:vastaanottaja %) "ELY_Urakanvalvoja@example.com") @sahkopostien-sisallot))
              tiemerkinnan-urakanvalvoja (first (filter #(clj-str/includes? (:vastaanottaja %) "erkki.esimerkki@example.com") @sahkopostien-sisallot))
              ilmoittaja (first (filter #(clj-str/includes? (:vastaanottaja %) "jalmari@example.com") @sahkopostien-sisallot))]
          ;; Viesti lähti oikeille henkilöille
          (is muhoksen-vastuuhenkilo)
          (is muhoksen-urakanvalvoja)
          (is tiemerkinnan-urakanvalvoja)
          (is ilmoittaja) ; Kopio lähettäjälle
          ;; Viestit lähetettiin oikeasta näkökulmasta
          (is (= (:otsikko muhoksen-vastuuhenkilo) "Harja: Urakan 'Muhoksen päällystysurakka' kohteen 'Leppäjärven ramppi' tiemerkintä on merkitty valmistuneeksi 25.05.2017"))
          (is (= (:otsikko muhoksen-urakanvalvoja) "Harja: Urakan 'Muhoksen päällystysurakka' kohteen 'Leppäjärven ramppi' tiemerkintä on merkitty valmistuneeksi 25.05.2017"))
          (is (= (:otsikko tiemerkinnan-urakanvalvoja) "Harja: Urakan 'Oulun tiemerkinnän palvelusopimus 2013-2018' kohteen 'Leppäjärven ramppi' tiemerkintä on merkitty valmistuneeksi 25.05.2017"))
          (is (= (:otsikko ilmoittaja) "Harja-viesti lähetetty: Urakan 'Oulun tiemerkinnän palvelusopimus 2013-2018' kohteen 'Leppäjärven ramppi' tiemerkintä on merkitty valmistuneeksi 25.05.2017")))
        ;; Sähköposteista löytyy oleelliset asiat
        (is (every? #(clj-str/includes? % saate) @sahkopostien-sisallot) "Saate löytyy")
        (is (every? #(clj-str/includes? % "25.05.2017") @sahkopostien-sisallot) "Valmistumispvm löytyy")
        (is (every? #(clj-str/includes? % "Jalmari Järjestelmävastuuhenkilö (org. Liikennevirasto)") @sahkopostien-sisallot)
            "Merkitsijä löytyy")))))

(deftest paivita-tiemerkintaurakan-yllapitokohteen-aikataulu-tulevaisuuteen
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        sahkoposti-valitetty (atom false)]
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
            vuosi 2017
            leppajarvi-aikataulu-tiemerkinta-alku (pvm/->pvm "22.5.2017")
            leppajarvi-aikataulu-tiemerkinta-loppu (pvm/->pvm "25.5.2060")
            saate "Kohde valmistui ajallaan"
            muut-vastaanottajat #{"erkki.petteri@esimerkki.com"}
            kohteet [{:id leppajarven-ramppi-id
                      :sahkopostitiedot {:kopio-itselle? true
                                         :muut-vastaanottajat muut-vastaanottajat
                                         :saate saate}
                      :aikataulu-tiemerkinta-alku leppajarvi-aikataulu-tiemerkinta-alku
                      :aikataulu-tiemerkinta-loppu leppajarvi-aikataulu-tiemerkinta-loppu}]
            mailitietojen-maara-ennen-lisaysta (ffirst (q
                                                         (str "SELECT count(*) FROM yllapitokohteen_sahkopostitiedot
                                         WHERE yllapitokohde_id = " 1 ";")))
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-yllapitokohteiden-aikataulu
                                    +kayttaja-jvh+
                                    {:urakka-id urakka-id
                                     :sopimus-id sopimus-id
                                     :vuosi vuosi
                                     :kohteet kohteet})
            vastaus-leppajarven-ramppi (kohde-nimella vastaus "Leppäjärven ramppi")
            mailitietojen-maara-lisayksen-jalkeen (q-map
                                                    (str "SELECT * FROM yllapitokohteen_sahkopostitiedot
                                         WHERE yllapitokohde_id = " 1 ";"))
            mailitiedot (first mailitietojen-maara-lisayksen-jalkeen)]
        ;; Muokatut kentät päivittyivät
        (is (= leppajarvi-aikataulu-tiemerkinta-loppu (:aikataulu-tiemerkinta-loppu vastaus-leppajarven-ramppi)))
        (is (= leppajarvi-aikataulu-tiemerkinta-alku (:aikataulu-tiemerkinta-alku vastaus-leppajarven-ramppi)))

        ;; Ennen lähetystä kohteella ei ollut yhtään odottavaa mailia
        (is (zero? mailitietojen-maara-ennen-lisaysta))

        ;; Leppäjärven tiemerkintä merkittiin valmistuneeksi tulevaisuuteen -> maili ei lähde, vaan tiedot menevät odotukseen
        (is (= mailitietojen-maara-ennen-lisaysta (dec (count mailitietojen-maara-lisayksen-jalkeen))))
        (is (= (:tyyppi mailitiedot) "tiemerkinta_valmistunut"))
        (is (= (:yllapitokohde_id mailitiedot) leppajarven-ramppi-id))
        (is (= (:saate mailitiedot) saate))
        (is (= (konv/array->set (:vastaanottajat mailitiedot)) muut-vastaanottajat))
        (is (true? (:kopio_lahettajalle mailitiedot)))
        (<!! (timeout 5000))
        (is (false? @sahkoposti-valitetty) "Maili ei lähde, eikä pidäkään")

        ;; Päivitä mailitiedot
        (let [kohteet [{:id leppajarven-ramppi-id
                        :sahkopostitiedot {:kopio-itselle? false
                                           :muut-vastaanottajat #{}
                                           :saate nil}
                        :aikataulu-tiemerkinta-alku leppajarvi-aikataulu-tiemerkinta-alku
                        :aikataulu-tiemerkinta-loppu leppajarvi-aikataulu-tiemerkinta-loppu}]
              vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :tallenna-yllapitokohteiden-aikataulu
                                      +kayttaja-jvh+
                                      {:urakka-id urakka-id
                                       :sopimus-id sopimus-id
                                       :vuosi vuosi
                                       :kohteet kohteet})
              mailitiedot-paivityksen-jalkeen (q-map
                                                (str "SELECT * FROM yllapitokohteen_sahkopostitiedot
                                         WHERE yllapitokohde_id = " 1 ";"))
              mailitiedot (first mailitiedot-paivityksen-jalkeen)]

          ;; Sähköpostitiedot päivittyi
          (is (= (:tyyppi mailitiedot) "tiemerkinta_valmistunut"))
          (is (= (:yllapitokohde_id mailitiedot) leppajarven-ramppi-id))
          (is (= (:saate mailitiedot) nil))
          (is (= (konv/array->set (:vastaanottajat mailitiedot)) #{}))
          (is (false? (:kopio_lahettajalle mailitiedot))))))))

(deftest merkitse-tiemerkintaurakan-kohde-valmiiksi-ilman-fim-kayttajia
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        sahkoposti-valitetty (atom false)]
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            nakkilan-ramppi-id (hae-yllapitokohde-nakkilan-ramppi)
            vuosi 2017
            kohteet [{:id nakkilan-ramppi-id
                      :aikataulu-tiemerkinta-alku (pvm/->pvm "27.5.2017")
                      :aikataulu-tiemerkinta-loppu (pvm/->pvm "28.5.2017")}]
            _ (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-yllapitokohteiden-aikataulu
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :sopimus-id sopimus-id
                               :vuosi vuosi
                               :kohteet kohteet})]
        ;; Maili lähtee, koska kopio itselle
        (<!! (timeout 5000))
        (is (true? @sahkoposti-valitetty) "Maili lähtee, koska kopio itselle")))))

(deftest merkitse-tiemerkintaurakan-kohde-valmiiksi-vaaraan-urakkaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
        nakkilan-ramppi-id (hae-yllapitokohde-nakkilan-ramppi)
        vuosi 2017
        kohteet [{:id nakkilan-ramppi-id
                  :aikataulu-tiemerkinta-alku (pvm/->pvm "27.5.2017")
                  :aikataulu-tiemerkinta-loppu (pvm/->pvm "28.5.2017")}]]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-yllapitokohteiden-aikataulu
                                           +kayttaja-jvh+
                                           {:urakka-id urakka-id
                                            :sopimus-id sopimus-id
                                            :vuosi vuosi
                                            :kohteet kohteet})))))

(deftest merkitse-tiemerkintaurakan-kohde-valmiiksi-ilman-fim-yhteytta
  (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" #())
  (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        nakkilan-ramppi-id (hae-yllapitokohde-nakkilan-ramppi)
        vuosi 2017
        aikataulu-tiemerkinta-alku (pvm/->pvm "27.5.2017")
        aikataulu-tiemerkinta-loppu (pvm/->pvm "28.5.2017")
        kohteet [{:id nakkilan-ramppi-id
                  :aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-alku
                  :aikataulu-tiemerkinta-loppu aikataulu-tiemerkinta-loppu}]
        _ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-yllapitokohteiden-aikataulu
                          +kayttaja-jvh+
                          {:urakka-id urakka-id
                           :sopimus-id sopimus-id
                           :vuosi vuosi
                           :kohteet kohteet})]))

(deftest merkitse-tiemerkintaurakan-kohde-valmiiksi
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        sahkoposti-valitetty (atom false)]
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            nakkilan-ramppi-id (hae-yllapitokohde-nakkilan-ramppi)
            vuosi 2017
            aikataulu-tiemerkinta-alku (pvm/->pvm "27.5.2017")
            aikataulu-tiemerkinta-loppu (pvm/->pvm "28.5.2017")
            kohteet [{:id nakkilan-ramppi-id
                      :aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-alku
                      :aikataulu-tiemerkinta-loppu aikataulu-tiemerkinta-loppu}]
            _ (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-yllapitokohteiden-aikataulu
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :sopimus-id sopimus-id
                               :vuosi vuosi
                               :kohteet kohteet})]
        ;; Nakkilan ramppi merkitään valmistuneeksi ensimmäisen kerran, pitäisi lähteä maili
        (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 5000)
        (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")))))

(deftest merkitse-tiemerkintaurakan-usea-kohde-valmiiksi
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        sahkoposti-valitetty (atom false)]
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            oulun-ohitusramppi-id (hae-yllapitokohde-oulun-ohitusramppi)
            nakkilan-ramppi-id (hae-yllapitokohde-nakkilan-ramppi)
            vuosi 2017
            aikataulu-tiemerkinta-alku (pvm/->pvm "27.5.2017")
            aikataulu-tiemerkinta-loppu (pvm/->pvm "28.5.2017")
            kohteet [{:id nakkilan-ramppi-id
                      :aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-alku
                      :aikataulu-tiemerkinta-loppu aikataulu-tiemerkinta-loppu}
                     {:id oulun-ohitusramppi-id
                      :aikataulu-tiemerkinta-alku aikataulu-tiemerkinta-alku
                      :aikataulu-tiemerkinta-loppu aikataulu-tiemerkinta-loppu}]
            _ (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-yllapitokohteiden-aikataulu
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :sopimus-id sopimus-id
                               :vuosi vuosi
                               :kohteet kohteet})]
        ;; Usea kohde merkitään valmiiksi, pitäisi lähteä maili
        (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 5000)
        (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")))))

(deftest merkitse-tiemerkintaurakan-kohde-valmiiksi-vaaralla-urakalla
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        sahkoposti-valitetty (atom false)]
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [urakka-id (hae-oulun-tiemerkintaurakan-id)
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            oulaisten-ohitusramppi-id (hae-yllapitokohde-oulaisten-ohitusramppi)
            vuosi 2017
            kohteet [{:id oulaisten-ohitusramppi-id
                      :aikataulu-tiemerkinta-alku (pvm/->pvm "27.5.2017")
                      :aikataulu-tiemerkinta-loppu (pvm/->pvm "27.5.2017")}]]
        (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :tallenna-yllapitokohteiden-aikataulu
                                               +kayttaja-jvh+
                                               {:urakka-id urakka-id
                                                :sopimus-id sopimus-id
                                                :vuosi vuosi
                                                :kohteet kohteet}))
            "Oulaisten ohitusramppi ei ole merkitty suoritettavaksi tähän urakkaan")))))

(deftest paallystyksen-merkitseminen-valmiiksi-toimii
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        oulaisten-ohitusramppi-id (hae-yllapitokohde-oulaisten-ohitusramppi)
        suorittava-tiemerkintaurakka-id (hae-oulun-tiemerkintaurakan-id)
        sahkoposti-valitetty (atom false)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        vuosi 2017]

    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))

    (with-fake-http
      [+testi-fim+ fim-vastaus]

      ;; Lisätään kohteelle ensin päällystyksen aikataulutiedot ja suorittava tiemerkintäurakka
      (let [tiemerkintapvm (pvm/->pvm-aika "23.5.2017 12:00")
            _ (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-yllapitokohteiden-aikataulu
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :sopimus-id sopimus-id
                               :vuosi vuosi
                               :kohteet [{:id oulaisten-ohitusramppi-id
                                          :nimi "Oulaisten ohitusramppi"
                                          :suorittava-tiemerkintaurakka suorittava-tiemerkintaurakka-id
                                          :aikataulu-paallystys-alku (pvm/->pvm-aika "19.5.2017 12:00")
                                          :aikataulu-paallystys-loppu (pvm/->pvm-aika "20.5.2017 12:00")}]})
            ;; Merkitään kohde valmiiksi tiemerkintään
            aikataulu-ennen-testia (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                                   {:urakka-id urakka-id
                                                    :sopimus-id sopimus-id
                                                    :vuosi vuosi})
            oulaisten-ohitusramppi-ennen-testia (kohde-nimella aikataulu-ennen-testia "Oulaisten ohitusramppi")
            muut-kohteet-ennen-testia (first (filter #(not= (:nimi %) "Oulaisten ohitusramppi") aikataulu-ennen-testia))
            vastaus-kun-merkittu-valmiiksi (kutsu-palvelua (:http-palvelin jarjestelma)
                                                           :merkitse-kohde-valmiiksi-tiemerkintaan +kayttaja-jvh+
                                                           {:tiemerkintapvm tiemerkintapvm
                                                            :kohde-id oulaisten-ohitusramppi-id
                                                            :urakka-id urakka-id
                                                            :sopimus-id sopimus-id
                                                            :vuosi vuosi})
            oulaisten-ohitusramppi-testin-jalkeen (kohde-nimella vastaus-kun-merkittu-valmiiksi "Oulaisten ohitusramppi")
            muut-kohteet-testin-jalkeen (first (filter #(not= (:nimi %) "Oulaisten ohitusramppi") vastaus-kun-merkittu-valmiiksi))]

        (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 5000)
        (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")

        ;; Valmiiksi merkitsemisen jälkeen tilanne on sama kuin ennen merkintää, sillä erotuksella, että
        ;; valittu kohde merkittiin valmiiksi tiemerkintään
        (is (= muut-kohteet-ennen-testia muut-kohteet-testin-jalkeen))
        (is (= (dissoc oulaisten-ohitusramppi-ennen-testia
                       :aikataulu-tiemerkinta-takaraja
                       :tiemerkintaurakan-voi-vaihtaa?)
               (dissoc oulaisten-ohitusramppi-ennen-testia
                       :aikataulu-tiemerkinta-takaraja
                       :tiemerkintaurakan-voi-vaihtaa?)))
        (is (nil? (:aikataulu-tiemerkinta-takaraja oulaisten-ohitusramppi-ennen-testia)))
        (is (nil? (:valmis-tiemerkintaan oulaisten-ohitusramppi-ennen-testia)))
        (is (some? (:aikataulu-tiemerkinta-takaraja oulaisten-ohitusramppi-testin-jalkeen)))
        (is (some? (:valmis-tiemerkintaan oulaisten-ohitusramppi-testin-jalkeen)))))))

(deftest tiemerkintavalmiuden-peruminen-toimii
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        suorittava-tiemerkintaurakka-id (hae-oulun-tiemerkintaurakan-id)
        sahkoposti-valitetty (atom false)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        vuosi 2017]

    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (fn [_] (reset! sahkoposti-valitetty true)))

    (with-fake-http
      [+testi-fim+ fim-vastaus]

      ;; Lisätään kohteelle ensin päällystyksen aikataulutiedot ja suorittava tiemerkintäurakka
      (let [tiemerkintapvm nil ; Tämä tarkoittaa että valmius perutaan
            _ (kutsu-palvelua (:http-palvelin jarjestelma)
                              :tallenna-yllapitokohteiden-aikataulu
                              +kayttaja-jvh+
                              {:urakka-id urakka-id
                               :sopimus-id sopimus-id
                               :vuosi vuosi
                               :kohteet [{:id leppajarven-ramppi-id
                                          :nimi "Leppäjärven ramppi"
                                          :suorittava-tiemerkintaurakka suorittava-tiemerkintaurakka-id
                                          :aikataulu-paallystys-alku (pvm/->pvm-aika "19.5.2017 12:00")
                                          :aikataulu-paallystys-loppu (pvm/->pvm-aika "20.5.2017 12:00")}]})
            ;; Merkitään kohde valmiiksi tiemerkintään
            aikataulu-ennen-testia (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                                   {:urakka-id urakka-id
                                                    :sopimus-id sopimus-id
                                                    :vuosi vuosi})
            leppajarven-ramppi-ennen-testia (kohde-nimella aikataulu-ennen-testia "Leppäjärven ramppi")
            muut-kohteet-ennen-testia (first (filter #(not= (:nimi %) "Leppäjärven ramppi") aikataulu-ennen-testia))
            vastaus-kun-merkittu-valmiiksi (kutsu-palvelua (:http-palvelin jarjestelma)
                                                           :merkitse-kohde-valmiiksi-tiemerkintaan +kayttaja-jvh+
                                                           {:tiemerkintapvm tiemerkintapvm
                                                            :kohde-id leppajarven-ramppi-id
                                                            :urakka-id urakka-id
                                                            :sopimus-id sopimus-id
                                                            :vuosi vuosi})
            leppajarven-ramppi-testin-jalkeen (kohde-nimella vastaus-kun-merkittu-valmiiksi "Leppäjärven ramppi")
            muut-kohteet-testin-jalkeen (first (filter #(not= (:nimi %) "Leppäjärven ramppi") vastaus-kun-merkittu-valmiiksi))]

        (odota-ehdon-tayttymista #(true? @sahkoposti-valitetty) "Sähköposti lähetettiin" 5000)
        (is (true? @sahkoposti-valitetty) "Sähköposti lähetettiin")

        ;; Valmiiksi merkitsemisen jälkeen tilanne on sama kuin ennen merkintää, sillä erotuksella, että
        ;; valittu kohde merkittiin valmiiksi tiemerkintään
        (is (= muut-kohteet-ennen-testia muut-kohteet-testin-jalkeen))
        (is (= (dissoc leppajarven-ramppi-ennen-testia
                       :aikataulu-tiemerkinta-takaraja
                       :tiemerkintaurakan-voi-vaihtaa?)
               (dissoc leppajarven-ramppi-ennen-testia
                       :aikataulu-tiemerkinta-takaraja
                       :tiemerkintaurakan-voi-vaihtaa?)))
        (is (some? (:valmis-tiemerkintaan leppajarven-ramppi-ennen-testia)))
        (is (nil? (:aikataulu-tiemerkinta-takaraja leppajarven-ramppi-testin-jalkeen)))
        (is (nil? (:valmis-tiemerkintaan leppajarven-ramppi-testin-jalkeen)))))))

(deftest yllapitokohteen-suorittavan-tiemerkintaurakan-vaihto-ei-toimi-jos-kirjauksia
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        lapin-urakka-id (hae-lapin-tiemerkintaurakan-id)
        vuosi 2017
        leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        kohteet [{:id leppajarven-ramppi-id
                  :nimi "Leppäjärven ramppi"
                  :kohdenumero "L03"
                  :aikataulu-paallystys-alku (pvm/->pvm-aika "19.5.2017 12:00")
                  :aikataulu-kohde-valmis (pvm/->pvm "29.5.2017")
                  :suorittava-tiemerkintaurakka lapin-urakka-id
                  :valmis-tiemerkintaan (pvm/->pvm-aika "23.5.2017 12:00")
                  :aikataulu-paallystys-loppu (pvm/->pvm-aika "20.5.2017 12:00"),
                  :aikataulu-tiemerkinta-takaraja (pvm/->pvm "1.6.2017")
                  :aikataulu-tiemerkinta-alku nil,
                  :aikataulu-tiemerkinta-loppu (pvm/->pvm "26.5.2017")}]
        aiempi-aikataulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                         {:urakka-id (hae-muhoksen-paallystysurakan-id)
                                          :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                                          :vuosi vuosi})
        aiempi-aikataulu-leppajarven-ramppi (kohde-nimella aiempi-aikataulu "Leppäjärven ramppi")
        nykyinen-aikataulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-yllapitokohteiden-aikataulu
                                           +kayttaja-jvh+
                                           {:urakka-id urakka-id
                                            :sopimus-id sopimus-id
                                            :vuosi vuosi
                                            :kohteet kohteet})
        nykyinen-aikataulu-leppajarven-ramppi (kohde-nimella nykyinen-aikataulu "Leppäjärven ramppi")]

    (is (not= (:suorittava-tiemerkintaurakka nykyinen-aikataulu-leppajarven-ramppi) lapin-urakka-id)
        "Suorittavaa tiemerkintäurakkaa ei vaihdettu, koska tiemerkintäurakasta on tehty kirjauksia kohteelle")
    (is (= (:suorittava-tiemerkintaurakka aiempi-aikataulu-leppajarven-ramppi)
           (:suorittava-tiemerkintaurakka nykyinen-aikataulu-leppajarven-ramppi))
        "Leppäjärven rampin suorittava tiemerkintäurakka on edelleen sama")))

(deftest testidatassa-validit-aikataulut
  (let [yllapitokohteet (:maara (first (q-map "SELECT COUNT(*) as maara FROM yllapitokohde;")))
        aikataulut (:maara (first (q-map "SELECT COUNT(*) as maara FROM yllapitokohteen_aikataulu;")))]
    (is (> yllapitokohteet 1))
    (is (> aikataulut 1))
    (is (= yllapitokohteet aikataulut) "Testidatassa tulisi olla jokaisella ylläpitokohteella aikataulu")))


(deftest sakkojen-maara-oikein-paallystysurakan-kohteille
  (let [urakka-id (hae-muhoksen-paallystysurakan-id)
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)]
    (let [kohteet-kannassa (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :urakan-yllapitokohteet
                                           +kayttaja-jvh+ {:urakka-id urakka-id
                                                           :sopimus-id sopimus-id
                                                           :vuosi 2017})
          sakot-ja-bonukset-maara (reduce + 0 (keep :sakot-ja-bonukset kohteet-kannassa))]
      (is (not (nil? kohteet-kannassa)))
      (is (= -1000M sakot-ja-bonukset-maara)))))


(deftest yllapitokohteen-urakan-yhteyshenkiloiden-haku
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))]
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :yllapitokohteen-urakan-yhteyshenkilot
                                    +kayttaja-jvh+
                                    {:yllapitokohde-id leppajarven-ramppi-id
                                     :urakkatyyppi :paallystys})]

        (is (= vastaus
               {:fim-kayttajat [{:kayttajatunnus "A000001"
                                 :sahkoposti "ELY_Urakanvalvoja@example.com"
                                 :puhelin ""
                                 :sukunimi "Esimerkki"
                                 :roolit ["ELY urakanvalvoja"]
                                 :roolinimet ["ELY_Urakanvalvoja"]
                                 :poistettu false
                                 :etunimi "Erkki"
                                 :tunniste nil
                                 :organisaatio "ELY"}
                                {:kayttajatunnus "A000002"
                                 :sahkoposti "vastuuhenkilo@example.com"
                                 :puhelin "0400123456789"
                                 :sukunimi "Esimerkki"
                                 :roolit ["Urakan vastuuhenkilö"]
                                 :roolinimet ["vastuuhenkilo"]
                                 :poistettu false
                                 :etunimi "Eero"
                                 :tunniste nil
                                 :organisaatio "ELY"}
                                {:kayttajatunnus "A000003"
                                 :sahkoposti "eetvartti.esimerkki@example.com"
                                 :puhelin "0400123456788"
                                 :sukunimi "Esimerkki"
                                 :roolit []
                                 :roolinimet []
                                 :poistettu false
                                 :etunimi "Eetvartti"
                                 :tunniste nil
                                 :organisaatio "ELY"}]
                :yhteyshenkilot [{:kayttajatunnus "Blad1936"
                                  :sahkoposti "VihtoriOllila@einrot.com"
                                  :sukunimi "Ollila"
                                  :rooli "Kunnossapitopäällikkö"
                                  :id 89
                                  :matkapuhelin "042 220 6892"
                                  :etunimi "Vihtori"
                                  :organisaatio {:tyyppi :urakoitsija
                                                 :id 14
                                                 :nimi "YIT Rakennus Oy"
                                                 :lyhenne nil}
                                  :tyopuhelin nil
                                  :organisaatio_nimi "YIT Rakennus Oy"}
                                 {:kayttajatunnus "Clorge69"
                                  :sahkoposti "ReijoVanska@gustr.com"
                                  :sukunimi "Vänskä"
                                  :rooli "Tieliikennekeskus"
                                  :id 90
                                  :matkapuhelin "042 805 1911"
                                  :etunimi "Reijo"
                                  :organisaatio {:tyyppi :urakoitsija
                                                 :id 14
                                                 :nimi "YIT Rakennus Oy"
                                                 :lyhenne nil}
                                  :tyopuhelin nil
                                  :organisaatio_nimi "YIT Rakennus Oy"}]}))))))

(deftest yllapitokohteen-urakan-yhteyshenkiloiden-haku-ilman-oikeuksia
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))]
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
            yhteyshenkilot (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :yllapitokohteen-urakan-yhteyshenkilot
                                           +kayttaja-vastuuhlo-muhos+
                                           {:yllapitokohde-id leppajarven-ramppi-id
                                            :urakkatyyppi :paallystys})]
        (is (map? yhteyshenkilot) "Yhteyshenkilöt palautuivat")
        (is (= 3 (count (:fim-kayttajat yhteyshenkilot))) "FIM käyttäjäin lkm")
        (is (= 2 (count (:yhteyshenkilot yhteyshenkilot))) "Yhteyshenkilöiden lkm")))))
