(ns harja.palvelin.integraatiot.vayla-rest.sahkoposti-api-test
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [clojure.string :as str]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.testi :refer :all]
            [harja.jms-test :refer [feikki-jms]]
            [harja.tyokalut.xml :as xml]
            [harja.tyokalut.xsl-fo :as xsl-fo]
            [clojure.data.zip.xml :as z]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.itmf :as itmf]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.integraatio :as integraatio]
            [harja.kyselyt.integraatiot :as integraatio-kyselyt]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.integraatiot.api.tyokalut :as api-tyokalut]
            [harja.palvelin.integraatiot.api.ilmoitukset :as api-ilmoitukset]
            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-testi-tyokalut]
            [harja.palvelin.integraatiot.jms.tyokalut :as jms-tyokalut]
            [harja.palvelin.integraatiot.vayla-rest.sahkoposti :as sahkoposti-api]
            [harja.palvelin.integraatiot.tloik.aineistot.toimenpidepyynnot :as aineisto-toimenpidepyynnot]
            [harja.palvelin.integraatiot.labyrintti.sms :as labyrintti]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.integraatiopisteet.http :as integraatiopiste-http]
            [harja.pvm :as pvm]
            [slingshot.slingshot :refer [try+ throw+]])
  (:import (org.postgis PGgeometry)
           (java.util UUID)))

(def ehdon-timeout 20000)
(def spostin-vastaanotto-url "/sahkoposti/toimenpidekuittaus")
(def kayttaja "destia")
(def kayttaja-yit "yit-rakennus")
(defonce asetukset {:itmf integraatio/itmf-asetukset
                    :sonja integraatio/sonja-asetukset})

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :api-ilmoitukset (component/using
                       (api-ilmoitukset/->Ilmoitukset)
                       [:http-palvelin :db :integraatioloki])
    :itmf (feikki-jms "itmf")
    :pdf-vienti (component/using
                  (pdf-vienti/luo-pdf-vienti)
                  [:http-palvelin])
    :sonja (feikki-jms "sonja")
    :api-sahkoposti (component/using
                              (sahkoposti-api/->ApiSahkoposti {:api-sahkoposti integraatio/api-sahkoposti-asetukset
                                                               :tloik {:toimenpidekuittausjono "Harja.HarjaToT-LOIK.Ack"}})
                              [:http-palvelin :db :integraatioloki :itmf])
    :labyrintti (component/using
                  (labyrintti/->Labyrintti "foo" "testi" "testi" (atom #{}))
                  [:db :http-palvelin :integraatioloki])
    :tloik (component/using
             (tloik-testi-tyokalut/luo-tloik-komponentti)
             [:db :itmf :integraatioloki :labyrintti :api-sahkoposti])))

(use-fixtures :each (fn [testit]
                      (binding [*aloitettavat-jmst* #{"itmf" "sonja"}
                                *lisattavia-kuuntelijoita?* true
                                *jms-kaynnistetty-fn* (fn []
                                                        (jms-tyokalut/itmf-jolokia-jono tloik-testi-tyokalut/+tloik-ilmoitusviestijono+ nil :purge)
                                                        (jms-tyokalut/itmf-jolokia-jono tloik-testi-tyokalut/+tloik-ilmoituskuittausjono+ nil :purge))]
                        (jarjestelma-fixture testit))))

(defn aseta-xml-sahkopostin-sisalto [otsikko sisalto lahettaja vastaanottaja]
  (-> "test/resurssit/api/sahkoposti/sahkoposti_malli.xml"
    slurp
    (.replace "__OTSIKKO__" otsikko)
    (.replace "__SISALTO__" sisalto)
    (.replace "__LAHETTAJA__" lahettaja)
    (.replace "__VASTAANOTTAJA__" vastaanottaja)))

(defn- luo-testi-pdf
  "Jotta liitettä voidaan testata, tarvitaan jotain sisältöä muka pdf liitteeseen."
  [param1 param2]
  (with-meta
    (xsl-fo/dokumentti
      {:margin {:left "5mm" :right "5mm" :top "5mm" :bottom "5mm"
                :body "0mm"}}

      [:fo:wrapper {:font-size 8}
       [:fo:block
        "Yllättävästä häiriöstä erikseen ilmoitus puhelimitse"
        " urakoitsijan linjalle 0200 21200"]])
    {:tiedostonimi (str "Tietyöilmoitus-jokutie.pdf")}))

