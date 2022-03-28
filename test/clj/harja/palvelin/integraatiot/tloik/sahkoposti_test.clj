(ns harja.palvelin.integraatiot.tloik.sahkoposti-test
  (:require [harja.palvelin.integraatiot.tloik.sahkoposti :as sut]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.labyrintti.sms :refer [feikki-labyrintti]]
            [harja.jms-test :refer [feikki-jms]]
            [com.stuartsierra.component :as component]
            [harja.integraatio :as integraatio]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.tloik.tyokalut :refer [luo-tloik-komponentti tuo-ilmoitus] :as tloik-apurit]
            [harja.palvelin.integraatiot.tloik.aineistot.toimenpidepyynnot :as aineisto-toimenpidepyynnot]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as integraatiopiste-http]
            [clj-time
             [core :as t]
             [format :as df]]
            [clojure.string :as str])
  (:import (java.util UUID)))

(def spostin-vastaanotto-url "/sahkoposti/toimenpidekuittaus")
(def kayttaja "jvh")
(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :itmf (feikki-jms "itmf")
    :sonja (feikki-jms "sonja")
    :api-sahkoposti (component/using
                       (sahkoposti-api/->ApiSahkoposti {:api-sahkoposti integraatio/api-sahkoposti-asetukset
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
            vastaanotettu-vastaus (future (api-tyokalut/post-kutsu ["/sahkoposti/toimenpidekuittaus"] kayttaja portti sposti_aloitettu nil true))
            _ (odota-ehdon-tayttymista #(realized? vastaanotettu-vastaus) "Saatiin toimenpiteet-aloitettu-vastaus." 4000)

            ;; Päivystäjä kuittaa ilmoituksen aloitetuksi
            sposti_aloitettu (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                               (str "[Aloitettu] " viesti) "JoukoKasslin@gustr.com" "harja-ilmoitukset@vayla.fi")
            vastaanotettu-vastaus (future (api-tyokalut/post-kutsu ["/sahkoposti/toimenpidekuittaus"] kayttaja portti sposti_aloitettu nil true))
            _ (odota-ehdon-tayttymista #(realized? vastaanotettu-vastaus) "Saatiin toimenpiteet-aloitettu-vastaus." 4000)]
        ;; Tarkista että viesti lähtee päivystäjälle
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 1)) "VIRKA-APUPYYNTÖ" #_(str "#[" urakka-id "/" ilmoitus-id "] Toimenpide­pyyntö (VIRKA-APUPYYNTÖ)")))

        ;; Testataan lopetuskuittauksen tekeminen
        (let [random-viesti (str (UUID/randomUUID))
              ;; Lähetä toimenpiteet aloitettu kuittaus
              sposti_toimenpiteet-aloitettu (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                                              (str "[Toimenpiteet aloitettu] " random-viesti) "JoukoKasslin@gustr.com" "harja-ilmoitukset@vayla.fi")
              toimenpiteet-aloitettu-vastaus (future (api-tyokalut/post-kutsu ["/sahkoposti/toimenpidekuittaus"] kayttaja portti sposti_toimenpiteet-aloitettu nil true))
              _ (odota-ehdon-tayttymista #(realized? toimenpiteet-aloitettu-vastaus) "Saatiin toimenpiteet-aloitettu-vastaus." 4000)


              ;; Lähetä toimenpiteet lopetettu kuittaus
              sposti_toimenpiteet-lopetettu (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                                              (str "[Toimenpiteet lopetettu] " random-viesti) "JoukoKasslin@gustr.com" "harja-ilmoitukset@vayla.fi")
              toimenpiteet-aloitettu-vastaus (future (api-tyokalut/post-kutsu ["/sahkoposti/toimenpidekuittaus"] kayttaja portti sposti_toimenpiteet-lopetettu nil true))
              _ (odota-ehdon-tayttymista #(realized? toimenpiteet-aloitettu-vastaus) "Saatiin toimenpiteet-lopetettu-vastaus." 4000)
              ilmoitus (tloik-apurit/hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]

           ;; Tarkista, että ilmoitukselle on kirjautunut merkintä toimenpiteistä
          (is (true? (ffirst (q (str "SELECT \"aiheutti-toimenpiteita\" FROM ilmoitus WHERE ilmoitusid = 123456789"))))
            "Sähköpostikuittauksella voi merkitä aiheutuneet toimenpiteet"))))))

(def vaara-urakka-kuittaus "jes")

(deftest vaara-urakka-sahkoposti-test
  "Jos viesti on ensin mennyt väärälle urakalle ja tuo urakka on kuitannut viestin kuittauksella \"Väärä urakka\" ja sen jälkeen Liikennekeskus siirtänyt viestin toiselle urakalle niin tällöin viesti näkyy pelkästään Harjaselaimessa. Siitä ei tule sähköposteja lainkaan.
  Simuloidaan siis tilanne näin:
  1. Lähetetään toimenpidepyyntö
  2. jonka perusteella Harja lähettää rest-apin kautta sähköpostia väärälle urakalle.
  2. Väärän urakan päivystäjä vastaa kuittaus-sähköpostilla 'vaara-urakka'
  3. Harja lähettää 'vaara-urakka' tyyppisen viesitn T-loikin jonolle."
  (let [paivystajan-email "pekka.paivystaja@example.com"
        toimenpiteen-vastausemail "harja@vayla.fi"
        lokaali-sahkopostipalvelin "http://localhost:8084/api/sahkoposti"]

    (with-redefs
            [sahkoposti-api/muodosta-lahetys-uri (fn [_ _] lokaali-sahkopostipalvelin)
             integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ _ _ _]
                                                    {:status 200
                                                     :header "jotain"
                                                     :body onnistunut-kuittaus})
             ;; Lokaalisti ei oikein ole tilanteita, joissa tulee 2 urakkaa samalle koordinaatille, joten feikataan tilanne
             harja.palvelin.palvelut.urakat/hae-urakka-id-sijainnilla (fn [_ _ _ _]
                                                                        (hae-rovaniemen-maanteiden-hoitourakan-id))
             ;; Feikatulle rovaniemen urakalle ei ole lokaalikannassa päivystäjiä, joten feikataan sekin
             harja.kyselyt.yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat
             (fn [db urakka-id] (list {:id 1
                                       :etunimi "Pekka"
                                       :sukunimi "Päivystäjä"
                                       ;; Testi olettaa, että labyrinttiä ei ole mockattu eikä käynnistetty, joten puhelinnumerot on jätetty tyhjäksi
                                       :matkapuhelin nil
                                       :tyopuhelin nil
                                       :sahkoposti paivystajan-email
                                       :alku (t/now)
                                       :loppu (t/now)
                                       :vastuuhenkilo true
                                       :varahenkilo true}))
             ;; Feikatulle rovaniemen urakalle ei ole lokaalikannassa päivystäjiä, joten feikataan sekin, uudestaan
             harja.kyselyt.yhteyshenkilot/hae-urakan-paivystaja-sahkopostilla
             (fn [db urakka-id lahettaja] (list {:id 1
                                       :etunimi "Pekka"
                                       :sukunimi "Päivystäjä"
                                       ;; Testi olettaa, että labyrinttiä ei ole mockattu eikä käynnistetty, joten puhelinnumerot on jätetty tyhjäksi
                                       :matkapuhelin nil
                                       :tyopuhelin nil
                                       :sahkoposti paivystajan-email
                                       :alku (t/now)
                                       :loppu (t/now)
                                       :vastuuhenkilo true
                                       :varahenkilo true}))]

            (let [;; Alustetaan valaistusurakan viesti, mutta lähetetään se tälle väärälle rovaniemen hoitourakalle
                  vaara-urakka-id (hae-rovaniemen-maanteiden-hoitourakan-id)
                  viesti-id (str (UUID/randomUUID))
                  ilmoitus-id 987654321
                  nyt-185 (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                            (t/minus (t/now) (t/minutes 185)))
                  nyt-180 (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                            (t/minus (t/now) (t/minutes 180)))
                  valaistusilmoitus (tloik-apurit/testi-valaistusilmoitus-sanoma-eri-sijaintiin viesti-id ilmoitus-id nyt-185 nyt-180 815 4492320.265 3458642.657)

                  ;; 1. Lähetä valaistusilmoitus ikään kuin t-loikista jonoihin - sisältää väärät tiedot. Sijainti ja urakka ei täsmää
                  _ (jms/laheta (:itmf jarjestelma) tloik-apurit/+tloik-ilmoitusviestijono+ valaistusilmoitus)
                  ;; Ilmoituksen käsittelyyn menee hetki
                  ;; 2. Ei simuloida sähköpostin lähetystä urakkalle, koska se tapahtuu automaattisesti. Sen sijaan odotellaan hetki
                  _ (Thread/sleep 1000)

                  ;; Sähköposti on lähetetty ihan oikein Rest apin kautta ja kun urakoitsija sen saa, niin se vastaa, että väärä urakka
                  sposti_xml (aseta-xml-sahkopostin-sisalto (str "#[" vaara-urakka-id "/" ilmoitus-id "] Väärä urakka")
                               "[Väärä urakka] Ei kuulu meidän alueelle tämä homma." paivystajan-email toimenpiteen-vastausemail)
                  ;; 3. Urakoitsija vastaa "Väärä urakka"
                  vaara-urakka-email-vastaus (future (api-tyokalut/post-kutsu [spostin-vastaanotto-url] kayttaja portti sposti_xml nil true))
                  _ (Thread/sleep 1000)
                  _ (odota-ehdon-tayttymista #(realized? vaara-urakka-email-vastaus) "Saatiin väärä urakka vastaus." 5000)
                  _ (Thread/sleep 1000)
                  ;; 4. Harja käsittelee "Väärä urakka" viestin ja lähettää siitä T-LOIKille ilmoituksen
                  ;; Me voidaan tarkistaa integraatioviesteistä, että näin on todella tapahtunut
                  integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))]
              ;; 1. Varmistetaan, että jonoista saatiin ilmoitus toimenpiteestä
              (is "sisään" (:suunta (first integraatioviestit)))
              (is "JMS" (:siirtotyyppi (first integraatioviestit)))
              (is "tloik-ilmoituskuittausjono" (:osoite (first integraatioviestit)))
              (is (clojure.string/includes? (:sisalto (first integraatioviestit)) viesti-id))

              ;; 2. Varmistetaan, että Harja lähettää urakoitsijalle tietoa toimenpiteestä
              (is "ulos" (:suunta (second integraatioviestit)))
              (is "HTTP" (:siirtotyyppi (first integraatioviestit)))
              (is lokaali-sahkopostipalvelin (:osoite (first integraatioviestit)))
              (is (clojure.string/includes? (:sisalto (first integraatioviestit)) viesti-id))
              ;; Sähköpostipalvelimen lähettämä kuittaus xml, että sähköposti on välitetty urakoitsijalle
              (is "sisään" (:suunta (nth integraatioviestit 2)))
              (is "HTTP" (:siirtotyyppi (nth integraatioviestit 2)))
              (is lokaali-sahkopostipalvelin (:osoite (nth integraatioviestit 2)))
              (is (clojure.string/includes? (:sisalto (nth integraatioviestit 2)) "sahkoposti:kuittaus"))
              ;; Harjan kuittaus T-LOIKille, että ollaan välitetty viestit eteenpäin
              (is "ulos" (:suunta (nth integraatioviestit 3)))
              (is "JMS" (:siirtotyyppi (nth integraatioviestit 3)))
              (is "tloik-ilmoituskuittausjono" (:osoite (nth integraatioviestit 3)))
              (is (clojure.string/includes? (:sisalto (nth integraatioviestit 3)) viesti-id))


              ;; 3. Varmistetaan, että urakoitsija vastaa sähköpostiin "väärä urakka"
              (is "sisään" (:suunta (nth integraatioviestit 4)))
              (is "HTTP" (:siirtotyyppi (nth integraatioviestit 4)))
              (is lokaali-sahkopostipalvelin (:osoite (nth integraatioviestit 4)))
              (is (clojure.string/includes? (:sisalto (nth integraatioviestit 4)) "Väärä urakka"))

              ;; 4. Varmistetaan, että T-LOIKia informoitiin väärästä urakalasta
              (is "ulos" (:suunta (nth integraatioviestit 5)))
              (is "JMS" (:siirtotyyppi (nth integraatioviestit 5)))
              (is "tloik-ilmoituskuittausjono" (:osoite (nth integraatioviestit 5)))
              (is (clojure.string/includes? (:sisalto (nth integraatioviestit 5)) "<tyyppi>vaara-urakka</tyyppi>"))))))
