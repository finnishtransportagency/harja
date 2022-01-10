(ns harja.palvelin.palvelut.varuste-velho-test
  (:require [cheshire.core :as cheshire]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.paallystyksen-maksuerat :as paallystyksen-maksuerat-domain]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus-domain]
            [harja.domain.paallystysilmoitus :as paallystysilmoitus-domain]
            [harja.domain.paallystysilmoitus :as pot-domain]
            [harja.domain.pot2 :as pot2-domain]
            [harja.domain.skeema :as skeema]
            [harja.domain.sopimus :as sopimus-domain]
            [harja.domain.urakka :as urakka-domain]
            [harja.jms-test :refer [feikki-jms]]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yllapitokohteet-test :as yllapitokohteet-test]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys :refer :all]
            [harja.palvelin.palvelut.yllapitokohteet.pot2 :as pot2]
            [harja.palvelin.palvelut.varuste-velho :as varuste-velho]
            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [namespacefy.core :refer [namespacefy]]
            [taoensso.timbre :as log]))

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

(deftest hae-urakan-35-uusimmat-varusteet
  (let [urakka-id 35
        odotettu-oid-lista (sort ["1.2.246.578.4.3.12.512.310173990"
                                  "1.2.246.578.4.3.12.512.310173991"
                                  "1.2.246.578.4.3.12.512.310173992"
                                  "1.2.246.578.4.3.12.512.310173993"
                                  "1.2.246.578.4.3.12.512.310173994"
                                  "1.2.246.578.4.3.12.512.310173995"
                                  "1.2.246.578.4.3.12.512.310173996"
                                  "1.2.246.578.4.3.12.512.310173997"
                                  "1.2.246.578.4.3.12.512.310173998"])
        saatu-oid-lista (->> (varuste-velho/hae-urakan-varustetoteuma-ulkoiset (:db jarjestelma) +kayttaja-jvh+ {:urakka-id urakka-id})
                            :toteumat
                            (map :ulkoinen-oid)
                            vec
                            sort
                            )]
    (is (= odotettu-oid-lista saatu-oid-lista))))