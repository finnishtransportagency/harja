(ns harja.palvelin.palvelut.yllapitokohteet.materiaalikirjasto-test
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
            [harja.jms-test :refer [feikki-jms]]
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
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :itmf (feikki-jms "itmf")
                        :sonja (feikki-jms "sonja")
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

(defn- siivoa-muuttuvat [testi-col]
  (do
    (dissoc (into {} testi-col) :id ::m/muokattu ::m/luotu)))

(def lisaaine-default1
  {1 (merge
       {:valittu? true}
       (namespacefy
         {:pitoisuus 1.5M}
         {:ns :lisaaine}))})

(def sideaine-default1
  {:lopputuote {:aineet {0 (namespacefy
                             {:tyyppi "20/30"
                              :pitoisuus 50.1M
                              :lopputuote? true}
                             {:ns :sideaine})}}}) ;; lopputuote / lisätty

(def sideaine-default2
  {:lopputuote (merge
                 {:valittu? true}
                 {:aineet {0 (namespacefy
                               {:tyyppi 1
                                :pitoisuus 10.56M
                                :lopputuote? true}
                               {:ns :sideaine})}})

   :lisatty {:aineet {0 (merge
                          {:valittu? true}
                          (namespacefy
                            {:tyyppi 2
                             :pitoisuus 10.4M
                             :lopputuote? false}
                            {:ns :sideaine}))}}}) ;; lopputuote / lisatty

(def runkoaine-kiviaines-default1
  {1 (merge
       {:valittu? true}
       (namespacefy {:esiintyma "Zatelliitti"
                     :kuulamyllyarvo 12.1M
                     :litteysluku 4.1M
                     :massaprosentti 34}
                    {:ns :runkoaine}))})

(def urakan-testimassa
  {::pot2-domain/urakka-id (hae-utajarven-paallystysurakan-id)
   ::pot2-domain/nimen-tarkenne "Tarkenne"
   ::pot2-domain/tyyppi (:koodi (first paallystys-ja-paikkaus-domain/+paallystetyypit+)) ;; Harjan vanhassa kielenkäytössä nämä on päällystetyyppejä
   ::pot2-domain/max-raekoko (first pot2-domain/massan-max-raekoko)
   ::pot2-domain/kuulamyllyluokka (:nimi (first paallystysilmoitus-domain/+kuulamyllyt+))
   ::pot2-domain/litteyslukuluokka 1
   ::pot2-domain/dop-nro "12345abc"
   ::pot2-domain/runkoaineet runkoaine-kiviaines-default1
   ::pot2-domain/sideaineet sideaine-default2
   ::pot2-domain/lisaaineet lisaaine-default1})

(defn odotettu-massa [ylikirjoitettavat]
  (merge
    {:harja.domain.pot2/dop-nro "12345abc"
     :harja.domain.pot2/kuulamyllyluokka "AN5"
     :harja.domain.pot2/lisaaineet {1 {:lisaaine/pitoisuus 1.5M
                                       :valittu? true}}
     :harja.domain.pot2/litteyslukuluokka 1
     :harja.domain.pot2/massa-id 3
     :harja.domain.pot2/max-raekoko 5
     :harja.domain.pot2/nimen-tarkenne "Tarkenne"
     :harja.domain.pot2/runkoaineet {1 {:runkoaine/esiintyma "Zatelliitti"
                                        :runkoaine/kuulamyllyarvo 12.1M
                                        :runkoaine/litteysluku 4.1M
                                        :runkoaine/massaprosentti 34
                                        :valittu? true}}
     :harja.domain.pot2/sideaineet {:lisatty {:aineet {0 {:sideaine/lopputuote? false
                                                          :sideaine/pitoisuus 10.4M
                                                          :sideaine/tyyppi 2
                                                          :valittu? true}}}
                                    :lopputuote {:aineet {0 #:sideaine{:lopputuote? true
                                                                       :pitoisuus 10.56M
                                                                       :tyyppi 1}}
                                                 :valittu? true}}
     :harja.domain.pot2/tyyppi 1
     :harja.domain.pot2/urakka-id 7}
    ylikirjoitettavat))