(deftest laheta-tietyoilmoitus-sahkoposti-liitteen-kanssa
  (let [_ (pdf-vienti/rekisteroi-pdf-kasittelija! (:pdf-vienti jarjestelma) :tietyoilmoitus luo-testi-pdf)
        {pdf-bytet :tiedosto-bytet
         tiedostonimi :tiedostonimi} (pdf-vienti/luo-pdf (:pdf-vienti jarjestelma)
                                       :tietyoilmoitus kayttaja-yit 123456)
        sisalto {:viesti "Jotain tekstiä sisällöksi"
                 :pdf-liite pdf-bytet}
        ;; Lähetetään viesti ja tarkistetaan kuittaus
        viesti-id (str (UUID/randomUUID))
        vastaus (with-redefs [sahkoposti-api/muodosta-lahetys-uri (fn [_ _] "http://localhost:8084/api/sahkoposti")
                              integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ _ _ _]
                                                                     {:status 200
                                                                      :header "jotain"
                                                                      :body (onnistunut-sahkopostikuittaus viesti-id)})]
                  (sahkoposti/laheta-viesti-ja-liite! (:api-sahkoposti jarjestelma)
                    "lasse.lahettaja@example.com"
                    ["ville.vastaanottaja@example.com"]
                    "Otsikoidaan"
                    sisalto
                    tiedostonimi))]

    (is (not (nil? (:viesti-id vastaus))) "Ei saatu viesti-id:tä kuittauksessa")
    (is (not (nil? (:aika vastaus))) "Ei saatu aikaa kuittauksessa")
    (is (true? (:onnistunut vastaus)) "Kuittauksen mukaan viesitn lähetys epäonnistui")

    (let [db (:db jarjestelma)
          integraatio-id (integraatio-kyselyt/integraation-id db "api" "sahkoposti-ja-liite-lahetys")
          integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))
          integraatiotapahtumat (q-map (str "select id, integraatio, alkanut, paattynyt, lisatietoja, onnistunut, ulkoinenid FROM integraatiotapahtuma"))]
      ;; Varmistetaan, että integraatioviestejä on 2. Lähetys ja vastaanotto
      (is (= 2 (count integraatioviestit)))
      ;; Varmistetaan, että integraatiotapahtuma löytyy
      (is (= (:integraatio (first integraatiotapahtumat)) integraatio-id)))))

(deftest laheta-ihan-tavallinen-sahkoposti-onnistuu
  (let [viesti-id (str (UUID/randomUUID))
        integraatio-id (integraatio-kyselyt/integraation-id (:db jarjestelma) "api" "sahkoposti-lahetys")
        vastaus
        (try+ (future (with-redefs [sahkoposti-api/muodosta-lahetys-uri (fn [_ _] "http://localhost:8084/api/sahkoposti")
                                    integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ _ _ _]
                                                                           {:status 200
                                                                            :header "jotain"
                                                                            :body (onnistunut-sahkopostikuittaus viesti-id)})]
                        (sahkoposti/laheta-viesti! (:api-sahkoposti jarjestelma)
                          "seppoyit@example.org"
                          "pekka.paivystaja@example.org"
                          "Otsikko" "Nyt ois päällystyskode 22 valmiina tiellä 23/123/123/123")))
          (catch Throwable th
            (println (str "VIRHE: " (.getMessage th) " " (.getStackTrace th)))
            "VIRHE"))
        _ (odota-ehdon-tayttymista #(realized? vastaus) "Sähköpostin lähetys on yritetty." ehdon-timeout)
        integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))
        integraatiotapahtumat (q-map (str "select id, integraatio, alkanut, paattynyt, lisatietoja, onnistunut, ulkoinenid FROM integraatiotapahtuma"))]

    (is (< 0 (count integraatioviestit)) "Integraatio viestiä ei löydetty tietokannasta")
    (is (= integraatio-id (:integraatio (first integraatiotapahtumat))))
    (is (= true (:onnistunut (first integraatiotapahtumat))))))

