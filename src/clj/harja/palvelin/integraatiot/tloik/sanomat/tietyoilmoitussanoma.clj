(ns harja.palvelin.integraatiot.tloik.sanomat.tietyoilmoitussanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.domain.tietyoilmoitukset :as tietyoilmoitus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.kyselyt.tietyoilmoitukset :as tietyoilmoitukset]
            [harja.geo :as geo])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/tloik/")

(def data (tietyoilmoitukset/hae-ilmoitus (:db harja.palvelin.main/harja-jarjestelma) 1))

(defn henkilo [avain ilmoittaja]
  [avain
   [:etunimi (::tietyoilmoitus/etunimi ilmoittaja)]
   [:sukunimi (::tietyoilmoitus/sukunimi ilmoittaja)]
   [:matkapuhelin (::tietyoilmoitus/matkapuhelin ilmoittaja)]
   [:sahkoposti (::tietyoilmoitus/sahkoposti ilmoittaja)]])

(defn urakka [data]
  [:urakka
   [:id (::tietyoilmoitus/urakka-id data)]
   [:nimi (::tietyoilmoitus/urakka-nimi data)]
   [:tyyppi (::tietyoilmoitus/urakkatyyppi data)]])

(defn urakoitsija [data]
  [:urakoitsija
   [:nimi (::tietyoilmoitus/urakoitsijan-nimi data)]
   ;; todo: lisättävä frontille ja kantaan
   [:ytunnus "2163026-3"]])

(defn urakoitsijan-yhteyshenkilot [data]
  [:urakoitsijan-yhteyshenkilot
   ;; todo: nämä pitäisi hakea FIM:stä, jos niitä ei ole suoraan kirjattu kantaan?
   [:urakoitsijan-yhteyshenkilo
    [:etunimi "Urho"]
    [:sukunimi "Urakoitsija"]
    [:matkapuhelin "+34592349342"]
    [:tyopuhelin "+34592349342"]
    [:sahkoposti "urho@example.com"]
    [:vastuuhenkilo "true"]]])

(defn tyotyypit [data]
  (into [:tyotyypit]
        (map #(vector :tyotyyppi
                      [:tyyppi (::tietyoilmoitus/tyyppi %)]
                      [:kuvaus (::tietyoilmoitus/kuvaus %)])
             (::tietyoilmoitus/tyotyypit data))))

(defn tilaaja [data]
  [:tilaaja
   [:nimi (::tietyoilmoitus/tilaajan-nimi data)]])

(defn tilaajan-yhteyshenkilot [data]
  ;; todo: nämä pitäisi hakea FIM:stä, jos niitä ei ole suoraan kirjattu kantaan?
  [:tilaajan-yhteyshenkilot
   [:tilaajan-yhteyshenkilo
    [:etunimi "Eija"]
    [:sukunimi "Elyläinen"]
    [:matkapuhelin "+34592349342"]
    [:tyopuhelin "+34592349342"]
    [:sahkoposti "eija@example.com"]
    [:vastuuhenkilo "true"]]])

(defn sijainti [data]
  (let [osoite (::tietyoilmoitus/osoite data)
        viivat (:lines (geo/pg->clj (::tierekisteri/geometria osoite)))
        alkukoordinaatit (first (:points (first viivat)))
        loppukoordinaatit (last (:points (last viivat)))]
    [:sijainti
     [:tierekisteriosoitevali
      [:tienumero (::tierekisteri/tie osoite)]
      [:alkuosa (::tierekisteri/aosa osoite)]
      [:alkuetaisyys (::tierekisteri/aet osoite)]
      (when (::tierekisteri/losa osoite)
        [:loppuosa (::tierekisteri/losa osoite)])
      (when
        (::tierekisteri/let osoite)
        [:loppuetaisyys (::tierekisteri/let osoite)])
      ;; todo: hae erikseen kannasta
      [:karttapvm "2016-01-01"]]
     [:alkukoordinaatit
      [:x (first alkukoordinaatit)]
      [:y (second alkukoordinaatit)]]
     (when loppukoordinaatit
       [:loppukoordinaatit
        [:x (first loppukoordinaatit)]
        [:y (second loppukoordinaatit)]])
     ;; todo: pitää laskea erikseen sijannista
     [:pituus "1000.00"]
     [:tienNimi (::tietyoilmoitus/tien-nimi data)]
     [:kunnat (::tietyoilmoitus/kunnat data)]
     [:alkusijainninKuvaus (::tietyoilmoitus/alkusijainnin-kuvaus data)]
     [:loppusijainninKuvaus (::tietyoilmoitus/loppusijainnin-kuvaus data)]]))

