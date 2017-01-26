(ns harja.palvelin.integraatiot.tloik.sahkoposti-test
  (:require [harja.palvelin.integraatiot.tloik.sahkoposti :as sut]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.sms-test :refer [feikki-labyrintti]]
            [harja.jms-test :refer [feikki-sonja]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer [luo-tloik-komponentti tuo-ilmoitus] :as tloik-apurit]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sonja.sahkoposti.sanomat :as sahkoposti-sanomat])
  (:import (java.util UUID)))

(def kayttaja "jvh")
(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :sonja (feikki-sonja)
    :sonja-sahkoposti (component/using
                        (sahkoposti/luo-sahkoposti "foo@example.com"
                                                   {:sahkoposti-sisaan-jono         "email-to-harja"
                                                    :sahkoposti-sisaan-kuittausjono "email-to-harja-ack"
                                                    :sahkoposti-ulos-jono           "harja-to-email"
                                                    :sahkoposti-ulos-kuittausjono   "harja-to-email-ack"})
                        [:sonja :db :integraatioloki])
    :labyrintti (feikki-labyrintti)
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :sonja :integraatioloki :klusterin-tapahtumat :sonja-sahkoposti])))

(use-fixtures :each jarjestelma-fixture)

(deftest kuittausviestin-lukeminen
  (let [{:keys [urakka-id ilmoitus-id kuittaustyyppi kommentti]}
        (sut/lue-kuittausviesti "#[4/1234] Toimenpidepyyntö" "[Aloitettu] aletaanpa hommiin")]
    (is (= urakka-id 4))
    (is (= ilmoitus-id 1234))
    (is (= kuittaustyyppi :aloitettu))
    (is (= kommentti "aletaanpa hommiin"))))

(deftest virheellisen-viestin-lukeminen
  (is (= '(:virhe) (keys (sut/lue-kuittausviesti "#[12/3333 asdasd" "eipä mitään")))))

(defn sahkoposti-viesti [id lahettaja vastaanottaja otsikko sisalto]
  (-> "resources/xsd/sahkoposti/esimerkit/sahkoposti_template.xml"
      slurp
      (.replace "__ID__" id)
      (.replace "__LAHETTAJA__" lahettaja)
      (.replace "__VASTAANOTTAJA__" vastaanottaja)
      (.replace "__OTSIKKO__" otsikko)
      (.replace "__SISALTO__" sisalto)))

(deftest tarkista-kuittauksen-vastaanotto-sahkopostilla
  (let [ilmoitusviesti (atom nil)]
    (tloik-apurit/tee-testipaivystys)
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" (partial reset! ilmoitusviesti))
    (sonja/laheta (:sonja jarjestelma)
                  tloik-apurit/+tloik-ilmoitusviestijono+
                  tloik-apurit/+testi-ilmoitus-sanoma+)
    (let [saapunut (-> (odota-arvo ilmoitusviesti)
                       .getText
                       sahkoposti-sanomat/lue-sahkoposti)
          vastaanottaja (:vastaanottaja saapunut)
          viesti (str (UUID/randomUUID))]
      ;; Tarkista että viesti lähtee päivystäjälle
      (is (= (:otsikko saapunut) "#[4/123456789] Toimenpide­pyyntö (VIRKA-APUPYYNTÖ)"))

      ;; Lähetä aloitettu kuittaus
      (sonja/laheta (:sonja jarjestelma) "email-to-harja"
                    (sahkoposti-viesti "111222333" vastaanottaja "harja-ilmoitukset@liikennevirasto.fi"
                                       (:otsikko saapunut)
                                       (str "[Vastaanotettu] " viesti)))

      ;; Odotetaan, että kuittaus on tallentunut
      (odota #(= 1 (ffirst (q (str "SELECT COUNT(*) FROM ilmoitustoimenpide WHERE vapaateksti LIKE '%" viesti "%'"))))
             "Kuittaus on tallentunut" 2000))))