;; Simuloi tilannetta, jossa sähköpostipalvelin ei vastaa
(deftest laheta-tavallinen-sahkoposti-epaonnistuu-api-palvelun-jalkeen
  (let [integraatio-id (integraatio-kyselyt/integraation-id (:db jarjestelma) "api" "sahkoposti-lahetys")
        sahkoposti-lahetys-url "http://localhost:8084/harja/api/sahkoposti/xml"
        viesti-id (str (UUID/randomUUID))
        vastaus (future (with-fake-http [{:url sahkoposti-lahetys-url :method :post} {:status 400
                                                                                      :header "jotain"
                                                                                      :body (epaonnistunut-sahkopostikuittaus viesti-id)}]
                          (sahkoposti/laheta-viesti! (:api-sahkoposti jarjestelma)
                            "seppoyit@example.org"
                            "pekka.paivystaja@example.org"
                            "Otsikko" "Nyt ois päällystyskode 22 valmiina tiellä 23/123/123/123")))
        _ (odota-ehdon-tayttymista #(realized? vastaus) "Sähköpostin lähetystä on yritetty." ehdon-timeout)
        integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))
        integraatiotapahtumat (q-map (str "select id, integraatio, alkanut, paattynyt, lisatietoja, onnistunut, ulkoinenid FROM integraatiotapahtuma"))]

    (is (= false @vastaus))
    (is (< 0 (count integraatioviestit)) "Integraatio viestiä ei löydetty tietokannasta")
    (is (= integraatio-id (:integraatio (first integraatiotapahtumat))))
    (is (= false (:onnistunut (first integraatiotapahtumat))))))

;; Simuloi tilannetta, jossa Harja lähettää onnistuneesti, mutta saadaan virheellinen kuittaus
(deftest laheta-tavallinen-sahkoposti-epaonnistuu-virheelliseen-kuittaukseen
  (let [integraatio-id (integraatio-kyselyt/integraation-id (:db jarjestelma) "api" "sahkoposti-lahetys")
        sahkoposti-lahetys-url "http://localhost:8084/harja/api/sahkoposti/xml"
        viesti-id (str (UUID/randomUUID))
        vastaus (future (with-fake-http [{:url sahkoposti-lahetys-url :method :post} {:status 400
                                                                                      :header "jotain"
                                                                                      :body (epaonnistunut-sahkopostikuittaus viesti-id)}]
                          (sahkoposti/laheta-viesti! (:api-sahkoposti jarjestelma)
                            "seppoyit@example.org"
                            "pekka.paivystaja@example.org"
                            "Otsikko" "Nyt ois päällystyskode 22 valmiina tiellä 23/123/123/123")))
        _ (odota-ehdon-tayttymista #(realized? vastaus) "Sähköpostin lähetystä on yritetty." ehdon-timeout)
        integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))
        integraatiotapahtumat (q-map (str "select id, integraatio, alkanut, paattynyt, lisatietoja, onnistunut, ulkoinenid FROM integraatiotapahtuma"))]

    (is (= false @vastaus))
    (is (< 0 (count integraatioviestit)) "Integraatio viestiä ei löydetty tietokannasta")
    (is (= integraatio-id (:integraatio (first integraatiotapahtumat))))
    (is (= false (:onnistunut (first integraatiotapahtumat))))))

