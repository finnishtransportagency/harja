(ns harja.palvelin.palvelut.yllapitokohteet-test
  (:require [clojure.test :refer :all]

            [clojure.core.match :refer [match]]
            [clojure.core.async :refer [<!! timeout]]
            [clojure.java.io :as io]
            [clojure.string :as clj-str]

            [clj-time.coerce :as c]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.integraatio :as integraatio]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.komponentit
             [tietokanta :as tietokanta]
             [fim-test :refer [+testi-fim+]]
             [fim :as fim]]
            [harja.palvelin.palvelut
             [yllapitokohteet :refer :all]]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet.paallystyskohteet-excel :as paallystyskohteet-excel]
            [harja.testi :refer :all]

            [harja.jms-test :refer [feikki-jms]]
            [harja.pvm :as pvm]

            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.yha
             [tyokalut :as yha-tyokalut]
             [yha-komponentti :as yha]]

            [harja.domain.yllapitokohde :as yllapitokohteet-domain]
            [harja.paneeliapurit :as paneeli]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q])
  (:import (java.util UUID))
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
                               (fim/->FIM {:url +testi-fim+})
                               [:db :integraatioloki])
                        :itmf (feikki-jms "itmf")
                        :api-sahkoposti (component/using
                                          (sahkoposti-api/->ApiSahkoposti {:api-sahkoposti integraatio/api-sahkoposti-asetukset
                                                                           :tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                                          [:http-palvelin :db :integraatioloki :itmf])
                        :http-palvelin (testi-http-palvelin)
                        :yllapitokohteet (component/using
                                           (->Yllapitokohteet {})
                                           [:http-palvelin :db :fim :api-sahkoposti :yha-integraatio])
                        :yha-integraatio (component/using
                                           (yha/->Yha {:url yha-tyokalut/+yha-url+})
                                           [:db :http-palvelin :integraatioloki])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(def yllapitokohde-testidata {:kohdenumero 999
                              :nimi "Testiramppi4564ddf"
                              :yllapitokohdetyotyyppi :paallystys
                              :sopimuksen-mukaiset-tyot 400
                              :tr-numero 20
                              :tr-ajorata 1
                              :tr-kaista 11
                              :tr-alkuosa 1
                              :tr-alkuetaisyys 1
                              :tr-loppuosa 3
                              :tr-loppuetaisyys 2
                              :bitumi_indeksi 123
                              :kaasuindeksi 123})

(def yllapitokohdeosa-testidata {:nimi "Testiosa123456"
                                 :tr-numero 20
                                 :tr-alkuosa 1
                                 :tr-alkuetaisyys 1
                                 :tr-loppuosa 3
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
                   WHERE urakka = " (hae-urakan-id-nimella "Muhoksen päällystysurakka") " AND sopimus = " (hae-muhoksen-paallystysurakan-paasopimuksen-id) ";"))))


(deftest paallystyskohteet-haettu-oikein
  (let [kohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                :urakan-yllapitokohteet +kayttaja-jvh+
                                {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                                 :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " (hae-urakan-id-nimella "Muhoksen päällystysurakka") ")
                                      AND poistettu IS NOT TRUE;")))
        leppajarven-ramppi (kohde-nimella kohteet "Leppäjärven ramppi")]
    (is (= (count kohteet) kohteiden-lkm) "Päällystyskohteiden määrä")
    (is (== (:maaramuutokset leppajarven-ramppi) 205)
        "Leppäjärven rampin määrämuutos laskettu oikein")))

(deftest paallystyskohteiden-tila-paatellaan-oikein-kun-kesa-menossa
  (with-redefs [pvm/nyt #(pvm/luo-pvm 2017 4 25)] ;; 25.5.2017
    (let [kohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :urakan-yllapitokohteet +kayttaja-jvh+
                                  {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
                                  {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
                                  {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
                                {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                                 :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                                 :vuosi 2017})]
    (is (paneeli/skeeman-luonti-onnistuu-kaikille?
          :paallystys
          (into [] yllapitokohteet-domain/yllapitokohde-kartalle-xf vastaus)))))

(deftest paallystysurakan-aikatauluhaku-toimii
  (let [urakan-yllapitokohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                               :urakan-yllapitokohteet +kayttaja-jvh+
                                               {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                                                :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                                                :vuosi 2017})
        aikataulu (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                  {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
                                  {:urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
                                   :sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
                                   :vuosi 2017})]
    (is (= (count aikataulu) 2) "Löytyi kaikki tiemerkintäurakalle osoitetut ylläpitokohteet")
    (is (not-any? #(contains? % :suorittava-tiemerkintaurakka) aikataulu)
        "Tiemerkinnän aikataulu ei sisällä päällystysurakkaan liittyvää tietoa")))

(deftest paallystyskohteet-haettu-oikein-vuodelle-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :urakan-yllapitokohteet +kayttaja-jvh+
                                {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                                 :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                                 :vuosi 2017})
        kohteiden-lkm (ffirst (q
                                (str "SELECT COUNT(*)
                                      FROM yllapitokohde
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " (hae-urakan-id-nimella "Muhoksen päällystysurakka") ")
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
                            {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                             :sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
                             :vuosi 2016})]
    (is (= (count res) 0) "Ei päällystyskohteita vuodelle 2016")))

(deftest tallenna-paallystyskohde-kantaan
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
          urakan-geometria-lisayksen-jalkeen (ffirst (q "SELECT ST_ASTEXT(alue) FROM urakka WHERe id = " urakka-id ";"))
          _ (kutsu-palvelua (:http-palvelin jarjestelma)
                    :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                              :vuosi 2018
                                                              :sopimus-id sopimus-id
                                                              :kohteet [(assoc yllapitokohde-testidata
                                                                          :tr-numero 20
                                                                          :tr-alkuosa 1
                                                                          :tr-alkuetaisyys 1
                                                                          :tr-loppuosa 1
                                                                          :tr-loppuetaisyys 5)]})
          paallekkaiset-paakohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :urakan-yllapitokohteet
                                                   +kayttaja-jvh+ {:urakka-id urakka-id
                                                                   :sopimus-id sopimus-id})]
      (is (not (nil? kohteet-kannassa)))
      (is (not= urakan-geometria-ennen-muutosta urakan-geometria-lisayksen-jalkeen "Urakan geometria päivittyi"))
      (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))
      (is (= (count (filter #(= (:nimi %) "Testiramppi4564ddf") paallekkaiset-paakohteet)) 2))
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
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
                                      WHERE sopimus IN (SELECT id FROM sopimus WHERE urakka = " (hae-urakan-id-nimella "Muhoksen päällystysurakka") ")
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
      (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
      (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
                                                :tr-kaista 1}]})
            yha-tr-osoite-jalkeen (ffirst (q (str "SELECT yha_tr_osoite FROM yllapitokohde WHERE nimi='Leppäjärven ramppi (päivitetty)'")))]
        (is (not= (:status vastaus) :validointiongelma)
            "Yritetään tallentaa uusi ylläpitokohde, joka menee Leppäjärven rampin päälle.
             Samalla tallennetaan kuitenkin myös uusi Leppäjärven ramppi, jossa tieosoite siirtyy. Ei tule Herjaa.")
        (is (= {:numero 20, :alkuosa 1, :alkuetaisyys 0, :loppuosa 3, :loppuetaisyys 0}
               (konv/lue-tr-osoite yha-tr-osoite-jalkeen)) "Päivittäminen ei koske yha-tr-osoite")))

    (testing "Päällekkäin menevät kohteet eri vuonna"
      (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
      (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
      (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
      (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
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
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
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
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
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
                                     yk.yhaid NOT IN (13376, 13377, 527823070, 547523069) AND
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
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        maara-ennen-testia (ffirst (q
                                     (str "SELECT count(*) FROM yllapitokohde
                                         WHERE urakka = " urakka-id " AND sopimus= " sopimus-id ";")))
        kohteet-ennen-testia (kutsu-palvelua (:http-palvelin jarjestelma)
                                             :urakan-yllapitokohteet +kayttaja-jvh+
                                             {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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

    (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
                    :massamaara nil
                    :tr-loppuosa 3
                    :tr-alkuosa 1
                    :tr-loppuetaisyys 2
                    :nimi "Testiosa123456"
                    :raekoko nil
                    :tyomenetelma nil
                    :paallystetyyppi nil
                    :id _
                    :tr-alkuetaisyys 1
                    :tr-numero 20
                    :toimenpide "Ei tehdä mitään"}
                   true))
        (is (= (+ maara-ennen-lisaysta 1) maara-lisayksen-jalkeen))))))

(deftest paivita-paallystysurakan-yllapitokohteen-aikataulu
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
  (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
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
  (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        oulaisten-ohitusramppi-id (hae-yllapitokohteen-id-nimella "Oulaisten ohitusramppi")
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
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
            nakkilan-ramppi-id (hae-yllapitokohteen-id-nimella "Nakkilan ramppi")
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
                      :aikataulu-tiemerkinta-loppu leppajarvi-aikataulu-tiemerkinta-loppu
                      :aikataulu-tiemerkinta-lisatieto "Tiemerkinnän lisätieto"}
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
            vastaus-leppajarven-ramppi (kohde-nimella vastaus "Leppäjärven ramppi")
            integraatioviestit (hae-ulos-lahtevat-integraatiotapahtumat)]
        ;; Kohteiden määrä ei muuttunut
        (is (= maara-ennen-lisaysta maara-paivityksen-jalkeen (count vastaus)))
        ;; Nimi ja kohdenumero eivät muuttuneet, koska näitä ei saa muokata tiemerkintäurakassa
        (is (= "Leppäjärven ramppi" (:nimi vastaus-leppajarven-ramppi)))
        (is (= "L03" (:kohdenumero vastaus-leppajarven-ramppi)))
        (is (= "Tiemerkinnän lisätieto" (:aikataulu-tiemerkinta-lisatieto vastaus-leppajarven-ramppi)) "Tiemerkinnän lisätieto")
        ;; Aikataulukentät päivittyivät
        (is (= leppajarvi-aikataulu-tiemerkinta-loppu (:aikataulu-tiemerkinta-loppu vastaus-leppajarven-ramppi)))
        (is (= leppajarvi-aikataulu-tiemerkinta-alku (:aikataulu-tiemerkinta-alku vastaus-leppajarven-ramppi)))


        ;; Odotetaan hetki varmistuaksemme siitä, ettei sähköpostia lähetetä tässä tilanteessa
        ;; Leppäjärven tiemerkintä on jo merkitty valmiiksi ja uusi pvm on sama kuin vanha.
        ;; Nakkilan rampille asetettiin vain aloituspvm, joten siitäkään ei mailia laiteta.
        (is (nil? (some #(clj-str/includes? (:sisalto %) "sahkoposti:sahkoposti") integraatioviestit)) "Maili on lähetetty.")))))

(deftest paivita-tiemerkintaurakan-yllapitokohteen-aikataulu-niin-etta-maili-lahtee
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-ja-oulun-tiemerkintaurakan-kayttajat.xml"))
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
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
            vastaus-leppajarven-ramppi (kohde-nimella vastaus "Leppäjärven ramppi")
            integraatio-sahkopostit (hae-ulos-lahtevat-integraatiotapahtumat)]
        ;; Kohteiden määrä ei muuttunut
        (is (= maara-ennen-lisaysta maara-paivityksen-jalkeen (count vastaus)))
        ;; Muokatut kentät päivittyivät
        (is (= leppajarvi-aikataulu-tiemerkinta-loppu (:aikataulu-tiemerkinta-loppu vastaus-leppajarven-ramppi)))
        (is (= leppajarvi-aikataulu-tiemerkinta-alku (:aikataulu-tiemerkinta-alku vastaus-leppajarven-ramppi)))

        ;; Leppäjärven tiemerkintä oli jo merkitty valmiiksi, mutta sitä päivitettiin -> pitäisi lähteä maili
        (let [muhoksen-vastuuhenkilo (first (filter #(clj-str/includes? (:sisalto %) "vastuuhenkilo@example.com") integraatio-sahkopostit))
              muhoksen-urakanvalvoja (first (filter #(clj-str/includes? (:sisalto %) "ELY_Urakanvalvoja@example.com") integraatio-sahkopostit))
              tiemerkinnan-urakanvalvoja (first (filter #(clj-str/includes? (:sisalto %) "erkki.esimerkki@example.com") integraatio-sahkopostit))
              ilmoittaja (first (filter #(clj-str/includes? (:sisalto %) "jalmari@example.com") integraatio-sahkopostit))]
          ;; Viesti lähti oikeille henkilöille
          (is muhoksen-vastuuhenkilo)
          (is muhoksen-urakanvalvoja)
          (is tiemerkinnan-urakanvalvoja)
          (is ilmoittaja) ; Kopio lähettäjälle
          ;; Viestit lähetettiin oikeasta näkökulmasta
          (is (clj-str/includes? (:sisalto muhoksen-vastuuhenkilo) "Harja: Urakan 'Muhoksen päällystysurakka' kohteen 'Leppäjärven ramppi' tiemerkintä on merkitty valmistuneeksi 25.05.2017"))
          (is (clj-str/includes? (:sisalto muhoksen-urakanvalvoja) "Harja: Urakan 'Muhoksen päällystysurakka' kohteen 'Leppäjärven ramppi' tiemerkintä on merkitty valmistuneeksi 25.05.2017"))
          (is (clj-str/includes? (:sisalto tiemerkinnan-urakanvalvoja) "Harja: Urakan 'Oulun tiemerkinnän palvelusopimus 2013-2022' kohteen 'Leppäjärven ramppi' tiemerkintä on merkitty valmistuneeksi 25.05.2017"))
          (is (clj-str/includes? (:sisalto ilmoittaja) "Harja-viesti lähetetty: Urakan 'Oulun tiemerkinnän palvelusopimus 2013-2022' kohteen 'Leppäjärven ramppi' tiemerkintä on merkitty valmistuneeksi 25.05.2017")))
        ;; Sähköposteista löytyy oleelliset asiat
        (is (every? #(clj-str/includes? (:sisalto %) saate) integraatio-sahkopostit) "Saate löytyy")
        (is (every? #(clj-str/includes? (:sisalto %) "25.05.2017") integraatio-sahkopostit) "Valmistumispvm löytyy")
        (is (every? #(clj-str/includes? (:sisalto %) "Jalmari Järjestelmävastuuhenkilö (org. Liikennevirasto)") integraatio-sahkopostit)
            "Merkitsijä löytyy")))))

(deftest paivita-tiemerkintaurakan-yllapitokohteen-aikataulu-tulevaisuuteen
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
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
            mailitiedot (first mailitietojen-maara-lisayksen-jalkeen)
            integraatio-sahkopostit (hae-ulos-lahtevat-integraatiotapahtumat)]
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
        (is (empty? integraatio-sahkopostit) "Maili ei lähde, eikä pidäkään")

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
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            nakkilan-ramppi-id (hae-yllapitokohteen-id-nimella "Nakkilan ramppi")
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
                               :kohteet kohteet})
            integraatioviestit (hae-ulos-lahtevat-integraatiotapahtumat)]
        (is (clj-str/includes? (:sisalto (first integraatioviestit)) "sahkoposti:sahkoposti") "Maili on lähetetty.")))))

(deftest merkitse-tiemerkintaurakan-kohde-valmiiksi-vaaraan-urakkaan
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        sopimus-id (hae-oulun-alueurakan-2014-2019-paasopimuksen-id)
        nakkilan-ramppi-id (hae-yllapitokohteen-id-nimella "Nakkilan ramppi")
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
  (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        nakkilan-ramppi-id (hae-yllapitokohteen-id-nimella "Nakkilan ramppi")
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
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            nakkilan-ramppi-id (hae-yllapitokohteen-id-nimella "Nakkilan ramppi")
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
                               :kohteet kohteet})
            integraatio-sahkopostit (hae-ulos-lahtevat-integraatiotapahtumat)]
        ;; Nakkilan ramppi merkitään valmistuneeksi ensimmäisen kerran, pitäisi lähteä maili
        (is (< 0 (count integraatio-sahkopostit)) "Sähköposti lähetettiin")))))

(deftest merkitse-tiemerkintaurakan-usea-kohde-valmiiksi
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))
        viesti-id (str (UUID/randomUUID))]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]
      (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            oulun-ohitusramppi-id (hae-yllapitokohteen-id-nimella "Oulun ohitusramppi")
            nakkilan-ramppi-id (hae-yllapitokohteen-id-nimella "Nakkilan ramppi")
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
                               :kohteet kohteet})
            integraatio-sahkopostit (hae-ulos-lahtevat-integraatiotapahtumat)]
        ;; Usea kohde merkitään valmiiksi, pitäisi lähteä maili
        (is (< 0 (count integraatio-sahkopostit)) "Sähköposti lähetettiin")))))

(deftest merkitse-tiemerkintaurakan-kohde-valmiiksi-vaaralla-urakalla
  (let [fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-muhoksen-paallystysurakan-kayttajat.xml"))]
    (with-fake-http
      [+testi-fim+ fim-vastaus]
      (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
            sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
            oulaisten-ohitusramppi-id (hae-yllapitokohteen-id-nimella "Oulaisten ohitusramppi")
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
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        oulaisten-ohitusramppi-id (hae-yllapitokohteen-id-nimella "Oulaisten ohitusramppi")
        suorittava-tiemerkintaurakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        vuosi 2017
        viesti-id (str (UUID/randomUUID))]

    (with-fake-http
      [+testi-fim+ fim-vastaus
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]

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
            muut-kohteet-testin-jalkeen (first (filter #(not= (:nimi %) "Oulaisten ohitusramppi") vastaus-kun-merkittu-valmiiksi))
            integraatio-sahkopostit (hae-ulos-lahtevat-integraatiotapahtumat)]

        (is (< 0 (count integraatio-sahkopostit)) "Sähköposti lähetettiin")

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
        (is (nil? (:aikataulu-tiemerkinta-takaraja oulaisten-ohitusramppi-testin-jalkeen)))
        (is (some? (:valmis-tiemerkintaan oulaisten-ohitusramppi-testin-jalkeen)))))))

(deftest paallystyksen-merkitseminen-valmiiksi-laskee-tiemerkinnan-takarajan-jos-merkinta-ja-jyrsinta-annettu
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        kohteen-nimi "Tärkeä kohde mt20 2022"
        kohde-id (hae-yllapitokohteen-id-nimella kohteen-nimi)
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        vuosi 2022
        viesti-id (str (UUID/randomUUID))
        tiemerkintapvm (pvm/->pvm "2.5.2022")
        ;; seuraavasta arkipäivästä + 21vrk
        oletettu-takaraja-pvm (pvm/->pvm "24.5.2022")]
    (with-fake-http
      [+testi-fim+ fim-vastaus
       {:url "http://localhost:8084/harja/api/sahkoposti/xml" :method :post} (onnistunut-sahkopostikuittaus viesti-id)]

      ;; Lisätään kohteelle ensin päällystyksen aikataulutiedot ja suorittava tiemerkintäurakka
      (let [aikataulu-ennen-testia (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :hae-yllapitourakan-aikataulu +kayttaja-jvh+
                                                   {:urakka-id urakka-id
                                                    :sopimus-id sopimus-id
                                                    :vuosi vuosi})
            kohde-ennen-testia (kohde-nimella aikataulu-ennen-testia kohteen-nimi)
            vastaus-kun-merkittu-valmiiksi (kutsu-palvelua (:http-palvelin jarjestelma)
                                                           :merkitse-kohde-valmiiksi-tiemerkintaan +kayttaja-jvh+
                                                           {:tiemerkintapvm tiemerkintapvm
                                                            :kohde-id kohde-id
                                                            :urakka-id urakka-id
                                                            :sopimus-id sopimus-id
                                                            :vuosi vuosi})
            kohde-testin-jalkeen (kohde-nimella vastaus-kun-merkittu-valmiiksi kohteen-nimi)]

        (is (nil? (:aikataulu-tiemerkinta-takaraja kohde-ennen-testia)))
        (is (nil? (:valmis-tiemerkintaan kohde-ennen-testia)))
        (is (= oletettu-takaraja-pvm (:aikataulu-tiemerkinta-takaraja kohde-testin-jalkeen)))
        (is (= tiemerkintapvm (:valmis-tiemerkintaan kohde-testin-jalkeen)))))))

(defn yllapitokohde-aikataulun-tallentamiseen [kohde-id kasin?]
  (let [takaraja (when kasin?
                   #inst "2022-03-07T22:00:00.000-00:00")
        aikataulurivin-id (ffirst (q "SELECT id FROM yllapitokohteen_aikataulu WHERE yllapitokohde = " kohde-id ";"))]
    {:tr-kaista nil, :valitavoitteet (), :kohdenumero "L15", :aikataulu-tiemerkinta-takaraja takaraja,
     :aikataulu-tiemerkinta-takaraja-kasin kasin?
     :valmis-tiemerkintaan #inst "2022-03-02T22:00:00.000-00:00", :aikataulu-kohde-alku #inst "2022-06-18T21:00:00.000-00:00", :tr-ajorata nil, :aikataulu-tiemerkinta-merkinta "muu", :aikataulu-tiemerkinta-alku nil, :tarkka-aikataulu (), :aikataulu-muokattu #inst "2022-03-01T13:19:57.000-00:00", :tr-loppuosa 2, :aikataulu-tiemerkinta-loppu nil, :aikataulu-muokkaaja 3, :aikataulu-tiemerkinta-jyrsinta "ei jyrsintää", :aikataulu-tiemerkinta-loppu-alkuperainen nil, :tr-alkuosa 2, :urakka 7, :tr-loppuetaisyys 1000, :nimi "Puolangantie", :kohdeosat [{:tr-kaista 11, :sijainti {:type :multiline, :lines [{:type :line, :points [[473846.98 7182870.803] [473960.292 7182931.312] [474183.474 7183050.716] [474330.801 7183128.039] [474404.246 7183166.586] [474452.892 7183194.074] [474453.6 7183194.541] [474489.012 7183219.83] [474518.267 7183248.087] [474546.19 7183280.333] [474567.13 7183306.317] [474574.948 7183316.489] [474650.223 7183414.291] [474660.147240582 7183426.73039112]]}]}, :tr-ajorata 0, :massamaara nil, :tr-loppuosa 2, :tr-alkuosa 2, :tr-loppuetaisyys 1000, :nimi "Puolangantien kohdeosa", :raekoko nil, :tyomenetelma nil, :paallystetyyppi nil, :id 9, :yllapitokohde-id kohde-id, :tr-alkuetaisyys 0, :tr-numero 837, :toimenpide nil}], :yllapitoluokka nil, :id aikataulurivin-id, :sopimus 37, :aikataulu-paallystys-loppu #inst "2022-06-20T21:00:00.000-00:00", :paallystysurakka "Utajärven päällystysurakka", :pituus 1000, :tr-alkuetaisyys 0, :tr-numero 837, :aikataulu-paallystys-alku #inst "2022-06-18T21:00:00.000-00:00", :sahkopostitiedot nil, :tietyoilmoitus-id nil, :aikataulu-kohde-valmis nil, :muokattu nil}))

;; testataan ns. normaalitapaus, missä ei ole arkipyhiä ja tiemerkintä voidaan aloittaa tiistaina
(deftest tiemerkinnan-takarajan-laskenta-normaalitapaus
  (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
        paallystysurakan-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        puolangantie-kohde-id (hae-yllapitokohteen-id-nimella "Puolangantie")
        vuosi 2022
        kohteet [(yllapitokohde-aikataulun-tallentamiseen puolangantie-kohde-id false)]
        kohde (first kohteet)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-yllapitokohteiden-aikataulu
                                +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :sopimus-id sopimus-id
                                 :vuosi vuosi
                                 :kohteet kohteet})
        puolangantie-vastauksessa (first (filter (fn [k]
                                                   (= "Puolangantie" (:nimi k)))
                                                 vastaus))]
    (is (= paallystysurakan-id (:urakka puolangantie-vastauksessa)) "paallystysurakan-id")
    (is (= puolangantie-kohde-id (:id kohde)) "kohde-id")
    ;; :valmis-tiemerkintaan #inst "2022-03-02T22:00:00.000-00:00" eli 3.3.
    ;; --> tähän ensin siirtymä seuraavaan arkipäivään + 14vrk eli 18.3.
    (is (= (:aikataulu-tiemerkinta-takaraja puolangantie-vastauksessa) #inst "2022-03-17T22:00:00.000-00:00") " takaraja laskettu oikein")))

(deftest tiemerkinnan-takarajan-asettaminen-kasin
  (let [urakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
        paallystysurakan-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-oulun-tiemerkintaurakan-paasopimuksen-id)
        puolangantie-kohde-id (hae-yllapitokohteen-id-nimella "Puolangantie")
        vuosi 2022
        kohteet [(yllapitokohde-aikataulun-tallentamiseen puolangantie-kohde-id true)]
        kohde (first kohteet)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-yllapitokohteiden-aikataulu
                                +kayttaja-jvh+
                                {:urakka-id urakka-id
                                 :sopimus-id sopimus-id
                                 :vuosi vuosi
                                 :kohteet kohteet})
        puolangantie-vastauksessa (first (filter (fn [k]
                                                   (= "Puolangantie" (:nimi k)))
                                                 vastaus))]
    (is (= paallystysurakan-id (:urakka puolangantie-vastauksessa)) "paallystysurakan-id")
    (is (= puolangantie-kohde-id (:id kohde)) "kohde-id")
    ;; :valmis-tiemerkintaan #inst "2022-03-02T22:00:00.000-00:00" eli 3.3.
    ;; --> tähän ensin siirtymä seuraavaan arkipäivään + 14vrk eli 18.3.
    (is (= (:aikataulu-tiemerkinta-takaraja puolangantie-vastauksessa) #inst "2022-03-07T22:00:00.000-00:00") " takaraja laskettu oikein")))

(deftest tiemerkintavalmiuden-peruminen-toimii
  (let [email-url "http://localhost:8084/harja/api/sahkoposti/xml"
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        leppajarven-ramppi-id (hae-yllapitokohde-leppajarven-ramppi-jolla-paallystysilmoitus)
        suorittava-tiemerkintaurakka-id (hae-urakan-id-nimella "Oulun tiemerkinnän palvelusopimus 2013-2022")
        fim-vastaus (slurp (io/resource "xsd/fim/esimerkit/hae-oulun-tiemerkintaurakan-kayttajat.xml"))
        vuosi 2017
        viesti-id (str (UUID/randomUUID))]

    (with-fake-http
      [+testi-fim+ fim-vastaus
       {:url email-url :method :post} (onnistunut-sahkopostikuittaus viesti-id)]

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
            muut-kohteet-testin-jalkeen (first (filter #(not= (:nimi %) "Leppäjärven ramppi") vastaus-kun-merkittu-valmiiksi))
            integraatio-sahkopostit (hae-ulos-lahtevat-integraatiotapahtumat)]

        (is (every? #(clj-str/includes? (:osoite %) email-url) integraatio-sahkopostit) "Sähköposti lähetettiin")

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
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        lapin-urakka-id (hae-urakan-id-nimella "Lapin tiemerkinnän palvelusopimus 2013-2018")
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
                                         {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
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

(deftest paallystyskohdetta-ei-saa-poistaa-kun-silla-on-tarkastus
  (let [kohde-id (ffirst (q "SELECT id FROM yllapitokohde where nimi = 'Aloittamaton kohde mt20'"))
        saako-poistaa? (first (yllapitokohteet-q/paallystyskohteen-saa-poistaa (:db jarjestelma) {:id kohde-id}))
        ;; Testataan että tarkastus palautuu arvossa false.
        odotettu {:yllapitokohde-ei-olemassa true, :yllapitokohde-lahetetty true, :tiemerkinnant-yh-toteuma true, :sanktio true, :paallystysilmoitus true, :tietyomaa true, :laatupoikkeama true, :tarkastus false}]
    (is (= odotettu saako-poistaa?) "Päällystyskohdetta ei saa poistaa")))

(deftest paallystyskohteen-saa-poistaa
  (let [kohde-id (ffirst (q "SELECT id FROM yllapitokohde where nimi = '0-ajoratainen testikohde mt20'"))
        saako-poistaa? (first (yllapitokohteet-q/paallystyskohteen-saa-poistaa (:db jarjestelma) {:id kohde-id}))
        ;; Testataan että tarkastuskin palautuu arvossa true
        odotettu {:yllapitokohde-ei-olemassa true, :yllapitokohde-lahetetty true, :tiemerkinnant-yh-toteuma true, :sanktio true, :paallystysilmoitus true, :tietyomaa true, :laatupoikkeama true, :tarkastus true}]
    (is (= odotettu saako-poistaa?) "Päällystyskohteen saa poistaa")))


(def tarkea-kohde-testidata
  {:id 27
   :sopimuksen-mukaiset-tyot 12,
   :arvonvahennykset 34
   :bitumi-indeksi 56
   :kaasuindeksi 78

   :maaramuutokset-ennustettu? false, :tila :kohde-valmis, :tr-kaista nil, :tiemerkinta-alkupvm #inst "2021-06-21T21:00:00.000-00:00", :kohdenumero "L42", :paallystys-loppupvm #inst "2021-06-20T21:00:00.000-00:00", :tr-ajorata nil, :urakka-id 7, :maaramuutokset 0, :kohde-valmispvm #inst "2021-06-23T21:00:00.000-00:00", :toteutunut-hinta nil, :tiemerkinta-loppupvm #inst "2021-06-22T21:00:00.000-00:00", :aikataulu-muokattu #inst "2022-01-25T06:15:47.000-00:00", :sakot-ja-bonukset nil, :tr-loppuosa 1, :yha-kohdenumero 116, :yllapitokohdetyyppi :paallyste, :nykyinen-paallyste nil, :tunnus nil, :lihavoi true, :tr-alkuosa 1, :urakka "Utajärven päällystysurakka"

   :yllapitokohteen-voi-poistaa? false, :tr-alkuetaisyys 1062 :tr-loppuetaisyys 3827, :nimi "Tärkeä kohde mt20",  :tr-numero 20 :yllapitokohdetyotyyppi :paallystys :kohdeosat []})

(deftest tallenna-yllapitokohteen-kustannukset
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        kohde-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        _ (println "kohde-id " kohde-id)
        alussa-ei-kustannuksia (first (q
                                         (str "SELECT * FROM yllapitokohteen_kustannukset
                                         WHERE yllapitokohde = " kohde-id";")))
        vastauksen-kohteet (:yllapitokohteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                             :tallenna-yllapitokohteet +kayttaja-jvh+ {:urakka-id urakka-id
                                                                                                       :vuosi 2021
                                                                                                       :sopimus-id sopimus-id
                                                                                                       :kohteet [tarkea-kohde-testidata]}))
        kohde (first (filter #(= kohde-id (:id %))
                             vastauksen-kohteet))]

    (let [kustannukset-tallennuksen-jalkeen (first (q
                                                     (str "SELECT * FROM yllapitokohteen_kustannukset
                                         WHERE yllapitokohde = " kohde-id";")))]

      (is (= alussa-ei-kustannuksia [(first alussa-ei-kustannuksia) kohde-id 0M 0M 0M 0M nil nil nil]))

      ;; take 8, koska emme tässä assertoi muokkausajanhetkeä
      (is (= (take 8 kustannukset-tallennuksen-jalkeen) [(first alussa-ei-kustannuksia) kohde-id 12M 34M 56M 78M nil (:id +kayttaja-jvh+)]))
      ;; assertoidaan kustannukset
      (is (= 12M (:sopimuksen-mukaiset-tyot kohde)) "Sopimuksen mukaiset työt")
      (is (= 34M (:arvonvahennykset kohde)) ":arvonvahennykset")
      (is (= 56M (:bitumi-indeksi kohde)) ":bitumi-indeksi")
      (is (= 78M (:kaasuindeksi kohde)) ":kaasuindeksi")

      (is (= (count (filter #(= (:nimi %) "Tärkeä kohde mt20") vastauksen-kohteet)) 1)))))


(def odotetut-tiedot-sahkopostilahetykseen
  {:tiemerkintaurakka-id 12, :kohde-nimi "Tärkeä kohde mt20", :tiemerkintaurakka-sampo-id "4242523-TES4", :tr-loppuosa 1, :aikataulu-tiemerkinta-loppu #inst "2021-06-22T21:00:00.000-00:00", :paallystysurakka-nimi "Utajärven päällystysurakka", :tr-alkuosa 1, :tr-loppuetaisyys 3827, :id 27, :tr-alkuetaisyys 1066, :tr-numero 20, :tiemerkintaurakka-nimi "Oulun tiemerkinnän palvelusopimus 2013-2022", :paallystysurakka-sampo-id "1337133-TES2", :paallystysurakka-id 7,
   :kaistat "11, 12" :ajoradat "1"
   :paallysteet "AB16, AN14; SMA16, AN7"
   :toimenpiteet "MPK; MPKJ"
   :sahkopostitiedot {:kopio-lahettajalle? nil, :muut-vastaanottajat #{}, :saate nil}})

(deftest yllapitokohteiden-tiedot-sahkopostilahestykseen
  (let [kohteen-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        tiedot (yllapitokohteet-q/yllapitokohteiden-tiedot-sahkopostilahetykseen
                 (:db jarjestelma)
                 [kohteen-id])]
    (is (= [odotetut-tiedot-sahkopostilahetykseen] tiedot) "Sähköpostilähetyksen tiedot OK")))

(deftest yllapitokohteiden-tiedot-sahkopostilahestykseen-jos-alikohteet-poistettu
  ;; poistettujen alikohteiden kaistoja, ajorataa, toimenpiteitä ja päällystettä ei saa nostaa sähköpostiin
  (let [kohteen-id (hae-yllapitokohteen-id-nimella "Tärkeä kohde mt20")
        poista-alikohteet (u (str "UPDATE yllapitokohdeosa SET poistettu = true WHERE yllapitokohde = " kohteen-id))
        tiedot (yllapitokohteet-q/yllapitokohteiden-tiedot-sahkopostilahetykseen
                 (:db jarjestelma)
                 [kohteen-id])]
    (is (= [(assoc odotetut-tiedot-sahkopostilahetykseen
              :ajoradat nil
              :kaistat nil
              :toimenpiteet nil
              :paallysteet nil)] tiedot)
        "Sähköpostilähetyksen tiedot OK, eli eivät sisällä poistettujen alikohteiden tietoja.")))

(def excel-rivit-utajarvi-2022
  (list {:lihavoi? false
         :rivi [13374
                "L14"
                nil
                "Ouluntie 2"
                0M
                [:varillinen-teksti
                 {:arvo 0
                  :fmt :raha
                  :tyyli :disabled}]
                0M
                0M
                [:kaava
                 {:alkusarake "E"
                  :kaava :summaa-vieressaolevat
                  :loppusarake "H"}]]}
        {:lihavoi? false
         :rivi [13375
                "L15"
                "A"
                "Puolangantie"
                400M
                [:varillinen-teksti
                 {:arvo 20M
                  :fmt :raha
                  :tyyli :disabled}]
                4543.95M
                0M
                [:kaava
                 {:alkusarake "E"
                  :kaava :summaa-vieressaolevat
                  :loppusarake "H"}]]}
        {:lihavoi? false
         :rivi [547523069
                "L42"
                "B"
                "Tärkeä kohde mt20 2022"
                0M
                [:varillinen-teksti
                 {:arvo 0
                  :fmt :raha
                  :tyyli :disabled}]
                0M
                0M
                [:kaava
                 {:alkusarake "E"
                  :kaava :summaa-vieressaolevat
                  :loppusarake "H"}]]}
        [nil nil nil nil nil nil nil nil nil]
        [nil nil nil nil nil nil nil nil nil]
        [nil
         nil
         nil
         "Yhteensä:"
         [:kaava
          {:alkurivi 5
           :kaava :summaa-yllaolevat
           :loppurivi 7}]
         [:kaava
          {:alkurivi 5
           :kaava :summaa-yllaolevat
           :loppurivi 7}]
         [:kaava
          {:alkurivi 5
           :kaava :summaa-yllaolevat
           :loppurivi 7}]
         [:kaava
          {:alkurivi 5
           :kaava :summaa-yllaolevat
           :loppurivi 7}]
         [:kaava
          {:alkurivi 5
           :kaava :summaa-yllaolevat
           :loppurivi 7}]]))

(deftest muodosta-paallystysexcelin-kohteiden-rivit
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        sopimus-id (hae-utajarven-paallystysurakan-paasopimuksen-id)
        vuosi (pvm/vuosi (pvm/nyt))
        kohteet (yllapitokohteet-domain/jarjesta-yllapitokohteet
                  (hae-urakan-yllapitokohteet (:db jarjestelma)
                                              +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :vuosi vuosi}))
        excelin-kohderivit (paallystyskohteet-excel/muodosta-excelrivit kohteet vuosi)]
    (is (= excel-rivit-utajarvi-2022 excelin-kohderivit) "Päällystyskohteiden kustannusten excelrivien muodostus")))

(def excel-rivit-muhos-2017
  (list
    {:lihavoi? false
     :rivi [1233534
            "L03"
            nil
            "Leppäjärven ramppi"
            400M
            [:varillinen-teksti
             {:arvo 205M
              :fmt :raha
              :tyyli :disabled}]
            100M
            2000M
            4543.95M
            0M
            [:kaava
             {:alkusarake "E"
              :kaava :summaa-vieressaolevat
              :loppusarake "J"}]]}
    {:lihavoi? false
     :rivi [54523243
            "308a"
            nil
            "Oulun ohitusramppi"
            9000M
            [:varillinen-teksti
             {:arvo 0
              :fmt :raha
              :tyyli :disabled}]
            200M
            nil
            565M
            100M
            [:kaava
             {:alkusarake "E"
              :kaava :summaa-vieressaolevat
              :loppusarake "J"}]]}
    {:lihavoi? false
     :rivi [456896958
            "310"
            nil
            "Oulaisten ohitusramppi"
            500M
            [:varillinen-teksti
             {:arvo 0
              :fmt :raha
              :tyyli :disabled}]
            3457M
            -3000M
            5M
            6M
            [:kaava
             {:alkusarake "E"
              :kaava :summaa-vieressaolevat
              :loppusarake "J"}]]}
    {:lihavoi? false
     :rivi [456896959
            "666"
            nil
            "Kuusamontien testi"
            500M
            [:varillinen-teksti
             {:arvo 0
              :fmt :raha
              :tyyli :disabled}]
            3457M
            nil
            5M
            6M
            [:kaava
             {:alkusarake "E"
              :kaava :summaa-vieressaolevat
              :loppusarake "J"}]]}
    [nil nil nil nil nil nil nil nil nil nil nil]
    [nil nil nil nil nil nil nil nil nil nil nil]
    [nil nil nil
     "Yhteensä:"
     [:kaava
      {:alkurivi 5
       :kaava :summaa-yllaolevat
       :loppurivi 8}]
     [:kaava
      {:alkurivi 5
       :kaava :summaa-yllaolevat
       :loppurivi 8}]
     [:kaava
      {:alkurivi 5
       :kaava :summaa-yllaolevat
       :loppurivi 8}]
     [:kaava
      {:alkurivi 5
       :kaava :summaa-yllaolevat
       :loppurivi 8}]
     [:kaava
      {:alkurivi 5
       :kaava :summaa-yllaolevat
       :loppurivi 8}]
     [:kaava
      {:alkurivi 5
       :kaava :summaa-yllaolevat
       :loppurivi 8}]
     [:kaava
      {:alkurivi 5
       :kaava :summaa-yllaolevat
       :loppurivi 8}]]))

(deftest muodosta-paallystysexcelin-kohteiden-rivit-muhos-2017-vain-yha-kohteet
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        sopimus-id (hae-muhoksen-paallystysurakan-paasopimuksen-id)
        vuosi 2017
        kohteet (yllapitokohteet-domain/jarjesta-yllapitokohteet
                  (hae-urakan-yllapitokohteet (:db jarjestelma)
                                              +kayttaja-jvh+
                                              {:urakka-id urakka-id
                                               :sopimus-id sopimus-id
                                               :vuosi vuosi
                                               :vain-yha-kohteet? true}))
        excelin-kohderivit (paallystyskohteet-excel/muodosta-excelrivit kohteet vuosi)]
    (is (= excel-rivit-muhos-2017 excelin-kohderivit) "Päällystyskohteiden kustannusten excelrivien muodostus hakee vain YHA-kohteet")))

(defn hae-yllapitokohteen-kustannukset  [yhaid]
  (first (q "SELECT * FROM yllapitokohteen_kustannukset WHERE yllapitokohde = (SELECT id FROM yllapitokohde WHERE yhaid = "yhaid ");")))

(deftest yllapitokohteen-tallennus-yha-idlla
  (let [db (luo-testitietokanta)
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        yhaid 527523069
        user-id (ffirst (q "SELECT id FROM kayttaja where kayttajanimi = 'jvh'"))
        kohteen-kustannukset-ennen (hae-yllapitokohteen-kustannukset yhaid)
        toteutunut-hinta nil ;; vain paikkauskohteille muuta kuin nil
        kohde {:yhaid yhaid
               :urakka urakka-id
               :sopimuksen_mukaiset_tyot 1
               :bitumi_indeksi 2
               :kaasuindeksi 3
               :muokkaaja user-id}
        _ (yllapitokohteet-q/tallenna-yllapitokohteen-kustannukset-yhaid! db kohde)
        kohteen-kustannukset-jalkeen (hae-yllapitokohteen-kustannukset yhaid)]
    (is (= (butlast kohteen-kustannukset-ennen) [15 27 0M 0M 0M 0M toteutunut-hinta nil]) "kustannukset ennen tallennusta")
    (is (= (butlast kohteen-kustannukset-jalkeen) [15 27 1M 0M 2M 3M toteutunut-hinta user-id]) "kustannukset ennen tallennuksen jälkeen")))