;; ;; MASSOJEN TESTIT
(deftest vaaran-urakan-urakoitsija-ei-saa-lisata-massaa-test
  (is (thrown? Exception
               (kutsu-palvelua (:http-palvelin jarjestelma)
                               :tallenna-urakan-massa
                               +kayttaja-yit_uuvh+ (dissoc urakan-testimassa ::pot2-domain/massa-id)))))

(deftest lisaa-massa-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-massa
                                +kayttaja-jvh+ (dissoc urakan-testimassa ::pot2-domain/massa-id))
        uusi-id (ffirst (q " SELECT max (id) FROM pot2_mk_urakan_massa;"))
        oletus-vastaus (odotettu-massa {:harja.domain.pot2/massa-id uusi-id
                                        :harja.domain.muokkaustiedot/luoja-id (ffirst (q "SELECT id FROM kayttaja where kayttajanimi = 'jvh'"))})]
    (is (= oletus-vastaus (siivoa-muuttuvat vastaus)) "Tallennettu massa")
    (is (= (siivoa-muuttuvat (:harja.domain.pot2/lisaaineet vastaus)) (siivoa-muuttuvat (:harja.domain.pot2/lisaaineet oletus-vastaus))))
    (is (= (siivoa-muuttuvat (:harja.domain.pot2/runkoaineet vastaus)) (siivoa-muuttuvat (:harja.domain.pot2/runkoaineet oletus-vastaus))))
    (is (= (siivoa-muuttuvat (:harja.domain.pot2/sideaineet vastaus)) (siivoa-muuttuvat (:harja.domain.pot2/sideaineet oletus-vastaus))))))