(defn ajankohta [data]
  [:ajankohta
   [:alku (xml/datetime->gmt-0-pvm (::tietyoilmoitus/alku data))]
   [:loppu (xml/datetime->gmt-0-pvm (::tietyoilmoitus/loppu data))]])

(defn tyoajat [data]
  (into [:tyoajat]
        (map
          (fn [tyoaika]
            (vector :tyoaika
                    [:alku (xml/datetime->gmt-0-aika (::tietyoilmoitus/alkuaika tyoaika))]
                    [:loppu (xml/datetime->gmt-0-aika (::tietyoilmoitus/loppuaika tyoaika))]
                    (into [:paivat] (map #(vector :paiva %) (::tietyoilmoitus/paivat tyoaika)))))
          (::tietyoilmoitus/tyoajat data))))

(defn lista [lista-avain arvoavain arvo-fn data]
  (into [lista-avain]
        (map #(vector arvoavain (arvo-fn %))
             data)))

(defn vaikutukset [data]
  [:vaikutukset
   [:vaikutussuunta (::tietyoilmoitus/vaikutussuunta data)]
   [:kaistajarjestelyt (::tietyoilmoitus/jarjestely (::tietyoilmoitus/kaistajarjestelyt data))]
   (into [:nopeusrajoitukset]
         (map #(vector :nopeusrajoitus
                       [:rajoitus (::tietyoilmoitus/rajoitus %)]
                       [:matka (::tietyoilmoitus/matka %)])
              (::tietyoilmoitus/nopeusrajoitukset data)))
   (into [:tienpinnat]
         (map #(vector :tienpinta
                       [:pintamateriaali (::tietyoilmoitus/materiaali %)]
                       [:matka (::tietyoilmoitus/matka %)])
              (::tietyoilmoitus/tienpinnat data)))
   [:kiertotie
    [:mutkaisuus "loivatMutkat"]
    [:tienpinnat
     [:tienpinta
      [:pintamateriaali "paallystetty"]
      [:matka "100"]]]]
   [:liikenteenohjaus
    [:ohjaus "ohjataanVuorotellen"]
    [:ohjaaja "liikenteenohjaaja"]]
   [:arvioitu-viivastys
    [:normaali-liikenteessa "100"]
    [:ruuhka-aikana "100"]]
   [:ajoneuvorajoitukset
    [:max-korkeus "100"]
    [:max-leveys "100"]
    [:max-pituus "100"]
    [:max-paino "100"]]
   [:huomautukset
    [:huomautus "avotuli"]]
   [:pysaytykset
    [:pysaytetaan-ajoittain "true"]
    [:tie-ajoittain-suljettu "false"]
    [:aikataulu
     [:alkaen "2017-04-19T17:38:57+03:00"]
     [:paattyen "2008-08-06T17:17:00+03:00"]]]])

(defn muodosta-viesti [data viesti-id]
  (let [ilmoittaja (::tietyoilmoitus/ilmoittaja data)]
    [:harja:tietyoilmoitus
     {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
     [:viestiId viesti-id]
     [:harja-tietyoilmoitus-id (::tietyoilmoitus/id data)]
     ;; todo: lisätään möyhemmin
     ;; [:tloik-tietyoilmoitus-id "234908234"]
     ;; todo: päättele
     [:toimenpide "uusi"]
     [:kirjattu (xml/datetime->gmt-0-pvm (::muokkaustiedot/luotu data))]
     (henkilo :ilmoittaja ilmoittaja)
     (urakka data)
     (urakoitsija data)
     (urakoitsijan-yhteyshenkilot data)
     (tilaaja data)
     (tilaajan-yhteyshenkilot data)
     (tyotyypit data)
     ;; todo: lisättävä kantaan
     [:luvan-diaarinumero "09864321"]
     (sijainti data)
     (ajankohta data)
     (tyoajat data)
     (vaikutukset data)
     [:lisatietoja "Lisätietoja"]]))

(defn muodosta [data viesti-id]
  (let [sisalto (muodosta-viesti data viesti-id)
        xml (xml/tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml)
      xml
      (let [virheviesti (format "Ilmoitustoimenpidettä ei voida lähettää. XML ei ole validia. XML: %s." xml)]
        (log/error virheviesti)
        (throw+ {:type virheet/+invalidi-xml+
                 :virheet [{:koodi :invalidi-ilmoitustoimenpide-xml
                            :viesti virheviesti}]})))))
