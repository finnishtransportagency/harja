(ns harja.palvelin.integraatiot.sonja.sahkoposti-test
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sonja-sahkoposti]
            [harja.palvelin.integraatiot.sonja.sahkoposti.sanomat :as sanomat]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.tyokalut.xml :as xml]
            [harja.kyselyt.integraatiot :as integraatiot]
            [clojure.test :as t :refer :all]
            [harja.pvm :as pvm]))

(def +sahkoposti-xsd+ "xsd/sahkoposti/sahkoposti.xsd")
(def +sahkoposti-esimerkki+ "resources/xsd/sahkoposti/esimerkit/sahkoposti.xml")

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    "jvh"
    :sonja (feikki-sonja)
    :sonja-sahkoposti (component/using
                        (sonja-sahkoposti/luo-sahkoposti "foo@example.com"
                                                         {:sahkoposti-sisaan-jono "email-to-harja"
                                                          :sahkoposti-ulos-jono "harja-to-email"
                                                          :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                        [:sonja :db :integraatioloki])))

(use-fixtures :once jarjestelma-fixture)

(defn kuittaus
  "Tee annetulle vastaanotetulle sähköpostiviestille kuittausviesti"
  [{viesti-id :viesti-id} virheet]
  [:sahkoposti:kuittaus {:xmlns:sahkoposti "http://www.liikennevirasto.fi/xsd/harja/sahkoposti"}
   [:viestiId viesti-id]
   [:aika (xml/formatoi-xsd-datetime (pvm/nyt))]
   [:onnistunut (nil? virheet)]
   (when virheet
     (for [virhe virheet]
       [:virheet virhe]))])

(deftest viestin-luku
  (let [{:keys [viesti-id lahettaja vastaanottaja otsikko sisalto]}
        (sanomat/lue-sahkoposti (slurp +sahkoposti-esimerkki+))]
    (is (= viesti-id "21EC2020-3AEA-4069-A2DD-08002B30309D"))
    (is (= lahettaja "harja@vayla.fi"))
    (is (= vastaanottaja "erkki.esimerkki@example.com"))
    (is (= otsikko "Testiviesti"))
    (is (= sisalto "Tämä on testiviesti!"))))

(deftest sahkopostin-vastaanotto
  (let [viesti-xml (slurp +sahkoposti-esimerkki+)
        saapunut (atom nil)
        poista-kuuntelija-fn (sahkoposti/rekisteroi-kuuntelija! (:sonja-sahkoposti jarjestelma)
                                                                #(reset! saapunut %))]

    ;; Lähetetään sähköpostiviesti Harjaan
    (sonja/laheta (:sonja jarjestelma) "email-to-harja" viesti-xml)

    (odota-ehdon-tayttymista #(not (nil? @saapunut)) "Odotetaan, että sähköposti on vastaanotettu" 500)

    ;; Varmistetaan, että viesti on saapunut oikein kuuntelijalle ja, että kuittaus
    ;; on lähetetty takaisin kuittausjonoon
    (is (= (:otsikko @saapunut) "Testiviesti"))

    (poista-kuuntelija-fn)))

(deftest sahkopostin-lahetys
  (let [lahetetty (atom nil)]
    (sonja/kuuntele (:sonja jarjestelma) "harja-to-email" #(reset! lahetetty (sanomat/lue-sahkoposti (.getText %))))

    ;; Lähetetään viesti ja odotetaan, että se on mennyt jonoon
    (sahkoposti/laheta-viesti! (:sonja-sahkoposti jarjestelma)
                               "lasse.lahettaja@example.com"
                               "ville.vastaanottaja@example.com"
                               "Otsikoidaan"
                               "Leipäteksti")
    (odota-ehdon-tayttymista #(not (nil? @lahetetty)) "Odotetaan, että viesti on lähetetty" 500)
    (is (= (:vastaanottaja @lahetetty) "ville.vastaanottaja@example.com"))
    (is (= (:lahettaja @lahetetty) "lasse.lahettaja@example.com"))

    (let [db (:db jarjestelma)
          integraatio (integraatiot/integraation-id db "sonja" "sahkoposti-lahetys")
          viesti-id (ffirst
                      (q "SELECT ulkoinenid
                          FROM integraatioviesti iv
                          JOIN integraatiotapahtuma it ON iv.integraatiotapahtuma = it.id
                          JOIN integraatio i ON i.id = it.integraatio
                          WHERE i.jarjestelma = 'sonja' AND
                                i.nimi = 'sahkoposti-lahetys' AND
                                iv.suunta = 'ulos'
                          ORDER BY it.alkanut
                          LIMIT 1; "))]

      ;; Varmistetaan että integraatiotapahtuma on auki
      (is (not (integraatiot/integraatiotapahtuma-paattynyt? db integraatio viesti-id)))

      ;; Lähetetään kuittaus ja varmistetaan, että integraatiotapahtuma on merkitty päättyneeksi
      (let [kuittaus-xml (xml/tee-xml-sanoma (kuittaus {:viesti-id viesti-id} nil))]
        (sonja/laheta (:sonja jarjestelma) "harja-to-email-ack" kuittaus-xml)

        (odota-ehdon-tayttymista #(integraatiot/integraatiotapahtuma-paattynyt? db integraatio viesti-id)
                                 "Odota, että integraatiotapahtuma päätetään"
                                 1500)))))