(deftest paivita-massa-test
  (let [paivitettavan-id (ffirst (q " SELECT (id) FROM pot2_mk_urakan_massa where dop_nro = '1234567';"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-massa
                                +kayttaja-jvh+ (assoc urakan-testimassa
                                                 ::pot2-domain/massa-id paivitettavan-id))
        oletus-vastaus (odotettu-massa {:harja.domain.muokkaustiedot/muokkaaja-id 3
                                        ::pot2-domain/massa-id paivitettavan-id
                                        ::pot2-domain/poistettu? false})]
    (is (= oletus-vastaus (siivoa-muuttuvat vastaus)) "Tallennettu massa")
    (is (= (siivoa-muuttuvat (:harja.domain.pot2/lisaaineet vastaus)) (siivoa-muuttuvat (:harja.domain.pot2/lisaaineet oletus-vastaus))))
    (is (= (siivoa-muuttuvat (:harja.domain.pot2/runkoaineet vastaus)) (siivoa-muuttuvat (:harja.domain.pot2/runkoaineet oletus-vastaus))))
    (is (= (siivoa-muuttuvat (:harja.domain.pot2/sideaineet vastaus)) (siivoa-muuttuvat (:harja.domain.pot2/sideaineet oletus-vastaus))))))


;; MURSKEIDEN TESTIT
(def urakan-testimurske
  #:harja.domain.pot2{:esiintyma "Kankkulan Kaivo 2", :nimen-tarkenne "LJYR", :iskunkestavyys "LA35", :tyyppi 1, :rakeisuus "0/56", :dop-nro "1234567-dope", :murske-id 1 :urakka-id (hae-utajarven-paallystysurakan-id)})

(defn- tallenna-murske-fn
  [http-palvelin payload]
  (kutsu-palvelua http-palvelin :tallenna-urakan-murske +kayttaja-jvh+ payload))

(defn- murkeen-oletusvastaus [ylikirjoitettavat]
  (merge
    {:harja.domain.muokkaustiedot/muokkaaja-id 3
     :harja.domain.pot2/dop-nro "1234567-dope"
     :harja.domain.pot2/esiintyma "Kankkulan Kaivo 2"
     :harja.domain.pot2/iskunkestavyys "LA35"
     :harja.domain.pot2/murske-id 1
     :harja.domain.pot2/nimen-tarkenne "LJYR"
     :harja.domain.pot2/poistettu? false
     :harja.domain.pot2/rakeisuus "0/56"
     :harja.domain.pot2/tyyppi 1
     :harja.domain.pot2/urakka-id 7}
    ylikirjoitettavat))

(deftest paivita-murske-test
  (let [vastaus (tallenna-murske-fn (:http-palvelin jarjestelma) urakan-testimurske)
        oletus-vastaus (murkeen-oletusvastaus {})]
    (is (= oletus-vastaus (siivoa-muuttuvat vastaus)) "murskeen vastaus")))

(deftest murskeen-tyypin-tarkenne-puuttuu-test
  (let [muun-tyypin-id (ffirst (q " SELECT (koodi) FROM pot2_mk_mursketyyppi where nimi = 'Muu';"))]
    (is (thrown? AssertionError
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :tallenna-urakan-murske
                                 +kayttaja-jvh+ (assoc urakan-testimurske ::pot2-domain/tyyppi muun-tyypin-id
                                                                          ::pot2-domain/tyyppi-tarkenne nil))))))

(deftest murskeen-tyypin-tarkenne-on-test
  (let [muun-tyypin-id (ffirst (q " SELECT (koodi) FROM pot2_mk_mursketyyppi where nimi = 'Muu';"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-murske
                                +kayttaja-jvh+ (assoc urakan-testimurske ::pot2-domain/tyyppi muun-tyypin-id
                                                                         ::pot2-domain/tyyppi-tarkenne "Validi tarkenne"))
        odotettu-vastaus {:harja.domain.muokkaustiedot/muokkaaja-id 3
                          :harja.domain.pot2/murske-id 1
                          :harja.domain.pot2/nimen-tarkenne "LJYR"
                          :harja.domain.pot2/poistettu? false
                          :harja.domain.pot2/tyyppi muun-tyypin-id
                          :harja.domain.pot2/tyyppi-tarkenne "Validi tarkenne"
                          :harja.domain.pot2/urakka-id 7}]
    (is (= odotettu-vastaus (siivoa-muuttuvat vastaus)))))

(defn testimurske-muu [muun-tyypin-id urakka-id]
  {:harja.domain.pot2/nimen-tarkenne "Nimen tarkenne"
   :harja.domain.pot2/tyyppi muun-tyypin-id
   :harja.domain.pot2/tyyppi-tarkenne "Tyypin tarkenne"
   :harja.domain.pot2/lahde "Lähde"
   :harja.domain.pot2/poistettu? false
   :harja.domain.pot2/urakka-id urakka-id})

(deftest muun-tyyppisen-murskeen-insert-test
  (let [muun-tyypin-id (ffirst (q " SELECT koodi FROM pot2_mk_mursketyyppi where nimi = 'Muu';"))
        urakka-id (hae-utajarven-paallystysurakan-id)
        uusi-id (+ 1 (ffirst (q " SELECT max (id) FROM pot2_mk_urakan_murske;")))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-murske
                                +kayttaja-jvh+ (testimurske-muu muun-tyypin-id
                                                                urakka-id))
        odotettu-vastaus {:harja.domain.muokkaustiedot/luoja-id (:id +kayttaja-jvh+)
                          :harja.domain.pot2/nimen-tarkenne "Nimen tarkenne"
                          :harja.domain.pot2/tyyppi muun-tyypin-id
                          :harja.domain.pot2/tyyppi-tarkenne "Tyypin tarkenne"
                          :harja.domain.pot2/lahde "Lähde"
                          ::pot2-domain/murske-id uusi-id
                          :harja.domain.pot2/urakka-id urakka-id}]
    (is (= odotettu-vastaus (siivoa-muuttuvat vastaus)))
    (is (every? #(contains? vastaus %) pot2-domain/murskesarakkeet-muu))))

(deftest murskeen-rakeisuuden-tarkenne-puuttuu-test
  (is (thrown? AssertionError
               (kutsu-palvelua (:http-palvelin jarjestelma)
                               :tallenna-urakan-murske
                               +kayttaja-jvh+ (assoc urakan-testimurske ::pot2-domain/rakeisuus "Muu"
                                                                        ::pot2-domain/rakeisuus-tarkenne nil)))))

(deftest murskeen-rakeisuuden-tarkenne-on-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-murske
                                +kayttaja-jvh+ (assoc urakan-testimurske ::pot2-domain/rakeisuus "Muu"
                                                                         ::pot2-domain/rakeisuus-tarkenne "Rakeisuuden tarkenne"))
        odotettu-vastaus (murkeen-oletusvastaus {::pot2-domain/rakeisuus "Muu"
                                                 ::pot2-domain/rakeisuus-tarkenne "Rakeisuuden tarkenne"})]
    (is (= odotettu-vastaus (siivoa-muuttuvat vastaus)))))

(deftest vaaran-urakan-urakoitsija-ei-saa-lisata-mursketta-test
  (is (thrown? Exception
               (kutsu-palvelua (:http-palvelin jarjestelma)
                               :tallenna-urakan-murske
                               +kayttaja-yit_uuvh+ (dissoc urakan-testimurske ::pot2-domain/murske-id)))))

(deftest lisaa-murske-test
  (let [vastaus (tallenna-murske-fn (:http-palvelin jarjestelma) (-> urakan-testimurske
                                                                     (dissoc ::pot2-domain/murske-id)
                                                                     (assoc ::pot2-domain/esiintyma "Hoppilan hyppyri")))
        uusi-id (ffirst (q " SELECT max (id) FROM pot2_mk_urakan_murske;"))
        oletus-vastaus {:harja.domain.muokkaustiedot/luoja-id 3
                        :harja.domain.pot2/dop-nro "1234567-dope"
                        :harja.domain.pot2/esiintyma "Hoppilan hyppyri"
                        :harja.domain.pot2/iskunkestavyys "LA35"
                        :harja.domain.pot2/murske-id uusi-id
                        :harja.domain.pot2/nimen-tarkenne "LJYR"
                        :harja.domain.pot2/rakeisuus "0/56"
                        :harja.domain.pot2/tyyppi 1
                        :harja.domain.pot2/urakka-id 7}]
    (is (= oletus-vastaus (siivoa-muuttuvat vastaus)) "murskeen vastaus")))

(deftest tallenna-uusi-murske-test-epavalidi-iskunkestavyys
  (is (thrown? AssertionError
               (tallenna-murske-fn (:http-palvelin jarjestelma) (merge urakan-testimurske
                                                                       {::pot2-domain/iskunkestavyys "EPÄVALIDI"})))))

(deftest tallenna-uusi-murske-test-epavalidi-rakeisuus
  (is (thrown? AssertionError
               (tallenna-murske-fn (:http-palvelin jarjestelma) (merge urakan-testimurske
                                                                       {::pot2-domain/rakeisuus "0/666"})))))

(deftest hae-urakan-pot2-massat
  (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-urakan-massa
                          +kayttaja-jvh+ urakan-testimassa)
        {massat :massat murskeet :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakan-massat-ja-murskeet
                        +kayttaja-jvh+ {:urakka-id (hae-utajarven-paallystysurakan-id)})
        oletetut-massat '({:harja.domain.pot2/dop-nro "1234567"
                           :harja.domain.pot2/kuulamyllyluokka "AN14"
                           :harja.domain.pot2/lisaaineet ({:lisaaine/id 1
                                                           :lisaaine/pitoisuus 0.5M
                                                           :lisaaine/tyyppi 2
                                                           ::pot2-domain/massa-id 1})
                           :harja.domain.pot2/litteyslukuluokka 1
                           :harja.domain.pot2/max-raekoko 16
                           :harja.domain.pot2/runkoaineet [{::pot2-domain/massa-id 1
                                                            :runkoaine/esiintyma "Kaiskakallio"
                                                            :runkoaine/id 1
                                                            :runkoaine/kuulamyllyarvo 10.0M
                                                            :runkoaine/kuvaus "Kelpo runkoaine tämä."
                                                            :runkoaine/litteysluku 9.5M
                                                            :runkoaine/massaprosentti 52
                                                            :runkoaine/tyyppi 1}]
                           :harja.domain.pot2/sideaineet ({::pot2-domain/massa-id 1
                                                           :sideaine/id 1
                                                           :sideaine/lopputuote? true
                                                           :sideaine/pitoisuus 4.8M
                                                           :sideaine/tyyppi 6})
                           :harja.domain.pot2/tyyppi 12
                           ::pot2-domain/massa-id 1}
                          {:harja.domain.pot2/dop-nro "987654331-2"
                           :harja.domain.pot2/kuulamyllyluokka "AN7"
                           :harja.domain.pot2/lisaaineet ({:lisaaine/id 2
                                                           :lisaaine/pitoisuus 0.5M
                                                           :lisaaine/tyyppi 1
                                                           ::pot2-domain/massa-id 2})
                           :harja.domain.pot2/litteyslukuluokka 2
                           :harja.domain.pot2/max-raekoko 16
                           :harja.domain.pot2/runkoaineet [{::pot2-domain/massa-id 2
                                                            :runkoaine/esiintyma "Sammalkallio"
                                                            :runkoaine/id 2
                                                            :runkoaine/kuulamyllyarvo 9.2M
                                                            :runkoaine/kuvaus "Jämäkkä runkoaine."
                                                            :runkoaine/litteysluku 6.5M
                                                            :runkoaine/massaprosentti 85
                                                            :runkoaine/tyyppi 1}
                                                           {::pot2-domain/massa-id 2
                                                            :runkoaine/esiintyma "Sammalkallio"
                                                            :runkoaine/fillerityyppi "Kalkkifilleri (KF)"
                                                            :runkoaine/id 3
                                                            :runkoaine/kuvaus "Oiva Filleri."
                                                            :runkoaine/massaprosentti 3
                                                            :runkoaine/tyyppi 3}
                                                           {::pot2-domain/massa-id 2
                                                            :runkoaine/esiintyma "Sammalkallio"
                                                            :runkoaine/id 4
                                                            :runkoaine/kuulamyllyarvo 11.2M
                                                            :runkoaine/kuvaus "Rouhea aine."
                                                            :runkoaine/litteysluku 4.5M
                                                            :runkoaine/massaprosentti 5
                                                            :runkoaine/tyyppi 2}]
                           :harja.domain.pot2/sideaineet ({::pot2-domain/massa-id 2
                                                           :sideaine/id 2
                                                           :sideaine/lopputuote? true
                                                           :sideaine/pitoisuus 5.5M
                                                           :sideaine/tyyppi 5})
                           :harja.domain.pot2/tyyppi 14
                           ::pot2-domain/massa-id 2}
                          {:harja.domain.pot2/dop-nro "12345abc"
                           :harja.domain.pot2/kuulamyllyluokka "AN5"
                           :harja.domain.pot2/lisaaineet ({:lisaaine/id 3
                                                           :lisaaine/pitoisuus 1.5M
                                                           :lisaaine/tyyppi 1
                                                           ::pot2-domain/massa-id 3})
                           :harja.domain.pot2/litteyslukuluokka 1
                           :harja.domain.pot2/max-raekoko 5
                           :harja.domain.pot2/nimen-tarkenne "Tarkenne"
                           :harja.domain.pot2/runkoaineet [{::pot2-domain/massa-id 3
                                                            :runkoaine/esiintyma "Zatelliitti"
                                                            :runkoaine/id 5
                                                            :runkoaine/kuulamyllyarvo 12.1M
                                                            :runkoaine/litteysluku 4.1M
                                                            :runkoaine/massaprosentti 34
                                                            :runkoaine/tyyppi 1}]
                           :harja.domain.pot2/sideaineet ({::pot2-domain/massa-id 3
                                                           :sideaine/id 3
                                                           :sideaine/lopputuote? true
                                                           :sideaine/pitoisuus 10.6M
                                                           :sideaine/tyyppi 1})
                           :harja.domain.pot2/tyyppi 1
                           ::pot2-domain/massa-id 3})
        oletetut-murskeet '(#:harja.domain.pot2{:esiintyma "Kankkulan Kaivo", :nimen-tarkenne "LJYR", :iskunkestavyys "LA30", :tyyppi 1, :rakeisuus "0/40", :dop-nro "1234567-dop", :murske-id 1})]
    (is (= massat oletetut-massat))
    (is (= murskeet oletetut-murskeet))))

