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

(def default-pot2-massa
  {::pot2-domain/urakka-id (hae-utajarven-paallystysurakan-id)
   ::pot2-domain/nimen-tarkenne "Tarkenne"
   ::pot2-domain/tyyppi (:koodi (first paallystys-ja-paikkaus-domain/+paallystetyypit+)) ;; Harjan vanhassa kielenkäytössä nämä on päällystetyyppejä
   ::pot2-domain/max-raekoko (first pot2-domain/massan-max-raekoko)
   ::pot2-domain/kuulamyllyluokka (:nimi (first paallystysilmoitus-domain/+kuulamyllyt+))
   ::pot2-domain/litteyslukuluokka 1
   ::pot2-domain/dop-nro "12345abc"
   ::pot2-domain/runkoaineet runkoaine-kiviaines-default1
   ::pot2-domain/sideaineet sideaine-default2
   ::pot2-domain/lisaaineet lisaaine-default1 })



;; Pot2 liittyväisiä testejä. Siirtele nämä omaan tiedostoon kun tuntuu siltä
(deftest tallenna-uusi-pot2-massa
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-urakan-pot2-massa
                                +kayttaja-jvh+ default-pot2-massa)
        _ (println "tallenna-uusi-pot2-massa :: vastaus " (pr-str vastaus))
        oletus-vastaus #:harja.domain.pot2{:lisaaineet {1 {:valittu? true, :lisaaine/pitoisuus 1.5M}}, :dop-nro "12345abc", :max-raekoko 5, :urakka-id 7, :nimen-tarkenne "Tarkenne", :litteyslukuluokka 1, :runkoaineet {1 {:valittu? true, :runkoaine/esiintyma "Zatelliitti", :runkoaine/kuulamyllyarvo 12.1M, :runkoaine/litteysluku 4.1M, :runkoaine/massaprosentti 34}}, :kuulamyllyluokka "AN5", :sideaineet {:lopputuote {:valittu? true, :aineet {0 #:sideaine{:tyyppi 1, :pitoisuus 10.56M, :lopputuote? true}}}, :lisatty {:aineet {0 {:valittu? true, :sideaine/tyyppi 2, :sideaine/pitoisuus 10.4M, :sideaine/lopputuote? false}}}}, :tyyppi 1}]
    (is (= (siivoa-muuttuvat (:harja.domain.pot2/lisaaineet vastaus)) (siivoa-muuttuvat (:harja.domain.pot2/lisaaineet oletus-vastaus))))
    (is (= (siivoa-muuttuvat (:harja.domain.pot2/runkoaineet vastaus)) (siivoa-muuttuvat (:harja.domain.pot2/runkoaineet oletus-vastaus))))
    (is (= (siivoa-muuttuvat (:harja.domain.pot2/sideaineet vastaus)) (siivoa-muuttuvat (:harja.domain.pot2/sideaineet oletus-vastaus))))))

