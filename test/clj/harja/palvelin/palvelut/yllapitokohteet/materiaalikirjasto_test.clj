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
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
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
                        :api-sahkoposti (component/using
                                          (sahkoposti-api/->ApiSahkoposti {:tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
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
                     :massaprosentti 34.0M}
                    {:ns :runkoaine}))})

(def urakan-testimassa
  {::pot2-domain/urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
   ::pot2-domain/nimen-tarkenne "Tarkenne"
   ::pot2-domain/tyyppi (:koodi (first paallystys-ja-paikkaus-domain/+paallystetyypit+)) ;; Harjan vanhassa kielenkäytössä nämä on päällystetyyppejä
   ::pot2-domain/max-raekoko (first pot2-domain/massan-max-raekoko)
   ::pot2-domain/kuulamyllyluokka (:nimi (first paallystysilmoitus-domain/+kuulamyllyt+))
   ::pot2-domain/litteyslukuluokka "FI15"
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
     :harja.domain.pot2/litteyslukuluokka "FI15"
     :harja.domain.pot2/massa-id 3
     :harja.domain.pot2/max-raekoko 5
     :harja.domain.pot2/nimen-tarkenne "Tarkenne"
     :harja.domain.pot2/runkoaineet {1 {:runkoaine/esiintyma "Zatelliitti"
                                        :runkoaine/kuulamyllyarvo 12.1M
                                        :runkoaine/litteysluku 4.1M
                                        :runkoaine/massaprosentti 34.0M
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
  #:harja.domain.pot2{:esiintyma "Kankkulan Kaivo 2", :nimen-tarkenne "LJYR", :iskunkestavyys "LA35", :tyyppi 1, :rakeisuus "0/56", :dop-nro "1234567-dope", :murske-id 1 :urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")})

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
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
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

(def oletetut-massat-utajarvi '({:harja.domain.pot2/dop-nro "1234567"
                                 :harja.domain.pot2/kaytossa ({:kohdenumero "L42"
                                                               :kohteiden-lkm 1
                                                               :nimi "Tärkeä kohde mt20"
                                                               :rivityyppi "paallyste"
                                                               :tila "aloitettu"}
                                                              {:kohdenumero "L42"
                                                               :kohteiden-lkm 2
                                                               :nimi "Tärkeä kohde mt20"
                                                               :rivityyppi "alusta"
                                                               :tila "aloitettu,aloitettu"})
                                 :harja.domain.pot2/kuulamyllyluokka "AN14"
                                 :harja.domain.pot2/lisaaineet ({:lisaaine/id 1
                                                                 :lisaaine/pitoisuus 0.5M
                                                                 :lisaaine/tyyppi 2
                                                                 ::pot2-domain/massa-id 1})
                                 :harja.domain.pot2/litteyslukuluokka "FI15"
                                 :harja.domain.pot2/max-raekoko 16
                                 :harja.domain.pot2/runkoaineet [{::pot2-domain/massa-id 1
                                                                  :runkoaine/esiintyma "Kaiskakallio"
                                                                  :runkoaine/id 1
                                                                  :runkoaine/kuulamyllyarvo 10.0M
                                                                  :runkoaine/kuvaus "Kelpo runkoaine tämä."
                                                                  :runkoaine/litteysluku 9.5M
                                                                  :runkoaine/massaprosentti 52.0M
                                                                  :runkoaine/tyyppi 1}]
                                 :harja.domain.pot2/sideaineet ({::pot2-domain/massa-id 1
                                                                 :sideaine/id 1
                                                                 :sideaine/lopputuote? true
                                                                 :sideaine/pitoisuus 4.8M
                                                                 :sideaine/tyyppi 6})
                                 :harja.domain.pot2/tyyppi 12
                                 ::pot2-domain/massa-id 1}
                                {:harja.domain.pot2/dop-nro "987654331-2"
                                 :harja.domain.pot2/kaytossa ({:kohdenumero "L42"
                                                               :kohteiden-lkm 1
                                                               :nimi "Tärkeä kohde mt20"
                                                               :rivityyppi "paallyste"
                                                               :tila "aloitettu"})
                                 :harja.domain.pot2/kuulamyllyluokka "AN7"
                                 :harja.domain.pot2/lisaaineet ({:lisaaine/id 2
                                                                 :lisaaine/pitoisuus 0.5M
                                                                 :lisaaine/tyyppi 1
                                                                 ::pot2-domain/massa-id 2})
                                 :harja.domain.pot2/litteyslukuluokka "FI20"
                                 :harja.domain.pot2/max-raekoko 16
                                 :harja.domain.pot2/runkoaineet [{::pot2-domain/massa-id 2
                                                                  :runkoaine/esiintyma "Sammalkallio"
                                                                  :runkoaine/id 2
                                                                  :runkoaine/kuulamyllyarvo 9.2M
                                                                  :runkoaine/kuvaus "Jämäkkä runkoaine."
                                                                  :runkoaine/litteysluku 6.5M
                                                                  :runkoaine/massaprosentti 85.0M
                                                                  :runkoaine/tyyppi 1}
                                                                 {::pot2-domain/massa-id 2
                                                                  :runkoaine/esiintyma "Sammalkallio"
                                                                  :runkoaine/fillerityyppi "Kalkkifilleri (KF)"
                                                                  :runkoaine/id 3
                                                                  :runkoaine/kuvaus "Oiva Filleri."
                                                                  :runkoaine/massaprosentti 3.0M
                                                                  :runkoaine/tyyppi 3}
                                                                 {::pot2-domain/massa-id 2
                                                                  :runkoaine/esiintyma "Sammalkallio"
                                                                  :runkoaine/id 4
                                                                  :runkoaine/kuulamyllyarvo 11.2M
                                                                  :runkoaine/kuvaus "Rouhea aine."
                                                                  :runkoaine/litteysluku 4.5M
                                                                  :runkoaine/massaprosentti 5.0M
                                                                  :runkoaine/tyyppi 2}]
                                 :harja.domain.pot2/sideaineet ({::pot2-domain/massa-id 2
                                                                 :sideaine/id 2
                                                                 :sideaine/lopputuote? true
                                                                 :sideaine/pitoisuus 5.5M
                                                                 :sideaine/tyyppi 5})
                                 :harja.domain.pot2/tyyppi 14
                                 ::pot2-domain/massa-id 2}))

(def oletetut-murskeet-utajarvi '(#:harja.domain.pot2{:esiintyma "Kankkulan Kaivo", :nimen-tarkenne "LJYR", :iskunkestavyys "LA30", :tyyppi 1, :rakeisuus "0/40", :dop-nro "1234567-dop", :murske-id 1 :kaytossa ({:kohdenumero "L42"
                                                                                                                                                                                                                   :kohteiden-lkm 1
                                                                                                                                                                                                                   :nimi "Tärkeä kohde mt20"
                                                                                                                                                                                                                   :tila "aloitettu"})}))

