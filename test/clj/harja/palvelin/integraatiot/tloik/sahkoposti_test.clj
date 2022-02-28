(ns harja.palvelin.integraatiot.tloik.sahkoposti-test
  (:require [harja.palvelin.integraatiot.tloik.sahkoposti :as sut]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.labyrintti.sms :refer [feikki-labyrintti]]
            [harja.jms-test :refer [feikki-jms]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer [luo-tloik-komponentti tuo-ilmoitus] :as tloik-apurit]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as integraatiopiste-http]
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
    :api-sahkoposti (component/using
                       (sahkoposti-api/->ApiSahkoposti {:api-sahkoposti {:suora? false
                                                                         :sahkoposti-lahetys-url "/harja/api/sahkoposti/xml"
                                                                         :palvelin "http://localhost:8084"
                                                                         :vastausosoite "harja-ala-vastaa@vayla.fi"}
                                                        :tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                       [:http-palvelin :db :integraatioloki :itmf])
    :labyrintti (feikki-labyrintti)
    :tloik (component/using
             (luo-tloik-komponentti)
             [:db :itmf :integraatioloki :api-sahkoposti])))

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

(defn aseta-xml-sahkopostin-sisalto [otsikko sisalto lahettaja vastaanottaja]
  (-> "test/resurssit/api/sahkoposti/sahkoposti_malli.xml"
    slurp
    (.replace "__OTSIKKO__" otsikko)
    (.replace "__SISALTO__" sisalto)
    (.replace "__LAHETTAJA__" lahettaja)
    (.replace "__VASTAANOTTAJA__" vastaanottaja)))

(def onnistunut-kuittaus
  "<sahkoposti:kuittaus xmlns:sahkoposti=\"http://www.liikennevirasto.fi/xsd/harja/sahkoposti\">\n
  <viestiId>21EC2020-3AEA-4069-A2DD-08002B30309D</viestiId>\n
  <aika>2008-09-29T04:49:45</aika>\n
  <onnistunut>true</onnistunut>\n</sahkoposti:kuittaus>")

;; Toistuvasti feilaa, kommentoidaan pois. Olisi hyvä korjata vakaaksi.
(deftest tarkista-kuittauksen-vastaanotto-sahkopostilla
  (with-redefs
    [sahkoposti-api/muodosta-lahetys-uri (fn [_ _] "http://localhost:8084/api/sahkoposti")
     integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ _ _ _]
                                            {:status 200
                                             :header "jotain"
                                             :body onnistunut-kuittaus})]
    (let [urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)
          ilmoitus-id 123456789]
      (tloik-apurit/tee-testipaivystys urakka-id)
      ;; Lähetä t-loikista jonoihin ilmoitus - ilmoitusid 123456789
      (jms/laheta (:itmf jarjestelma)
        tloik-apurit/+tloik-ilmoitusviestijono+
        (tloik-apurit/testi-ilmoitus-sanoma
          (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
            (t/minus (t/now) (t/minutes 300)))
          (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
            (t/minus (t/now) (t/minutes 305)))))
      (let [_ (Thread/sleep 1000)
            integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))

            ;; Päivystäjä kuittaa ilmoituksen vastaaotetuksi
            viesti (str (UUID/randomUUID))
            sposti_aloitettu (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                               (str "[Vastaanotettu] " viesti) "JoukoKasslin@gustr.com" "harja-ilmoitukset@vayla.fi")
            vastaanotettu-vastaus (future (api-tyokalut/post-kutsu ["/sahkoposti-api/xml"] kayttaja portti sposti_aloitettu nil true))
            _ (odota-ehdon-tayttymista #(realized? vastaanotettu-vastaus) "Saatiin toimenpiteet-aloitettu-vastaus." 4000)

            ;; Päivystäjä kuittaa ilmoituksen aloitetuksi
            sposti_aloitettu (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                               (str "[Aloitettu] " viesti) "JoukoKasslin@gustr.com" "harja-ilmoitukset@vayla.fi")
            vastaanotettu-vastaus (future (api-tyokalut/post-kutsu ["/sahkoposti-api/xml"] kayttaja portti sposti_aloitettu nil true))
            _ (odota-ehdon-tayttymista #(realized? vastaanotettu-vastaus) "Saatiin toimenpiteet-aloitettu-vastaus." 4000)]
        ;; Tarkista että viesti lähtee päivystäjälle
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 1)) "VIRKA-APUPYYNTÖ" #_(str "#[" urakka-id "/" ilmoitus-id "] Toimenpide­pyyntö (VIRKA-APUPYYNTÖ)")))

        ;; Testataan lopetuskuittauksen tekeminen
        (let [random-viesti (str (UUID/randomUUID))
              ;; Lähetä toimenpiteet aloitettu kuittaus
              sposti_toimenpiteet-aloitettu (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                                              (str "[Toimenpiteet aloitettu] " random-viesti) "JoukoKasslin@gustr.com" "harja-ilmoitukset@vayla.fi")
              toimenpiteet-aloitettu-vastaus (future (api-tyokalut/post-kutsu ["/sahkoposti-api/xml"] kayttaja portti sposti_toimenpiteet-aloitettu nil true))
              _ (odota-ehdon-tayttymista #(realized? toimenpiteet-aloitettu-vastaus) "Saatiin toimenpiteet-aloitettu-vastaus." 4000)


              ;; Lähetä toimenpiteet lopetettu kuittaus
              sposti_toimenpiteet-lopetettu (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                                              (str "[Toimenpiteet lopetettu] " random-viesti) "JoukoKasslin@gustr.com" "harja-ilmoitukset@vayla.fi")
              toimenpiteet-aloitettu-vastaus (future (api-tyokalut/post-kutsu ["/sahkoposti-api/xml"] kayttaja portti sposti_toimenpiteet-lopetettu nil true))
              _ (odota-ehdon-tayttymista #(realized? toimenpiteet-aloitettu-vastaus) "Saatiin toimenpiteet-lopetettu-vastaus." 4000)
              ilmoitus (tloik-apurit/hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]

           ;; Tarkista, että ilmoitukselle on kirjautunut merkintä toimenpiteistä
          (is (true? (ffirst (q (str "SELECT \"aiheutti-toimenpiteita\" FROM ilmoitus WHERE ilmoitusid = 123456789"))))
            "Sähköpostikuittauksella voi merkitä aiheutuneet toimenpiteet"))))))