(deftest laheta-tavallinen-sahkoposti-vaarallinen-kuittaus
  (let [integraatio-id (integraatio-kyselyt/integraation-id (:db jarjestelma) "api" "sahkoposti-lahetys")
        sahkoposti-lahetys-url "http://localhost:8084/harja/api/sahkoposti/xml"
        viesti-id (str (UUID/randomUUID))
        vaarallinen-kuittaus (str "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>\n"
                               (onnistunut-sahkopostikuittaus viesti-id))
        vastaus1 (future (with-fake-http [{:url sahkoposti-lahetys-url :method :post} vaarallinen-kuittaus]
                           (sahkoposti/laheta-viesti! (:api-sahkoposti jarjestelma)
                             "seppoyit@example.org"
                             "pekka.paivystaja@example.org"
                             "Otsikko" "Nyt ois päällystyskode 22 valmiina tiellä 23/123/123/123")))
        _ (odota-ehdon-tayttymista #(realized? vastaus1) "Sähköpostin lähetystä on yritetty." ehdon-timeout)
        vaarallinen-kuittaus2 (str "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"http://localhost:8080/\"> ]>\n"
                                (onnistunut-sahkopostikuittaus viesti-id))
        vastaus2 (future (with-fake-http [{:url sahkoposti-lahetys-url :method :post} vaarallinen-kuittaus2]
                           (sahkoposti/laheta-viesti! (:api-sahkoposti jarjestelma)
                             "seppoyit@example.org"
                             "pekka.paivystaja@example.org"
                             "Otsikko" "Nyt ois päällystyskode 22 valmiina tiellä 23/123/123/123")))
        _ (odota-ehdon-tayttymista #(realized? vastaus2) "Sähköpostin lähetystä on yritetty." ehdon-timeout)
        integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))
        integraatiotapahtumat (q-map (str "select id, integraatio, alkanut, paattynyt, lisatietoja, onnistunut, ulkoinenid FROM integraatiotapahtuma"))]

    (is (not (nil? @vastaus1)))
    (is (not (nil? @vastaus2)))
    (is (< 0 (count integraatioviestit)) "Integraatio viestiä ei löydetty tietokannasta")
    (is (= integraatio-id (:integraatio (first integraatiotapahtumat))))
    (is (= true (:onnistunut (first integraatiotapahtumat))))))

