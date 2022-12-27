(ns harja.palvelin.integraatiot.tloik.sahkoposti-test
  (:require [harja.palvelin.integraatiot.tloik.sahkoposti :as sut]
            [clojure.core.async :as async]
            [taoensso.timbre :as log]
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
(def odota-tausta-ajoa 1000)

(def jarjestelma-fixture
  (laajenna-integraatiojarjestelmafixturea
    kayttaja
    :itmf (feikki-jms "itmf")
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

(deftest tarkista-kuittauksen-vastaanotto-sahkopostilla
  (with-redefs
    [sahkoposti-api/muodosta-lahetys-uri (fn [_ _] "http://localhost:8084/api/sahkoposti")
     integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ _ _ _]
                                            {:status 200
                                             :header "jotain"
                                             :body onnistunut-kuittaus})]
    (let [urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
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
            integraatioviestit (hae-kaikki-integraatioviestit)

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
        ;; Tarkista, että saatiin virka-apupyyntö
        (is (clojure.string/includes? (:sisalto (first integraatioviestit)) "virkaApupyynto"))
        ;; Tarkista että viesti lähtee ulos ja eteenpäin urakan päivystäjälle
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 2)) "VIRKA-APUPYYNTÖ"))

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

(deftest vaara-urakka-sahkoposti-test
  "Jos viesti on ensin mennyt väärälle urakalle ja tuo urakka on kuitannut viestin kuittauksella \"Väärä urakka\" ja sen jälkeen Liikennekeskus siirtänyt viestin toiselle urakalle niin tällöin viesti näkyy pelkästään Harjaselaimessa. Siitä ei tule sähköposteja lainkaan.
  Simuloidaan siis tilanne näin:
  1. Lähetetään toimenpidepyyntö
  2. jonka perusteella Harja lähettää rest-apin kautta sähköpostia väärälle urakalle.
  3. Väärän urakan päivystäjä vastaa kuittaus-sähköpostilla 'vaara-urakka'
  4. Harja lähettää 'vaara-urakka' tyyppisen viesitn T-loikin jonolle.
  5. T-LOIK lähettää saman ilmoituksen uudestaan eri koordinaateilla (Tämän hetken urakan päättely perustuu pelkästään sijaintiin)
  6. Harja lähettää ilmoituksen oikealle päivystäjälle
  "
  (let [paivystajan-email "pekka.paivystaja@example.com"
        toimenpiteen-vastausemail "harja@vayla.fi"
        lokaali-sahkopostipalvelin "http://localhost:8084/api/sahkoposti"
        ;; Asetetaan koordinaatit, jotka ovat millilleen kahden urakan välissä, joten on potentiaalinen mahdollisuus
        ;; että löydetään väärä urakka, jolle ilmoitukset lähetetään
        x 4492320.265
        y 3458642.657
        eri-x 4492320.300
        vaara-urakka-id (hae-urakan-id-nimella "Rovaniemen MHU testiurakka (1. hoitovuosi)")
        oikea-urakka-id (hae-urakan-id-nimella "Oulun valaistuksen palvelusopimus 2013-2050")]

    (with-redefs
      [sahkoposti-api/muodosta-lahetys-uri (fn [_ _] lokaali-sahkopostipalvelin)
       integraatiopiste-http/tee-http-kutsu (fn [_ _ _ _ _ _ _ _ _ _ _]
                                              {:status 200
                                               :header "jotain"
                                               :body onnistunut-kuittaus})
       ;; Lokaalisti ei oikein ole tilanteita, joissa tulee 2 urakkaa samalle koordinaatille, joten feikataan tilanne
       harja.palvelin.palvelut.urakat/hae-urakka-id-sijainnilla (fn [db urakkatyyppi x y]
                                                                  ;; Vähä kämänen tapa pakottaa urakka vaihtumaan
                                                                  (if (and (= x 4492320.265)
                                                                        (= y 3458642.657))
                                                                    vaara-urakka-id
                                                                    oikea-urakka-id))
       ;; Feikatulle rovaniemen/oulun valaistus urakalle ei ole lokaalikannassa päivystäjiä, joten feikataan sekin
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
       ;; Feikatulle rovaniemen/oulun valaistus urakalle ei ole lokaalikannassa päivystäjiä, joten feikataan sekin, uudestaan
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
            viesti-id (str (UUID/randomUUID))
            ilmoitus-id 987654321
            nyt-185 (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                      (t/minus (t/now) (t/minutes 185)))
            nyt-180 (df/unparse (df/formatter "yyyy-MM-dd'T'HH:mm:ss" (t/time-zone-for-id "Europe/Helsinki"))
                      (t/minus (t/now) (t/minutes 180)))
            valaistusilmoitus (tloik-apurit/testi-valaistusilmoitus-sanoma-eri-sijaintiin viesti-id ilmoitus-id nyt-185 nyt-180 815 x y)

            ;; 1. Lähetä valaistusilmoitus ikään kuin t-loikista jonoihin - sisältää väärät tiedot. Sijainti ja urakka ei täsmää
            _ (jms/laheta (:itmf jarjestelma) tloik-apurit/+tloik-ilmoitusviestijono+ valaistusilmoitus)

            ;; 2. Ei simuloida sähköpostin lähetystä urakkalle, koska se tapahtuu automaattisesti. Sen sijaan odotellaan hetki
            _ (async/<!! (async/timeout odota-tausta-ajoa)) ;; Ilmoituksen käsittelyyn menee hetki
            ;; Varmistetaan, että ilmoitus löytyy tietokannasta
            ilmoitus-db (first (q-map (format "select id, urakka from ilmoitus where ilmoitusid = %s" ilmoitus-id)))

            ;; Sähköposti on lähetetty ihan oikein Rest apin kautta ja kun urakoitsija sen saa, niin se vastaa, että väärä urakka
            sposti_xml (aseta-xml-sahkopostin-sisalto
                         (str "#[" vaara-urakka-id "/" ilmoitus-id "] Väärä urakka")
                         "[Väärä urakka] Ei kuulu meidän alueelle tämä homma."
                         paivystajan-email
                         toimenpiteen-vastausemail)
            ;; 3. Urakoitsija vastaa "Väärä urakka"
            vaara-urakka-email-vastaus (future (api-tyokalut/post-kutsu [spostin-vastaanotto-url] kayttaja portti sposti_xml nil true))
            _ (odota-ehdon-tayttymista #(realized? vaara-urakka-email-vastaus) "Saatiin väärä urakka vastaus." 5000)
            _ (async/<!! (async/timeout odota-tausta-ajoa))

            ;; 4. Harja käsittelee "Väärä urakka" viestin ja lähettää siitä T-LOIKille ilmoituksen
            ;; Me voidaan tarkistaa integraatioviesteistä, että näin on todella tapahtunut
            uusi-valaistusilmoitus (tloik-apurit/testi-valaistusilmoitus-sanoma-eri-sijaintiin viesti-id ilmoitus-id nyt-185 nyt-180 815 eri-x y)
            _ (jms/laheta (:itmf jarjestelma) tloik-apurit/+tloik-ilmoitusviestijono+ uusi-valaistusilmoitus)
            ;; Koska ci-putkella saattaa kestää hetki, ennenkuin kaikki taustaprosessit on simuloitu, niin odotellaan vielä hetki
            tarkista-ilmoituksen-saapuminen (fn []
                                              (let [viestit (hae-kaikki-integraatioviestit)]
                                                (< 8 (count viestit))))
            _ (odota-ehdon-tayttymista #(tarkista-ilmoituksen-saapuminen) "Saatiin vastaus ilmoitushakuun." 10000)
            _ (async/<!! (async/timeout 5000))
            integraatioviestit (hae-kaikki-integraatioviestit)
            ;; Varmistetaan, että ilmoitus löytyy tietokannasta, ja tarkistetaan sen urakka
            uusi-ilmoitus-db (first (q-map (format "select id, urakka from ilmoitus where ilmoitusid = %s" ilmoitus-id)))]

        ;; Varmistetaan, että ilmoitus on tallentunut kantaan
        (is (not (nil? ilmoitus-db)))
        (is (= vaara-urakka-id (:urakka ilmoitus-db)))
        ;; Ja varmistetaan, että sen urakka on päivitetty sen jälkeen kun T-LOIK on korjannut ilmoituksen sijainnin
        (is (= oikea-urakka-id (:urakka uusi-ilmoitus-db)))

        ;; 1. Varmistetaan, että jonoista saatiin ilmoitus toimenpiteestä, joka on valaistusurakalle tarkoitettu ja kuuluu väärään urakkaan
        (is (= "sisään" (:suunta (first integraatioviestit))))
        (is (= "JMS" (:siirtotyyppi (first integraatioviestit))))
        (is (= "tloik-ilmoituskuittausjono" (:osoite (first integraatioviestit))))
        (is (clojure.string/includes? (:sisalto (first integraatioviestit)) viesti-id))
        (is (clojure.string/includes? (:sisalto (first integraatioviestit)) "valaistus"))
        (is (clojure.string/includes? (:sisalto (first integraatioviestit)) "Hailuodossa"))
        (is (= 1 (:integraatiotapahtuma (first integraatioviestit))))

        ;; 2. Harja ilmoittaa T-LOIKILLE, että on vastaanottanut viestin
        (is (= 1 (:integraatiotapahtuma (second integraatioviestit))))
        (is (= "ulos" (:suunta (second integraatioviestit))))
        (is (= "JMS" (:siirtotyyppi (second integraatioviestit))))
        ;(is lokaali-sahkopostipalvelin (:osoite (first integraatioviestit)))
        (is (clojure.string/includes? (:sisalto (second integraatioviestit)) viesti-id))

        ;; 3. Sähköpostipalvelimelle lähetettävä xml joka on viesti siis urakoitsijan päivystäjälle
        (is (= 2 (:integraatiotapahtuma (nth integraatioviestit 2))))
        (is (= "ulos" (:suunta (nth integraatioviestit 2))))
        (is (= "HTTP" (:siirtotyyppi (nth integraatioviestit 2))))
        ;(is lokaali-sahkopostipalvelin (:osoite (nth integraatioviestit 3)))
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 2)) "sahkoposti:sahkoposti"))

        ;; 4. Sähköpostipalvelimen kuittaus, että se on saanut viestin välitettäväksi
        (is (= 2 (:integraatiotapahtuma (nth integraatioviestit 3))))
        (is (= "sisään" (:suunta (nth integraatioviestit 3))))
        (is (= "HTTP" (:siirtotyyppi (nth integraatioviestit 3))))
        ;(is lokaali-sahkopostipalvelin (:osoite (nth integraatioviestit 3)))
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 3)) "sahkoposti:kuittaus"))

        ;; 5. Urakoitsijan päivystäjä vastaa, että tämä toimenpidepyyntö on tullut väärään urakkaan
        (is (= "sisään" (:suunta (nth integraatioviestit 4))))
        (is (= "HTTP" (:siirtotyyppi (nth integraatioviestit 4))))
        (is lokaali-sahkopostipalvelin (:osoite (nth integraatioviestit 4)))
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 4)) "Väärä urakka"))

        ;; 6. Varmistetaan, että "turha" kuittauslogi löytyy tietokannasta. Onnistuneissa sähköpostin vastaanotoissa ei lähetetä oikeasti sähköpostiin kuittausta, kuten jonohommissa tapahtuu
        (is (= "ulos" (:suunta (nth integraatioviestit 5))))
        (is (= "HTTP" (:siirtotyyppi (nth integraatioviestit 5))))
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 5)) "sahkoposti:kuittaus"))

        ;; 7. Harjan kuittaus T-LOIKille, että ollaan välitetty viestit eteenpäin
        (is (= "ulos" (:suunta (nth integraatioviestit 6))))
        (is (= "JMS" (:siirtotyyppi (nth integraatioviestit 6))))
        ;Varmistetaan, että T-LOIKia informoitiin väärästä urakasta
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 6)) "<tyyppi>vaara-urakka</tyyppi>"))

        ;; 8. T-LOIK lähettää ilmoituksen uudestaan
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 7)) "harja:ilmoitus"))
        (is (= "sisään" (:suunta (nth integraatioviestit 7))))
        (is (= "JMS" (:siirtotyyppi (nth integraatioviestit 7))))
        (is (= "tloik-ilmoituskuittausjono" (:osoite (nth integraatioviestit 7))))
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 7)) viesti-id))

        ;; 9. Harja ilmoittaa T-LOIKIlle, että toimenpideviesti tullaan välittämään oikealle urakalle sähköpostilla
        (is (= "ulos" (:suunta (nth integraatioviestit 8))))
        (is (= "JMS" (:siirtotyyppi (nth integraatioviestit 8))))
        (is lokaali-sahkopostipalvelin (:osoite (nth integraatioviestit 8)))
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 8)) (str ilmoitus-id)))

        ;; 10. Harja ilmoittaa toiselle päivystäjälle saapuneesta ilmoituksesta sähköpostitse
        (is (= "ulos" (:suunta (nth integraatioviestit 9))))
        (is (= "HTTP" (:siirtotyyppi (nth integraatioviestit 9))))
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 9)) (format "[%s/%s]" oikea-urakka-id ilmoitus-id)))

        ;; 11. Sähköpostipalvelin kuittaa, että on saanut sähköpostin välitettäväkseen
        (is (= "sisään" (:suunta (nth integraatioviestit 10))))
        (is (= "HTTP" (:siirtotyyppi (nth integraatioviestit 10))))
        (is (clojure.string/includes? (:sisalto (nth integraatioviestit 10)) "sahkoposti:kuittaus"))))))
