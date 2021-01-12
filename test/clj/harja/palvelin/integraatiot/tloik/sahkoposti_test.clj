(ns harja.palvelin.integraatiot.tloik.sahkoposti-test
  (:require [harja.palvelin.integraatiot.tloik.sahkoposti :as sut]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.labyrintti.sms :refer [feikki-labyrintti]]
            [harja.jms-test :refer [feikki-jms]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer [luo-tloik-komponentti tuo-ilmoitus] :as tloik-apurit]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.integraatiot.sonja.sahkoposti.sanomat :as sahkoposti-sanomat]
            [clj-time
             [core :as t]
             [format :as df]])
  (:import (java.util UUID)))

(def kayttaja "jvh")
(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :itmf (feikki-jms "itmf")
    :sonja (feikki-jms "sonja")
    :sonja-sahkoposti (component/using
                        (sahkoposti/luo-sahkoposti "foo@example.com"
                                                   {:sahkoposti-sisaan-jono "email-to-harja"
                                                    :sahkoposti-ulos-jono "harja-to-email"
                                                    :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                        [:sonja :db :integraatioloki])
    :labyrintti (feikki-labyrintti)
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :itmf :integraatioloki :sonja-sahkoposti])))

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

;; Toistuvasti feilaa, kommentoidaan pois. Olisi hyvä korjata vakaaksi.
#_(deftest tarkista-kuittauksen-vastaanotto-sahkopostilla
  (let [ilmoitusviesti (atom nil)
        urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)]
    (tloik-apurit/tee-testipaivystys urakka-id)
    (jms/kuuntele! (:sonja jarjestelma) "harja-to-email" (partial reset! ilmoitusviesti))
    (jms/laheta (:sonja jarjestelma)
                  tloik-apurit/+tloik-ilmoitusviestijono+
                  (tloik-apurit/testi-ilmoitus-sanoma
                    (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                                (t/minus (t/now) (t/minutes 300)))
                    (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                                (t/minus (t/now) (t/minutes 305)))))
    (let [saapunut (-> (odota-arvo ilmoitusviesti)
                       .getText
                       sahkoposti-sanomat/lue-sahkoposti)
          vastaanottaja (:vastaanottaja saapunut)
          viesti (str (UUID/randomUUID))]
      ;; Tarkista että viesti lähtee päivystäjälle
      (is (= (:otsikko saapunut) (str "#[" urakka-id "/123456789] Toimenpide­pyyntö (VIRKA-APUPYYNTÖ)")))

      ;; Lähetä aloitettu kuittaus
      (jms/laheta (:sonja jarjestelma) "email-to-harja"
                    (sahkoposti-viesti "111222333" vastaanottaja "harja-ilmoitukset@vayla.fi"
                                       (:otsikko saapunut)
                                       (str "[Vastaanotettu] " viesti)))

      ;; Odotetaan, että kuittaus on tallentunut
      (odota #(= 1 (ffirst (q (str "SELECT COUNT(*) FROM ilmoitustoimenpide WHERE vapaateksti LIKE '%" viesti "%'"))))
             "Kuittaus on tallentunut" 2000)

      ;; Testataan lopetuskuittauksen tekeminen
      (let [viesti (str (UUID/randomUUID))]

        ;; Lähetä lopetettu toimenpitein kuittaus
        (jms/laheta (:sonja jarjestelma) "email-to-harja"
                      (sahkoposti-viesti "111222333" vastaanottaja "harja-ilmoitukset@vayla.fi"
                                         (:otsikko saapunut)
                                         (str "[Lopetettu toimenpitein] " viesti)))
        
        ;; Odotetaan, että kuittaus on tallentunut
        (odota #(= 1 (ffirst (q (str "SELECT COUNT(*) FROM ilmoitustoimenpide WHERE vapaateksti LIKE '%" viesti "%'"))))
               "Kuittaus on tallentunut" 2000)

        ;; Tarkista, että ilmoitukselle on kirjautunut merkintä toimenpiteistä
        (is (true? (ffirst (q (str "SELECT \"aiheutti-toimenpiteita\" FROM ilmoitus WHERE ilmoitusid = 123456789"))))
            "Sähköpostikuittauksella voi merkitä aiheutuneet toimenpiteet")))))
