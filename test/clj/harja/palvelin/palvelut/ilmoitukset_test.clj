(ns harja.palvelin.palvelut.ilmoitukset-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.ilmoitukset :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konv]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :hae-ilmoitukset (component/using
                                                (->Ilmoitukset)
                                                [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest hae-ilmoitukset-sarakkeet
  (let []
    (is (oikeat-sarakkeet-palvelussa?
          [:id :urakka :ilmoitusid :ilmoitettu :valitetty :yhteydenottopyynto :vapaateksti
           :ilmoitustyyppi :selitteet :urakkatyyppi :suljettu :sijainti :uusinkuittaus

           [:tr :numero] [:tr :alkuosa] [:tr :loppuosa] [:tr :alkuetaisyys] [:tr :loppuetaisyys]
           [:ilmoittaja :etunimi] [:ilmoittaja :sukunimi] [:ilmoittaja :tyopuhelin] [:ilmoittaja :matkapuhelin]
           [:ilmoittaja :sahkoposti] [:ilmoittaja :tyyppi]
           [:lahettaja :etunimi] [:lahettaja :sukunimi] [:lahettaja :puhelinnumero] [:lahettaja :sahkoposti]

           [:kuittaukset 0 :id] [:kuittaukset 0 :kuitattu] [:kuittaukset 0 :vapaateksti] [:kuittaukset 0 :kuittaustyyppi]
           [:kuittaukset 0 :kuittaaja :etunimi] [:kuittaukset 0 :kuittaaja :sukunimi] [:kuittaukset 0 :kuittaaja :matkapuhelin]
           [:kuittaukset 0 :kuittaaja :tyopuhelin] [:kuittaukset 0 :kuittaaja :sahkoposti]
           [:kuittaukset 0 :kuittaaja :organisaatio] [:kuittaukset 0 :kuittaaja :ytunnus]
           [:kuittaukset 0 :kasittelija :etunimi] [:kuittaukset 0 :kasittelija :sukunimi]
           [:kuittaukset 0 :kasittelija :matkapuhelin] [:kuittaukset 0 :kasittelija :tyopuhelin]
           [:kuittaukset 0 :kasittelija :sahkoposti] [:kuittaukset 0 :kasittelija :organisaatio]
           [:kuittaukset 0 :kasittelija :ytunnus]]

          :hae-ilmoitukset
          {:hallintayksikko nil
           :urakka nil
           :tilat nil
           :tyypit [:kysely :toimepidepyynto :ilmoitus]
           :aikavali nil
           :hakuehto nil}))))