;; Simuloi tilannetta, jossa api ei vastaa
(deftest laheta-tavallinen-sahkoposti-epaonnistuu-api-kutsulla
  (let [viesti-id (str (UUID/randomUUID))
        integraatio-id (integraatio-kyselyt/integraation-id (:db jarjestelma) "api" "sahkoposti-lahetys")
        sahkoposti-lahetys-url "http://localhost:8084/harja/api/sahkoposti/xml"
        vastaus
        (sahkoposti/laheta-viesti! (:api-sahkoposti jarjestelma)
          "seppoyit@example.org"
          "pekka.paivystaja@example.org"
          "Otsikko" "Nyt ois päällystyskode 22 valmiina tiellä 23/123/123/123")
        ;_ (odota-ehdon-tayttymista #(realized? vastaus) "Sähköpostin lähetystä on yritetty." ehdon-timeout)
        integraatioviestit (q-map (str "select id, integraatiotapahtuma, suunta, sisaltotyyppi, siirtotyyppi, sisalto, otsikko, parametrit, osoite, kasitteleva_palvelin
          FROM integraatioviesti;"))
        integraatiotapahtumat (q-map (str "select id, integraatio, alkanut, paattynyt, lisatietoja, onnistunut, ulkoinenid FROM integraatiotapahtuma"))]
    (is (false? vastaus))))

(defn hae-integraatiotapahtumat-tietokannasta []
  (q-map (str "SELECT it.id, it.integraatio, it.alkanut, it.paattynyt, it.onnistunut, i.jarjestelma, i.nimi
            FROM integraatiotapahtuma it JOIN integraatio i ON i.id = it.integraatio order by it.id DESC;")))

;; Käytä sittenkin olemassaolevia koodeja. Eli korvaa (:sonja-email järjestelmä) viritelmä uudella rest-api viritelmällä itse palvelupäässä
#_(deftest paallystysurakka-ilmoittaa-tiemerkinnalle-kohteen-valmistumisen
    (let [vastaus (with-redefs [sahkoposti-api/sahkoposti-rajapinta-url "http://localhost:8084/api/sahkoposti"]
                    (sahkoposti/laheta-viesti! (:api-sahkoposti jarjestelma)
                      "seppoyit@example.org"
                      "pekka.paivystaja@example.org"
                      "Otsikko" "Nyt ois päällystyskode 22 valmiina tiellä 23/123/123/123"))
          _ (pr-str "******************* vastaus" (pr-str vastaus))
          ;; Tarkista, että integraatio -tauluun tulee oikeat merkinnät sähköpostin lähettämisestä
          integraatiot (hae-integraatiotapahtumat-tietokannasta)
          _ (println " ******************' integraatiot" integraatiot)
          ]
      (is (= "sahkoposti-lahetys" (:nimi (first integraatiot))))
      ;; :paattynyt arvo vectorissa kertoo, onko integraatio onnistunut
      (is (not (nil? (:paattynyt (first integraatiot)))))))

;; Urakanvalvoja löytää virheitä paikkauskohteista
;; Pitääköhän tämä tehdä jo olemassaolevalla koodilla?
#_(deftest urakanvalvoja-ilmoittaa-virheesta-paikkauskohteessa
    (let [sposti_xml (aseta-xml-sahkopostin-sisalto "Ilmoitan virheestä"
                       "Paikkauskohde \"Juupajoenkoukku\" on jäänyt kesken."
                       "seppoyit@example.org" "vayla@harja.fi")
          vastaus -- tähän itse sähköpostilogiikka
          ;; Tarkista, että integraatio -tauluun tulee oikeat merkinnät sähköpostin lähettämisestä
          integraatiot (hae-integraatiotapahtumat-tietokannasta)
          _ (println "paallystysurakka-ilmoittaa-tiemerkinnalle-kohteen-valmistumisen :: vastaus" (pr-str vastaus))
          ]
      (is (some #{"sahkoposti-lahetys"} (first integraatiot)))
      ;; Viides arvo vectorissa kertoo, onko integraatio onnistunut
      (is (true? (nth (first integraatiot) 4)))
      (is (= 1 0))))

(defn luo-urakalle-voimassa-oleva-paivystys [urakka-id]
  (u (str "INSERT INTO paivystys (vastuuhenkilo, alku, loppu, urakka, yhteyshenkilo)
           VALUES (TRUE, (now() :: DATE - 5) :: TIMESTAMP, (now() :: DATE + 5) :: TIMESTAMP,
           " urakka-id ", (SELECT id FROM yhteyshenkilo WHERE tyopuhelin = '0505555555' LIMIT 1));")))

;; Erilaisia kuittaustyyppejä kuittaustyyppi-pattern #"\[(Vastaanotettu|Aloitettu|Toimenpiteet aloitettu|Lopetettu|Lopetettu toimenpitein|Muutettu|Vastattu|Väärä urakka)\]")
(deftest vastaanota-sahkoposti-paivystaja-ilmoittaa-toimenpiteesta
  (let [urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
        _ (luo-urakalle-voimassa-oleva-paivystys urakka-id)
        ;; 1. Tee ilmoitus tietokantaan - simuloi tilannetta, jossa T-LOIKilta on tullut jonon kautta ilmoituksia
        _ (tloik-testi-tyokalut/tuo-ilmoitus)
        ilmoitus-id 123456789
        ;; 2. Simuloidaan tilanne, jossa urakoitsija vastaa sähköpostilla, että toimenpidepyyntö on tullut perille
        sposti_xml (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                     "[Vastaanotettu] Tutkitaan asiaa" "seppoyit@example.org" "vayla@harja.fi")
        vastaus (future (api-tyokalut/post-kutsu [spostin-vastaanotto-url] kayttaja-yit portti sposti_xml nil true))
        _ (odota-ehdon-tayttymista #(realized? vastaus) "Urakoitsija vastaa, että toimenpidepyyntö on tullut perille." ehdon-timeout)
        ilmoitustoimenpiteet (tloik-testi-tyokalut/hae-ilmoitustoimenpiteet-ilmoitusidlla ilmoitus-id)
        ilmoitus (tloik-testi-tyokalut/hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]
    (is (= "vastaanotettu" (:tila ilmoitus)))
    (is (= "vastaanotto" (:kuittaustyyppi (first ilmoitustoimenpiteet))) "Ilmoitustoimenpide ei ole valitys vaiheessa menossa.")))

(deftest testaa-toimenpidepyynto-koko-ketju
  "Simuloidaan tilanne, jossa T-LOIKista tulee toimenpidepyyntö. Ja sen koko prosessi käsitellään tässä, vaiheet:
  1. T-LOIK lähettää toimenpidepyynnön
  2. Harja kuittaa pyynnön vastaanotetuksi
  3. Harja selvittää mihin urakkaan toimenpidepyyntö kuuluu ja lähettää urakan päivystäjälle sähköpostia. REST-APIn kautta.
  4. Päivystäjä kuittaa viestin vastaanotetuksi.
  5. Harja päivittää toimenpiteen voimassaolevaksi
  6. Päivystäjä kuittaa toimenpiteen valmiiksi.
  7. Harja merkitsee kokonaisuudessaan toimenpidepyynnön valmiiksi ja käsitellyksi."
  (let [paivystajan-email "pekka.paivystaja@example.com"
        toimenpiteen-vastausemail "harja@vayla.fi"
        viestit (atom [])
        viesti-id (str (UUID/randomUUID))]
    (lisaa-kuuntelijoita! {"itmf" {"Harja.HarjaToT-LOIK.Ack" #(swap! viestit conj (.getText %))}})

    ;; Lisää urakalle kuvitteellinen päivystäjä
    (with-redefs [harja.kyselyt.yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat
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
                  harja.kyselyt.yhteyshenkilot/hae-urakan-paivystaja-sahkopostilla
                  (fn [db urakka-id lahettaja]
                    (list {:id 1
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
                  sahkoposti-api/muodosta-lahetys-uri (fn [_ _] "http://localhost:8084/api/sahkoposti")
                  integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ _ _ _]
                                                         {:status 200
                                                          :header "jotain"
                                                          :body (onnistunut-sahkopostikuittaus viesti-id)})]
      (let [urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
            ilmoitushaku (future (api-tyokalut/get-kutsu ["/api/urakat/" urakka-id "/ilmoitukset?odotaUusia=true"]
                                   kayttaja-yit portti))
            viesti-id (str (UUID/randomUUID))
            ilmoitus-id (rand-int 99999999)
            sijainti aineisto-toimenpidepyynnot/sijainti-oulun-alueella
            ilmoittaja aineisto-toimenpidepyynnot/ilmoittaja-xml]
        (async/<!! (async/timeout 2000))
        ;; Ilmoita jonon kautta toimenpidepyynnöstä urakoitsijalle
        (jms/laheta (:itmf jarjestelma) tloik-testi-tyokalut/+tloik-ilmoitusviestijono+ (aineisto-toimenpidepyynnot/toimenpidepyynto-sanoma viesti-id ilmoitus-id sijainti ilmoittaja))

        (odota-ehdon-tayttymista #(realized? ilmoitushaku) "Saatiin vastaus ilmoitushakuun." ehdon-timeout)
        (let [;; Huolimatta odota-ehdon-tayttymista funktion käyttämisestä, ilmoitukset eivät asappina tule kantaan.
              ;; Joten joudutaan vartomaan niitä vielä hetki
              _ (odota-ehdon-tayttymista
                  #(not (empty? (tloik-testi-tyokalut/hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)))
                  "Saatiin vastaus ilmoitushakuun." ehdon-timeout)
              ilmoitus (tloik-testi-tyokalut/hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]
          (is (= ilmoitus-id (:ilmoitus-id ilmoitus)))
          (is (= ilmoitus-id (:ilmoitus-id ilmoitus))
            "Viesti on käsitelty ja tietokannasta löytyy ilmoitus T-LOIK:n id:llä"))

        (let [{:keys [status body] :as vastaus} @ilmoitushaku
              ilmoitustoimenpide (tloik-testi-tyokalut/hae-ilmoitustoimenpide-ilmoitusidlla ilmoitus-id)]
          (is (= ilmoitus-id (:ilmoitusid ilmoitustoimenpide)) "Ilmoitustoimpenpide on olemassa")
          (is (= 200 status) "Ilmoituksen haku APIsta onnistuu")
          (is (= "valitys" (:kuittaustyyppi ilmoitustoimenpide)) "Ilmoitustoimenpide ei ole valitys vaiheessa menossa."))

        ;; Merkataan hommat aloitetuksi/selvittely alkaa
        (let [sposti_xml (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                           "[Aloitettu] Alan alan hommiin" paivystajan-email toimenpiteen-vastausemail)
              tyon-aloitus-vastaus (future (api-tyokalut/post-kutsu [spostin-vastaanotto-url] kayttaja-yit portti sposti_xml nil true))
              _ (Thread/sleep 1000)
              _ (odota-ehdon-tayttymista #(realized? tyon-aloitus-vastaus) "Saatiin tyon-aloitus-vastaus." ehdon-timeout)
              _ (odota-ehdon-tayttymista #(< 0 (count @viestit)) "Viestikuittaus on vastaanotettu." ehdon-timeout)
              ilmoitustoimenpiteet (tloik-testi-tyokalut/hae-ilmoitustoimenpiteet-ilmoitusidlla ilmoitus-id)
              ilmoitus (tloik-testi-tyokalut/hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]

          (is (= "aloitettu" (:tila ilmoitus)) "Ilmoituksen toimenpiteitä ei ole aloitettu.")
          ;; Pitäisi olla menossa ensimmäinen kuittaus eli ilmoitustoimenpide
          (is (= "aloitus" (:kuittaustyyppi (nth ilmoitustoimenpiteet 2))) "Ilmoitustoimenpide ei ole aloitettu -vaiheessa menossa."))

        ;; Merkataan toimenpiteet aloitetuksi
        (let [sposti_xml (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                           "[Toimenpiteet aloitettu] Puristettiin homma liikkeelle. Korjattavaa löytyi."
                           paivystajan-email toimenpiteen-vastausemail)
              toimenpiteet-aloitettu-vastaus (future (api-tyokalut/post-kutsu [spostin-vastaanotto-url] kayttaja-yit portti sposti_xml nil true))
              _ (odota-ehdon-tayttymista #(realized? toimenpiteet-aloitettu-vastaus) "Saatiin toimenpiteet-aloitettu-vastaus." ehdon-timeout)
              ;; T-LOIKille ei ITMF jonon kautta lähetetä toimenpiteiden aloituksesta sen kummemmin enää ilmoituksia
              ;; Eli jonosta saa löytyä vain 2 viestiä. Lopetuksesta tulee sitten lisää viestejä t-loikille välitettäväksi.
              _ (try
                  (odota-ehdon-tayttymista #(= 2 (count @viestit)) "Toimenpiteetkuittaus on vastaanotettu." ehdon-timeout)
                  (catch Exception e
                    (println "VIRHE testeissä! " (pr-str e))))
              ilmoitustoimenpiteet (tloik-testi-tyokalut/hae-ilmoitustoimenpiteet-ilmoitusidlla ilmoitus-id)
              ilmoitus (tloik-testi-tyokalut/hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]

          (is (not (nil? (:toimenpiteet-aloitettu ilmoitus))) "Ilmoituksen toimenpiteitä ei ole aloitettu.")
          ;; Pitäisi olla menossa toinen kuittaus eli ilmoitustoimenpide
          (is (= "aloitus" (:kuittaustyyppi (nth ilmoitustoimenpiteet 2))) "Ilmoitustoimenpide ei ole aloitettu -vaiheessa menossa."))

        ;; Merkataan toimenpiteet valmiiksi
        (let [sposti_xml (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                           "[Lopetettu toimenpitein] Pistettiin miehissä homma pakettiin"
                           paivystajan-email toimenpiteen-vastausemail)
              tyon-lopetus-vastaus (future (api-tyokalut/post-kutsu [spostin-vastaanotto-url] kayttaja-yit portti sposti_xml nil true))
              _ (odota-ehdon-tayttymista #(realized? tyon-lopetus-vastaus) "Saatiin tyon-lopetus-vastaus." ehdon-timeout)
              _ (odota-ehdon-tayttymista #(= 3 (count @viestit)) "Lopetuskuittaus on vastaanotettu." ehdon-timeout)
              ilmoitustoimenpiteet (tloik-testi-tyokalut/hae-ilmoitustoimenpiteet-ilmoitusidlla ilmoitus-id)
              ilmoitus (tloik-testi-tyokalut/hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]

          (is (= "lopetettu" (:tila ilmoitus)) "Ilmoituksen tila ei olekaan vaihtunut oikein.")
          (is (= "lopetus" (:kuittaustyyppi (nth ilmoitustoimenpiteet 3))) "Ilmoitustoimenpide kuittaustyyppi ei päivittynytkään oikein."))
        (tloik-testi-tyokalut/poista-ilmoitus ilmoitus-id)))))


(deftest vastaanota-vaarallinen-sahkoposti-xml-api-rajapintaan
  (let [urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
        _ (luo-urakalle-voimassa-oleva-paivystys urakka-id)
        vaarallinen-sisalto "<![CDATA[<IMG SRC=http://www.example.com/siteLogo.gif onmouseover=javascript:alert(‘XSS’);>]]>"
        ;; 1. Tee ilmoitus tietokantaan - simuloi tilannetta, jossa T-LOIKilta on tullut jonon kautta ilmoituksia
        _ (tloik-testi-tyokalut/tuo-ilmoitus)
        ilmoitus-id 123456789
        ;; 2. Simuloidaan tilanne, jossa urakoitsija vastaa sähköpostilla, että toimenpidepyyntö on tullut perille
        sposti_xml (aseta-xml-sahkopostin-sisalto (str "#[" urakka-id "/" ilmoitus-id "] Toimenpidepyynto")
                     vaarallinen-sisalto
                     "seppoyit@example.org"
                     "vayla@harja.fi")
        vastaus (future (api-tyokalut/post-kutsu [spostin-vastaanotto-url] kayttaja-yit portti sposti_xml nil true))
        _ (odota-ehdon-tayttymista #(realized? vastaus) "Urakoitsija vastaa, että toimenpidepyyntö on tullut perille." ehdon-timeout)
        ilmoitustoimenpiteet (tloik-testi-tyokalut/hae-ilmoitustoimenpiteet-ilmoitusidlla ilmoitus-id)
        ilmoitus (tloik-testi-tyokalut/hae-ilmoitus-ilmoitusidlla-tietokannasta ilmoitus-id)]
    (is (= "kuittaamaton" (:tila ilmoitus)))
    (is (= 0 (count ilmoitustoimenpiteet)) "Ilmoitustoimenpide ei ole valitys vaiheessa menossa.")))

(deftest vastaanota-haro-sahkoposti-xml-api-rajapintaan
  (with-redefs [sahkoposti-api/muodosta-lahetys-uri (fn [_ _] "http://localhost:8084/api/sahkoposti")
                integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ _ _ _]
                                                       {:status 200
                                                        :header "jotain"
                                                        :body (onnistunut-sahkopostikuittaus nil)})]
    (let [urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
          _ (luo-urakalle-voimassa-oleva-paivystys urakka-id)
          ;; Luo sähköpostin sisältö, jossa ei ole mitään hyvää. Pelkästään vaarallisia asioita
          haro-sisalto "<![CDATA[<IMG SRC=http://www.example.com/siteLogo.gif onmouseover=javascript:alert(‘XSS’);>]]>"
          sposti_xml (aseta-xml-sahkopostin-sisalto "Väärä otsikko"
                       haro-sisalto
                       "seppoyit@example.org"
                       "vayla@harja.fi")
          vastaus (future (api-tyokalut/post-kutsu [spostin-vastaanotto-url] kayttaja-yit portti sposti_xml nil true))
          _ (odota-ehdon-tayttymista #(realized? vastaus) "Urakoitsija vastaa, että toimenpidepyyntö on tullut perille." ehdon-timeout)
          _ (Thread/sleep 1500)
          ulos-lahtevat-integraatiotapahtumat (hae-ulos-lahtevat-integraatiotapahtumat)
          ;; Virheelliseen sähköpostiin tehty vastaus näkyy tietokannassa integraatiotapahtumana - ota tarkasteluun vain sähköpostin lähetys tapahtumat
          integraatio (last (filter #(when (str/includes? (:sisalto %) "sahkoposti:sahkoposti") %) ulos-lahtevat-integraatiotapahtumat))]
      (is (str/includes? (:sisalto integraatio) "www.liikennevirasto.fi"))
      (is (str/includes? (:sisalto integraatio) "<vastaanottaja>seppoyit@example.org</vastaanottaja>"))
      (is (str/includes? (:sisalto integraatio) "Urakka-id puuttuu."))
      (is (str/includes? (:sisalto integraatio) "Ilmoitus-id puuttuu."))
      (is (str/includes? (:sisalto integraatio) "Kuittaustyyppi puuttuu."))
      (is (str/includes? (:sisalto integraatio) "Jos lähetit toimenpidekuittauksen tai muun määrämuotoisen viestin, tarkista viestin muoto ja lähetä se uudelleen. HARJA ei osannut käsitellä lähettämäsi sähköpostiviestin sisältöä. Virhe: Viestistä ei löytynyt kuittauksen tietoja.")))))