(deftest hae-urakan-pot2-massat
  (let [_ (kutsu-palvelua (:http-palvelin jarjestelma)
                          :tallenna-urakan-pot2-massa
                          +kayttaja-jvh+ default-pot2-massa)
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-pot2-massat
                                +kayttaja-jvh+ {:urakka-id (hae-utajarven-paallystysurakan-id)})
        oletettu-vastaus '({:harja.domain.pot2/dop-nro "1234567"
                            :harja.domain.pot2/kuulamyllyluokka "AN14"
                            :harja.domain.pot2/lisaaineet ({:lisaaine/id 1
                                                            :lisaaine/pitoisuus 0.5M
                                                            :lisaaine/tyyppi 2
                                                            :pot2-massa/id 1})
                            :harja.domain.pot2/litteyslukuluokka 1
                            :harja.domain.pot2/max-raekoko 16
                            :harja.domain.pot2/runkoaineet [{:pot2-massa/id 1
                                                             :runkoaine/esiintyma "Kaiskakallio"
                                                             :runkoaine/id 1
                                                             :runkoaine/kuulamyllyarvo 10.0M
                                                             :runkoaine/kuvaus "Kelpo runkoaine tämä."
                                                             :runkoaine/litteysluku 9.5M
                                                             :runkoaine/massaprosentti 52
                                                             :runkoaine/tyyppi 1}]
                            :harja.domain.pot2/sideaineet ({:pot2-massa/id 1
                                                            :sideaine/id 1
                                                            :sideaine/lopputuote? true
                                                            :sideaine/pitoisuus 4.8M
                                                            :sideaine/tyyppi 6})
                            :harja.domain.pot2/tyyppi 12
                            :pot2-massa/id 1}
                           {:harja.domain.pot2/dop-nro "987654331-2"
                            :harja.domain.pot2/kuulamyllyluokka "AN7"
                            :harja.domain.pot2/lisaaineet ({:lisaaine/id 2
                                                            :lisaaine/pitoisuus 0.5M
                                                            :lisaaine/tyyppi 1
                                                            :pot2-massa/id 2})
                            :harja.domain.pot2/litteyslukuluokka 2
                            :harja.domain.pot2/max-raekoko 16
                            :harja.domain.pot2/runkoaineet [{:pot2-massa/id 2
                                                             :runkoaine/esiintyma "Sammalkallio"
                                                             :runkoaine/id 2
                                                             :runkoaine/kuulamyllyarvo 9.2M
                                                             :runkoaine/kuvaus "Jämäkkä runkoaine."
                                                             :runkoaine/litteysluku 6.5M
                                                             :runkoaine/massaprosentti 85
                                                             :runkoaine/tyyppi 1}
                                                            {:pot2-massa/id 2
                                                             :runkoaine/esiintyma "Sammalkallio"
                                                             :runkoaine/fillerityyppi "Kalkkifilleri (KF)"
                                                             :runkoaine/id 3
                                                             :runkoaine/kuvaus "Oiva Filleri."
                                                             :runkoaine/massaprosentti 3
                                                             :runkoaine/tyyppi 3}
                                                            {:pot2-massa/id 2
                                                             :runkoaine/esiintyma "Sammalkallio"
                                                             :runkoaine/id 4
                                                             :runkoaine/kuulamyllyarvo 11.2M
                                                             :runkoaine/kuvaus "Rouhea aine."
                                                             :runkoaine/litteysluku 4.5M
                                                             :runkoaine/massaprosentti 5
                                                             :runkoaine/tyyppi 2}]
                            :harja.domain.pot2/sideaineet ({:pot2-massa/id 2
                                                            :sideaine/id 2
                                                            :sideaine/lopputuote? true
                                                            :sideaine/pitoisuus 5.5M
                                                            :sideaine/tyyppi 5})
                            :harja.domain.pot2/tyyppi 14
                            :pot2-massa/id 2}
                           {:harja.domain.pot2/dop-nro "12345abc"
                            :harja.domain.pot2/kuulamyllyluokka "AN5"
                            :harja.domain.pot2/lisaaineet ({:lisaaine/id 3
                                                            :lisaaine/pitoisuus 1.5M
                                                            :lisaaine/tyyppi 1
                                                            :pot2-massa/id 3})
                            :harja.domain.pot2/litteyslukuluokka 1
                            :harja.domain.pot2/max-raekoko 5
                            :harja.domain.pot2/nimen-tarkenne "Tarkenne"
                            :harja.domain.pot2/runkoaineet [{:pot2-massa/id 3
                                                             :runkoaine/esiintyma "Zatelliitti"
                                                             :runkoaine/id 5
                                                             :runkoaine/kuulamyllyarvo 12.1M
                                                             :runkoaine/litteysluku 4.1M
                                                             :runkoaine/massaprosentti 34
                                                             :runkoaine/tyyppi 1}]
                            :harja.domain.pot2/sideaineet ({:pot2-massa/id 3
                                                            :sideaine/id 3
                                                            :sideaine/lopputuote? true
                                                            :sideaine/pitoisuus 10.6M
                                                            :sideaine/tyyppi 1})
                            :harja.domain.pot2/tyyppi 1
                            :pot2-massa/id 3})]
    (is (= vastaus oletettu-vastaus))))