(def oletettu-testimassa-vastauksessa
  '(#:harja.domain.pot2{:dop-nro "12345abc"
                        :kaytossa ()
                        :kuulamyllyluokka "AN5"
                        :lisaaineet ({:harja.domain.pot2/massa-id 5
                                      :lisaaine/id 5
                                      :lisaaine/pitoisuus 1.5M
                                      :lisaaine/tyyppi 1})
                        :litteyslukuluokka "FI15"
                        :massa-id 5
                        :max-raekoko 5
                        :nimen-tarkenne "Tarkenne"
                        :runkoaineet [{:harja.domain.pot2/massa-id 5
                                       :runkoaine/esiintyma "Zatelliitti"
                                       :runkoaine/id 7
                                       :runkoaine/kuulamyllyarvo 12.1M
                                       :runkoaine/litteysluku 4.1M
                                       :runkoaine/massaprosentti 34.0M
                                       :runkoaine/tyyppi 1}]
                        :sideaineet ({:harja.domain.pot2/massa-id 5
                                      :sideaine/id 5
                                      :sideaine/lopputuote? true
                                      :sideaine/pitoisuus 10.6M
                                      :sideaine/tyyppi 1})
                        :tyyppi 1}))

(deftest hae-urakan-pot2-massat
  (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-urakan-massa
                          +kayttaja-jvh+ urakan-testimassa)
        {massat :massat murskeet :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakan-massat-ja-murskeet
                        +kayttaja-jvh+ {:urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")})]
    (is (= massat (concat oletetut-massat-utajarvi oletettu-testimassa-vastauksessa)))
    (is (= murskeet oletetut-murskeet-utajarvi))))

(deftest hae-pot2-koodistot-test
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-pot2-koodistot +kayttaja-jvh+ {})
        odotetut-avaimet #{:massatyypit
                           :mursketyypit
                           :runkoainetyypit
                           :sideainetyypit
                           :sidotun-kantavan-kerroksen-sideaine
                           :lisaainetyypit
                           :alusta-toimenpiteet
                           :paallystekerros-toimenpiteet
                           :verkon-sijainnit
                           :verkon-tarkoitukset
                           :verkon-tyypit}]
    (is (every? #(contains? vastaus %) odotetut-avaimet))
    (is (= (count (keys vastaus)) (count odotetut-avaimet)) "Koodistojen määrä ei täsmää odotettujen määrään.")
    (is (= (some #(= :harja.domain.pot2 {:nimi "Kovat asfalttibetonit", :lyhenne "Kovat asfalttibetonit", :koodi 10} %)
                 (:massatyypit vastaus))))
    (is (= (some #(= :harja.domain.pot2 {:nimi " Kalliomurske", :lyhenne "KaM", :koodi 1} %)
                 (:mursketyypit vastaus))))
    (is (= (some #(= :harja.domain.pot2{:nimi "Kiviaines", :lyhenne "Kiviaines", :koodi 1} %)
                 (:runkoainetyypit vastaus))))
    (is (= (some #(= :harja.domain.pot2 {:nimi "Bitumi, 20/30", :lyhenne " Bitumi, 20/30", :koodi 1} %)
                 (:sideainetyypit vastaus))))
    (is (= (list #:harja.domain.pot2{:nimi "(UUSIO) CEM I", :koodi 1} #:harja.domain.pot2{:nimi "(UUSIO) CEM II/A-S", :koodi 2} #:harja.domain.pot2{:nimi "(UUSIO) CEM II/B-S", :koodi 3} #:harja.domain.pot2{:nimi "(UUSIO) CEM II/A-D", :koodi 4} #:harja.domain.pot2{:nimi "(UUSIO) CEM II/A-V", :koodi 5} #:harja.domain.pot2{:nimi "(UUSIO) CEM II/B-V", :koodi 6} #:harja.domain.pot2{:nimi "(UUSIO) CEM II/A-L", :koodi 7} #:harja.domain.pot2{:nimi "(UUSIO) CEM II/A-LL", :koodi 8} #:harja.domain.pot2{:nimi "(UUSIO) CEM II/A-M", :koodi 9} #:harja.domain.pot2{:nimi "(UUSIO) CEM III/A", :koodi 10} #:harja.domain.pot2{:nimi "(UUSIO) CEM III/B", :koodi 11} #:harja.domain.pot2{:nimi "Masuuni- tms kuona", :koodi 12}) (:sidotun-kantavan-kerroksen-sideaine vastaus)))
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
    (is (= (count (:paallystekerros-toimenpiteet vastaus)) (ffirst (q "SELECT count(*) FROM pot2_mk_paallystekerros_toimenpide"))))
    (is (= (count (:verkon-sijainnit vastaus)) (ffirst (q "SELECT count(*) FROM pot2_verkon_sijainti"))))
    (is (= (count (:verkon-tarkoitukset vastaus)) (ffirst (q "SELECT count(*) FROM pot2_verkon_tarkoitus"))))
    (is (= (count (:verkon-tyypit vastaus)) (ffirst (q "SELECT count(*) FROM pot2_verkon_tyyppi"))))
    ))

(deftest hae-vastuuhenkilon-muut-urakat-joissa-materiaaleja
  (let [muut-urakat
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-muut-urakat-joissa-materiaaleja
                        +kayttaja-vastuuhlo-muhos+ {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")})
        oletetut (list {:id (hae-urakan-id-nimella "Utajärven päällystysurakka")
                        :nimi "Utajärven päällystysurakka"})]
    (is (= muut-urakat oletetut) "Muut urakat oikein")))


(deftest hae-paakayttajan-muut-urakat-joissa-materiaaleja
  (let [muut-urakat
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-muut-urakat-joissa-materiaaleja
                        +kayttaja-paakayttaja-skanska+ {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")})
        oletetut (list {:id (hae-urakan-id-nimella "Utajärven päällystysurakka")
                        :nimi "Utajärven päällystysurakka"})
        muut-urakat-paikkausurakasta (kutsu-palvelua (:http-palvelin jarjestelma)
                                                     :hae-muut-urakat-joissa-materiaaleja
                                                     +kayttaja-paakayttaja-skanska+ {:urakka-id (hae-urakan-id-nimella "Muhoksen paikkausurakka")})
        oletetut-paikkausurakasta (list {:id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                                         :nimi "Muhoksen päällystysurakka"}
                                        {:id (hae-urakan-id-nimella "Utajärven päällystysurakka")
                                         :nimi "Utajärven päällystysurakka"})]
    (is (= muut-urakat oletetut) "Muut urakat oikein")
    (is (= muut-urakat-paikkausurakasta oletetut-paikkausurakasta)) "Muut urakat oikein"))

(deftest laadunvalvojalla-ei-lukuoikeutta
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-muut-urakat-joissa-materiaaleja
                                         +kayttaja-laadunvalvoja-kemi+ {:urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")}))))


;; JVH näkee kaikki urakat, mutta vain sen urakoitsijan materiaalit mikä urakka on valittuna.
;; Tämä on tärkeää, jotta missään tilanteessa kukaan käyttäjä ei pysty viemään eri urakoitsijan materiaalia toiselle urakoitsijalle
(deftest jvh-nakee-kaikista-muista-urakoista
  (let [muut-urakat-kemi (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-muut-urakat-joissa-materiaaleja
                                         +kayttaja-jvh+ {:urakka-id (hae-kemin-paallystysurakan-2019-2023-id)})
        muut-urakat-muhos (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-muut-urakat-joissa-materiaaleja
                        +kayttaja-jvh+ {:urakka-id (hae-muhoksen-paallystysurakan-testipaikkauskohteen-id)})
        muut-urakat-oulu (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-muut-urakat-joissa-materiaaleja
                        +kayttaja-jvh+ {:urakka-id (hae-oulun-alueurakan-2014-2019-id)})
        oletetut-kemin-urakoitsija (list) ;; Tällä urakoitsijalla ei materiaaleja
        oletetut-skanska (list {:id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
                                :nimi "Muhoksen päällystysurakka"}
                               {:id (hae-urakan-id-nimella "Utajärven päällystysurakka")
                                :nimi "Utajärven päällystysurakka"}
                               )
        oletetut-yit (list {:id (hae-urakan-id-nimella "Aktiivinen Oulu Päällystys Testi")
                            :nimi "Aktiivinen Oulu Päällystys Testi"})]
    (is (= muut-urakat-kemi oletetut-kemin-urakoitsija) "Muut urakat oikein")
    (is (= muut-urakat-muhos oletetut-skanska) "Muut urakat oikein")
    (is (= muut-urakat-oulu oletetut-yit) "Muut urakat oikein")))

(deftest hae-urakan-massat-ja-murskeet-eri-kayttajilla
  (doseq [kayttaja [+kayttaja-vastuuhlo-muhos+
                  +kayttaja-paakayttaja-skanska+
                  +kayttaja-jvh+]]
    (let [{massat :massat murskeet :murskeet}
          (kutsu-palvelua (:http-palvelin jarjestelma)
                          :hae-urakan-massat-ja-murskeet
                          kayttaja {:urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")})]
      (is (= oletetut-massat-utajarvi massat) "Oletetut massat Utajärven urakasta")
      (is (= oletetut-murskeet-utajarvi murskeet) "Oletetut murskeet Utajärven urakasta"))))

(deftest hae-urakan-massat-ja-murskeet-laadunvalvoja-ei-nakyvyytta
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                         :hae-urakan-massat-ja-murskeet
                                         +kayttaja-laadunvalvoja-kemi+ {:urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")}))))


(deftest hae-urakan-massat-ja-murskeet-ei-onnistu-eri-urakoitsijan-urakasta
  (doseq [kayttaja [+kayttaja-vastuuhlo-muhos+
                    +kayttaja-paakayttaja-skanska+]]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-urakan-massat-ja-murskeet
                                           kayttaja {:urakka-id (hae-urakan-id-nimella "Aktiivinen Oulu Päällystys Testi")})) (str "Ei pidä onnistua käyttäjällä " kayttaja))))

(def oletetut-massat-oulun-paallystysurakassa
  '(#:harja.domain.pot2{:dop-nro "34567"
                        :kaytossa ()
                        :kuulamyllyluokka "AN14"
                        :lisaaineet ({:harja.domain.pot2/massa-id 4
                                      :lisaaine/id 4
                                      :lisaaine/pitoisuus 0.2M
                                      :lisaaine/tyyppi 2})
                        :litteyslukuluokka "FI15"
                        :massa-id 4
                        :max-raekoko 16
                        :runkoaineet [{:harja.domain.pot2/massa-id 4
                                       :runkoaine/esiintyma "Kolokallio"
                                       :runkoaine/id 6
                                       :runkoaine/kuulamyllyarvo 12.0M
                                       :runkoaine/kuvaus "Kelpo runkoaine tämäkin."
                                       :runkoaine/litteysluku 8.5M
                                       :runkoaine/massaprosentti 55.0M
                                       :runkoaine/tyyppi 1}]
                        :sideaineet ({:harja.domain.pot2/massa-id 4
                                      :sideaine/id 4
                                      :sideaine/lopputuote? true
                                      :sideaine/pitoisuus 3.8M
                                      :sideaine/tyyppi 6})
                        :tyyppi 12}))

(def oletetut-murskeet-oulun-paallystysurakassa (list ))

(deftest hae-urakan-massat-ja-murskeet-ei-onnistu-eri-urakoitsijan-urakasta-jvh
  (let [{massat :massat murskeet :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakan-massat-ja-murskeet
                        +kayttaja-jvh+ {:urakka-id (hae-urakan-id-nimella "Aktiivinen Oulu Päällystys Testi")})]
    (is (= oletetut-massat-oulun-paallystysurakassa massat) "Oletetut massat Oulun urakasta")
    (is (= oletetut-murskeet-oulun-paallystysurakassa murskeet) "Oletetut murskeet Oulun urakasta")))

(deftest tuo-materiaalit-utajarvi->porvoo
  (let [{massat :massat murskeet :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakan-massat-ja-murskeet
                        +kayttaja-vastuuhlo-muhos+ {:urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")})
        urakka-id (hae-urakan-id-nimella "Porvoon päällystysurakka")
        massa-idt (map ::pot2-domain/massa-id massat)
        murske-idt (map ::pot2-domain/murske-id murskeet)
        runkoaine-max-id (ffirst (q "SELECT max (id) FROM pot2_mk_massan_runkoaine;"))
        sideaine-max-id (ffirst (q "SELECT max (id) FROM pot2_mk_massan_sideaine;"))
        lisaaine-max-id (ffirst (q "SELECT max (id) FROM pot2_mk_massan_lisaaine;"))
        {tuodut-massat :massat tuodut-murskeet :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tuo-materiaalit-toisesta-urakasta
                        +kayttaja-vastuuhlo-porvoo+ {:urakka-id urakka-id
                                                     :massa-idt massa-idt
                                                     :murske-idt murske-idt})
        alkuperainen-massa-dop-1234567 (first (filter #(= "1234567" (::pot2-domain/dop-nro %)) massat))
        alkuperaisen-runkoaineet (::pot2-domain/runkoaineet alkuperainen-massa-dop-1234567)
        alkuperaisen-sideaineet (::pot2-domain/sideaineet alkuperainen-massa-dop-1234567)
        alkuperaisen-lisaaineet (::pot2-domain/lisaaineet alkuperainen-massa-dop-1234567)
        monistettu-dop-1234567 (first (filter #(= "1234567" (::pot2-domain/dop-nro %)) tuodut-massat))
        monistettu-dop-1234567-massa-id (::pot2-domain/massa-id monistettu-dop-1234567)
        monistettu-1234567-dop-murske (first (filter #(= "1234567-dop" (::pot2-domain/dop-nro %)) tuodut-murskeet))
        ;; koska luodaan runkoaineista kopio, niiden id:t saavat sekvenssistä max:ia seuraavan arvon
        odotetut-runkoaineet (map #(-> %
                                       (assoc ::pot2-domain/massa-id monistettu-dop-1234567-massa-id)
                                       (assoc :runkoaine/id (+ 1 runkoaine-max-id)))
                                  alkuperaisen-runkoaineet)
        odotetut-sideaineet (map #(-> %
                                      (assoc ::pot2-domain/massa-id monistettu-dop-1234567-massa-id)
                                      (assoc :sideaine/id (+ 1 sideaine-max-id)))
                                 alkuperaisen-sideaineet)
        odotetut-lisaaineet (map #(-> %
                                      (assoc ::pot2-domain/massa-id monistettu-dop-1234567-massa-id)
                                      (assoc :lisaaine/id (+ 1 lisaaine-max-id)))
                                 alkuperaisen-lisaaineet)]



    ;; vertaillaan alkuperäiseen massaan, siten että id:t korvataan monistetun massan id:illä
    ;; assertoidaan ensin massan perustiedot, myöhemmin erillisesti runko-, side- ja lisäaineet
    (is (= (-> alkuperainen-massa-dop-1234567
               (assoc ::pot2-domain/kaytossa (list)
                      ::pot2-domain/massa-id monistettu-dop-1234567-massa-id)
               (dissoc ::pot2-domain/runkoaineet
                       ::pot2-domain/sideaineet
                       ::pot2-domain/lisaaineet))
           ;; assertoidaan runko-, side- ja lisäaineet erikseen
           (dissoc monistettu-dop-1234567 ::pot2-domain/runkoaineet
                   ::pot2-domain/sideaineet
                   ::pot2-domain/lisaaineet)) "Tuodut massat")

    (is (= odotetut-runkoaineet (::pot2-domain/runkoaineet monistettu-dop-1234567)) "Runkoaineet monistettu oikein")
    (is (= odotetut-sideaineet (::pot2-domain/sideaineet monistettu-dop-1234567)) "Sideaineet monistettu oikein")
    (is (= odotetut-lisaaineet (::pot2-domain/lisaaineet monistettu-dop-1234567)) "Lisäaineet monistettu oikein")
    (is (= (-> (first murskeet)
               (assoc ::pot2-domain/kaytossa (list)
                      ::pot2-domain/murske-id (::pot2-domain/murske-id monistettu-1234567-dop-murske)))
           monistettu-1234567-dop-murske) "Murskeet monistettu oikein")))


(deftest hae-materiaalit-utajarvi->oulun-alueurakka-epaonnistuu
  ;; Oulun urakan vastuuhenkilö (yit uuvh) koettaa hakea Utajärven materiaaleja eikä saa onnistua
  (is (thrown? Exception
               (kutsu-palvelua (:http-palvelin jarjestelma)
                               :hae-urakan-massat-ja-murskeet
                               +kayttaja-yit_uuvh+ {:urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")}))))

(deftest tuo-materiaalit-utajarvi->oulun-alueurakka-epaonnistuu
  (let [{massat :massat murskeet :murskeet}
        ;; tässä teeskennellään että ensin jostain syystä (JVH:n avulla) saataisiin materiaalit haettua...
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakan-massat-ja-murskeet
                        +kayttaja-jvh+ {:urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")})
        urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        massa-idt (map ::pot2-domain/massa-id massat)
        murske-idt (map ::pot2-domain/murske-id murskeet)]
    (is (thrown? Exception
                 ;; ... jolloin vasta tämän palvelukutsun oikeustarkistus pääsee ajoon asti ja feilaa
                 ;; eli ei pidäkään voida hakea materiaaleja toisen urakoitsijan urakasta
                (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tuo-materiaalit-toisesta-urakasta
                                +kayttaja-yit_uuvh+ {:urakka-id urakka-id
                                                      :massa-idt massa-idt
                                                      :murske-idt murske-idt})))))

(deftest kaytossa-olevaa-massaa-ei-voi-poistaa
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        massan-id (ffirst (q (str "SELECT id FROM pot2_mk_urakan_massa WHERE urakka_id = "
                                  urakka-id
                                  " AND dop_nro = '1234567';")))]
    (is (thrown? SecurityException
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :poista-urakan-massa
                                 +kayttaja-vastuuhlo-muhos+ {:id massan-id})))))

(deftest kaytossa-olevaa-mursketta-ei-voi-poistaa
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        murskeen-id (ffirst (q (str "SELECT id FROM pot2_mk_urakan_murske WHERE urakka_id = "
                                  urakka-id
                                  " AND dop_nro = '1234567-dop';")))]
    (is (thrown? SecurityException
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :poista-urakan-massa
                                 +kayttaja-vastuuhlo-muhos+ {:id murskeen-id})))))

(deftest massan-poisto-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        {massat-ennen :massat murskeet-ennen :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakan-massat-ja-murskeet
                        +kayttaja-vastuuhlo-muhos+ {:urakka-id urakka-id})

        massan-id (ffirst (q (str "SELECT id FROM pot2_mk_urakan_massa WHERE urakka_id = "
                                  urakka-id
                                  " AND dop_nro = '764567-dop';")))
        {massat-jalkeen :massat murskeet-jalkeen :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :poista-urakan-massa
                        +kayttaja-vastuuhlo-muhos+ {:id massan-id})]
    (is (not (empty? massat-ennen)))
    (is (= 2 (count murskeet-ennen) (count murskeet-jalkeen)) "Mmurskeisiin ei koskettu")
    (is (not-empty (filter #(= massan-id (::pot2-domain/massa-id %))
                           massat-ennen)))
    (is (empty? (filter #(= massan-id (::pot2-domain/massa-id %))
                        massat-jalkeen)))
    (is (empty? massat-jalkeen))))

(deftest murskeen-poisto-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        {massat-ennen :massat murskeet-ennen :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakan-massat-ja-murskeet
                        +kayttaja-vastuuhlo-muhos+ {:urakka-id urakka-id})

        murskeen-id (ffirst (q (str "SELECT id FROM pot2_mk_urakan_murske WHERE urakka_id = "
                                  urakka-id
                                  " AND dop_nro = '3524534-dop';")))
        {massat-jalkeen :massat murskeet-jalkeen :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :poista-urakan-murske
                        +kayttaja-vastuuhlo-muhos+ {:id murskeen-id})]
    (is (= 1 (count massat-ennen) (count massat-jalkeen)) "Massoihin ei koskettu")
    (is (= 2 (count murskeet-ennen)))
    (is (not-empty (filter #(= murskeen-id (::pot2-domain/murske-id %))
                        murskeet-ennen)))
    (is (= 1 (count murskeet-jalkeen)))
    (is (empty? (filter #(= murskeen-id (::pot2-domain/murske-id %))
                 murskeet-jalkeen)))))

(deftest massaa-jolle-ei-loydy-urakkaa-heittaa-poikkeuksen
  (is (thrown? AssertionError
               (kutsu-palvelua (:http-palvelin jarjestelma)
                               :poista-urakan-massa
                               +kayttaja-vastuuhlo-muhos+ {:id 4231234234}))))

(deftest eri-urakoitsija-ei-saa-poistaa-massaa
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        massan-id (ffirst (q (str "SELECT id FROM pot2_mk_urakan_massa WHERE urakka_id = "
                                  urakka-id
                                  " AND dop_nro = '764567-dop';")))]
    (is (thrown? Throwable
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :poista-urakan-massa
                                 +kayttaja-yit_uuvh+ {:id massan-id})))))

(deftest eri-urakoitsija-ei-saa-poistaa-mursketta
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        murskeen-id (ffirst (q (str "SELECT id FROM pot2_mk_urakan_murske WHERE urakka_id = "
                                    urakka-id
                                    " AND dop_nro = '3524534-dop';")))]
    (is (thrown? Throwable
                 (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :poista-urakan-murske
                                 +kayttaja-yit_uuvh+ {:id murskeen-id})))))

(deftest nimen-tarkenne-erottelee-jos-tuodaan-sama-massa-tai-murske-useampaan-kertaan
  (let [{massat :massat murskeet :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakan-massat-ja-murskeet
                        +kayttaja-vastuuhlo-muhos+ {:urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")})
        urakka-id (hae-urakan-id-nimella "Porvoon päällystysurakka")
        tuotavan-massan-id (reduce min (map ::pot2-domain/massa-id massat))
        tuotavan-murskeen-id (mapv ::pot2-domain/murske-id murskeet)
        {tuodut-massat-eka :massat tuodut-murskeet-eka :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tuo-materiaalit-toisesta-urakasta
                        +kayttaja-vastuuhlo-porvoo+ {:urakka-id urakka-id
                                                     :massa-idt [tuotavan-massan-id]
                                                     :murske-idt tuotavan-murskeen-id})
        {tuodut-massat-toka :massat tuodut-murskeet-toka :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tuo-materiaalit-toisesta-urakasta
                        +kayttaja-vastuuhlo-porvoo+ {:urakka-id urakka-id
                                                     :massa-idt [tuotavan-massan-id]
                                                     :murske-idt tuotavan-murskeen-id})
        {tuodut-massat-kolmas :massat tuodut-murskeet-kolmas :murskeet}
        (kutsu-palvelua (:http-palvelin jarjestelma)
                        :tuo-materiaalit-toisesta-urakasta
                        +kayttaja-vastuuhlo-porvoo+ {:urakka-id urakka-id
                                                     :massa-idt [tuotavan-massan-id]
                                                     :murske-idt tuotavan-murskeen-id})
        toinen-kopioimalla-luotu-massa (first (filter #(not= (::pot2-domain/massa-id  %)
                                                             (-> tuodut-massat-eka first ::pot2-domain/massa-id))
                                                      tuodut-massat-toka))
        toinen-kopioimalla-luotu-murske (first (filter #(not= (::pot2-domain/murske-id  %)
                                                             (-> tuodut-murskeet-eka first ::pot2-domain/murske-id))
                                                      tuodut-murskeet-toka))
        kolmas-kopioimalla-luotu-massa (first (filter #(not-any? (fn [toiset-massat]
                                                                   (= (::pot2-domain/massa-id %)
                                                                      (::pot2-domain/massa-id toiset-massat)))
                                                                 tuodut-massat-toka)
                                                      tuodut-massat-kolmas))
        kolmas-kopioimalla-luotu-murske (first (filter #(not-any? (fn [toiset-murskeet]
                                                                    (= (::pot2-domain/murske-id %)
                                                                       (::pot2-domain/murske-id toiset-murskeet)))
                                                                  tuodut-murskeet-toka)
                                                       tuodut-murskeet-kolmas))]
    (is (nil? (::pot2-domain/nimen-tarkenne (first tuodut-massat-eka))) "Ensi tuonnin jälkeen massan nimi")
    (is (= " 2" (::pot2-domain/nimen-tarkenne toinen-kopioimalla-luotu-massa)) "Toisen tuonnin jälkeen massan nimi")
    (is (= " 3" (::pot2-domain/nimen-tarkenne kolmas-kopioimalla-luotu-massa)) "Kolmannen tuonnin jälkeen massan nimi")

    (is (= "LJYR" (::pot2-domain/nimen-tarkenne (first tuodut-murskeet-eka))) "Ensi tuonnin jälkeen murskeen nimi")
    (is (= "LJYR 2" (::pot2-domain/nimen-tarkenne toinen-kopioimalla-luotu-murske)) "Toisen tuonnin jälkeen murskeen nimi")
    (is (= "LJYR 3" (::pot2-domain/nimen-tarkenne kolmas-kopioimalla-luotu-murske)) "Kolmannen tuonnin jälkeen murskeen nimi")))

(deftest tarkenne-kayttaa-juoksevaa-numerointia-jos-sama-nimi
  (is (= (pot2/paivita-tarkennetta nil) " 2"))
  (is (= (pot2/paivita-tarkennetta "") " 2"))
  (is (= (pot2/paivita-tarkennetta " ") "  2"))
  (is (= (pot2/paivita-tarkennetta "abc") "abc 2"))
  (is (= (pot2/paivita-tarkennetta "abc 2") "abc 3"))
  (is (= (pot2/paivita-tarkennetta "abc 3") "abc 4")))