(deftest hae-pot2-koodistot-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-pot2-koodistot +kayttaja-jvh+ {})
        odotetut-avaimet #{:massatyypit
                           :mursketyypit
                           :runkoainetyypit
                           :sideainetyypit
                           :lisaainetyypit
                           :alusta-toimenpiteet
                           :paallystekerros-toimenpiteet
                           :verkon-sijainnit
                           :verkon-tarkoitukset
                           :verkon-tyypit}]
    (is (every? #(contains? vastaus %) odotetut-avaimet))
    (is (= (some #(= :harja.domain.pot2 {:nimi "Kovat asfalttibetonit", :lyhenne "Kovat asfalttibetonit", :koodi 10} %)
                 (:massatyypit vastaus))))
    (is (= (some #(= :harja.domain.pot2 {:nimi " Kalliomurske", :lyhenne "KaM", :koodi 1} %)
                 (:mursketyypit vastaus))))
    (is (= (some #(= :harja.domain.pot2{:nimi "Kiviaines", :lyhenne "Kiviaines", :koodi 1} %)
                 (:runkoainetyypit vastaus))))
    (is (= (some #(= :harja.domain.pot2 {:nimi "Bitumi, 20/30", :lyhenne " Bitumi, 20/30", :koodi 1} %)
                 (:sideainetyypit vastaus))))
    (is (= (some #(= :harja.domain.pot2 {:nimi "Kuitu", :lyhenne "Kuitu", :koodi 1}  %)
                 (:lisaainetyypit vastaus))))
    (is (= (some #(= :harja.domain.pot2{:nimi "Massanvaihto", :lyhenne "MV", :koodi 1}  %)
                 (:alusta-toimenpiteet vastaus))))
    (is (= (some #(= :harja.domain.pot2 {:nimi "Paksuudeltaan vakio laatta", :lyhenne "LTA", :koodi 12}  %)
                 (:paallystekerros-toimenpiteet vastaus))))


    (is (= (count (:massatyypit vastaus)) (ffirst (q "SELECT count(*) FROM pot2_mk_massatyyppi"))))
    (is (= (count (:mursketyypit vastaus)) (ffirst (q "SELECT count(*) FROM pot2_mk_mursketyyppi"))))
    (is (= (count (:runkoainetyypit vastaus)) (ffirst (q "SELECT count(*) FROM pot2_mk_runkoainetyyppi"))))
    (is (= (count (:sideainetyypit vastaus)) (ffirst (q "SELECT count(*) FROM pot2_mk_sideainetyyppi"))))
    (is (= (count (:lisaainetyypit vastaus)) (ffirst (q "SELECT count(*) FROM pot2_mk_lisaainetyyppi"))))
    (is (= (count (:alusta-toimenpiteet vastaus)) (ffirst (q "SELECT count(*) FROM pot2_mk_alusta_toimenpide"))))
    (is (= (count (:paallystekerros-toimenpiteet vastaus)) (ffirst (q "SELECT count(*) FROM pot2_mk_kulutuskerros_toimenpide"))))
    (is (= (count (:verkon-sijainnit vastaus)) (ffirst (q "SELECT count(*) FROM pot2_verkon_sijainti"))))
    (is (= (count (:verkon-tarkoitukset vastaus)) (ffirst (q "SELECT count(*) FROM pot2_verkon_tarkoitus"))))
    (is (= (count (:verkon-tyypit vastaus)) (ffirst (q "SELECT count(*) FROM pot2_verkon_tyyppi"))))
    )

  )