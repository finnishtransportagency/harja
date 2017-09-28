(ns harja.palvelin.integraatiot.tloik.sanomat.tietyoilmoitussanoma
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [hiccup.core :refer [html]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.domain.tietyoilmoitus :as tietyoilmoitus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.tierekisteri :as tierekisteri]
            [harja.kyselyt.tietyoilmoitukset :as tietyoilmoitukset]
            [harja.geo :as geo])
  (:use [slingshot.slingshot :only [throw+]]))

(def +xsd-polku+ "xsd/tloik/")

(defn- henkilo [avain ilmoittaja]
  [avain
   [:etunimi (::tietyoilmoitus/etunimi ilmoittaja)]
   [:sukunimi (::tietyoilmoitus/sukunimi ilmoittaja)]
   [:matkapuhelin (::tietyoilmoitus/matkapuhelin ilmoittaja)]
   [:sahkoposti (::tietyoilmoitus/sahkoposti ilmoittaja)]])

(defn- urakka [data]
  [:urakka
   [:id (::tietyoilmoitus/urakka-id data)]
   [:nimi (::tietyoilmoitus/urakkan-nimi data)]
   [:tyyppi (::tietyoilmoitus/urakkatyyppi data)]])

(defn- urakoitsija [data]
  [:urakoitsija
   [:nimi (::tietyoilmoitus/urakoitsijan-nimi data)]
   [:ytunnus (::tietyoilmoitus/urakoitsijan-ytunnus data)]])

(defn- urakoitsijan-yhteyshenkilot [data]
  (when (::tietyoilmoitus/urakoitsijayhteyshenkilo data)
    [:urakoitsijan-yhteyshenkilot
     (conj (henkilo :urakoitsijan-yhteyshenkilo (::tietyoilmoitus/urakoitsijayhteyshenkilo data))
           [:vastuuhenkilo "true"])]))

(defn- tilaaja [data]
  [:tilaaja
   [:nimi (::tietyoilmoitus/tilaajan-nimi data)]])

(defn- tilaajan-yhteyshenkilot [data]
  (when (::tietyoilmoitus/tilaajayhteyshenkilo data)
    [:tilaajan-yhteyshenkilot
     (conj (henkilo :tilaajan-yhteyshenkilo (::tietyoilmoitus/tilaajayhteyshenkilo data))
           [:vastuuhenkilo "true"])]))

(defn- tyotyypit [data]
  (when (::tietyoilmoitus/tyotyypit data)
    (into [:tyotyypit]
          (map #(vector :tyotyyppi
                        [:tyyppi (::tietyoilmoitus/tyyppi %)]
                        [:kuvaus (::tietyoilmoitus/kuvaus %)])
               (::tietyoilmoitus/tyotyypit data)))))

(defn- sijainti [data]
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
      [:karttapvm (xml/timestamp->xml-xs-date (:karttapvm data))]]
     [:alkukoordinaatit
      [:x (first alkukoordinaatit)]
      [:y (second alkukoordinaatit)]]
     (when loppukoordinaatit
       [:loppukoordinaatit
        [:x (first loppukoordinaatit)]
        [:y (second loppukoordinaatit)]])
     (when (:pituus data)
       [:pituus (:pituus data)])
     [:tienNimi (::tietyoilmoitus/tien-nimi data)]
     [:kunnat (::tietyoilmoitus/kunnat data)]
     [:alkusijainninKuvaus (::tietyoilmoitus/alkusijainnin-kuvaus data)]
     [:loppusijainninKuvaus (::tietyoilmoitus/loppusijainnin-kuvaus data)]]))

(defn- ajankohta [data]
  [:ajankohta
   [:alku (xml/datetime->gmt-0-pvm (::tietyoilmoitus/alku data))]
   [:loppu (xml/datetime->gmt-0-pvm (::tietyoilmoitus/loppu data))]])

(defn- tyoajat [data]
  (when (::tietyoilmoitus/tyoajat data)
    (into [:tyoajat]
          (map
            (fn [tyoaika]
              (vector :tyoaika
                      [:alku (xml/datetime->gmt-0-aika (::tietyoilmoitus/alkuaika tyoaika))]
                      [:loppu (xml/datetime->gmt-0-aika (::tietyoilmoitus/loppuaika tyoaika))]
                      (into [:paivat] (map #(vector :paiva %) (::tietyoilmoitus/paivat tyoaika)))))
            (::tietyoilmoitus/tyoajat data)))))

(defn- lista [lista-avain arvoavain arvo-fn data]
  (into [lista-avain]
        (map #(vector arvoavain (arvo-fn %))
             data)))

(defn- tienpinnat [pinnat]
  (when pinnat
    (into [:tienpinnat]
          (map #(vector :tienpinta
                        [:pintamateriaali (::tietyoilmoitus/materiaali %)]
                        [:matka (::tietyoilmoitus/matka %)])
               pinnat))))

(defn- nopeusrajoitukset [data]
  (when (::tietyoilmoitus/nopeusrajoitukset data)
    (into [:nopeusrajoitukset]
          (map #(vector :nopeusrajoitus
                        [:rajoitus (::tietyoilmoitus/rajoitus %)]
                        [:matka (::tietyoilmoitus/matka %)])
               (::tietyoilmoitus/nopeusrajoitukset data)))))

(defn- kiertotie [data]
  (when (::tietyoilmoitus/kiertotien-mutkaisuus data)
    [:kiertotie
     [:mutkaisuus (::tietyoilmoitus/kiertotien-mutkaisuus data)]
     (tienpinnat (::tietyoilmoitus/kiertotienpinnat data))]))

(defn- liikenteenohjaus [data]
  (let [ohjaus (::tietyoilmoitus/liikenteenohjaus data)
        ohjaaja (::tietyoilmoitus/liikenteenohjaaja data)]
    (when (or ohjaus ohjaaja)
      [:liikenteenohjaus
       (when ohjaus
         [:ohjaus ohjaus])
       (when ohjaaja
         [:ohjaaja ohjaaja])])))

(defn- viivastykset [data]
  (let [normaali-liikenteessa (::tietyoilmoitus/viivastys-normaali-liikenteessa data)
        ruuhka-aikana (::tietyoilmoitus/viivastys-ruuhka-aikana data)]
    (when (or normaali-liikenteessa ruuhka-aikana)
      [:arvioitu-viivastys
       (when normaali-liikenteessa
         [:normaali-liikenteessa normaali-liikenteessa])
       (when ruuhka-aikana
         [:ruuhka-aikana ruuhka-aikana])])))

(defn- rajoitukset [data]
  (let [rajoitukset (::tietyoilmoitus/ajoneuvorajoitukset data)
        korkeus (::tietyoilmoitus/max-korkeus rajoitukset)
        leveys (::tietyoilmoitus/max-leveys rajoitukset)
        pituus (::tietyoilmoitus/max-pituus rajoitukset)
        paino (::tietyoilmoitus/max-paino rajoitukset)]
    (when (or korkeus leveys pituus paino)
      [:ajoneuvorajoitukset
       (when korkeus
         [:max-korkeus (::tietyoilmoitus/max-korkeus rajoitukset)])
       (when leveys
         [:max-leveys (::tietyoilmoitus/max-leveys rajoitukset)])
       (when pituus
         [:max-pituus (::tietyoilmoitus/max-pituus rajoitukset)])
       (when paino
         [:max-paino (::tietyoilmoitus/max-paino rajoitukset)])])))

(defn- huomautukset [data]
  (when (::tietyoilmoitus/huomautukset data)
    (into [:huomautukset]
          (map
            #(vector :huomautus %)
            (::tietyoilmoitus/huomautukset data)))))

(defn- pysaytykset [data]
  (let [ajoittaiset-pysaytykset (::tietyoilmoitus/ajoittaiset-pysaytykset data)
        ajoittain-suljettu-tie (::tietyoilmoitus/ajoittain-suljettu-tie data)
        alku (::tietyoilmoitus/pysaytysten-alku data)
        loppu (::tietyoilmoitus/pysaytysten-loppu data)]
    (when (or ajoittaiset-pysaytykset ajoittain-suljettu-tie alku loppu)
      [:pysaytykset
       (when ajoittaiset-pysaytykset
         [:pysaytetaan-ajoittain (str ajoittaiset-pysaytykset)])
       (when ajoittain-suljettu-tie
         [:tie-ajoittain-suljettu (str ajoittain-suljettu-tie)])
       [:aikataulu
        (when alku
          [:alkaen (xml/datetime->gmt-0-pvm alku)])
        (when loppu
          [:paattyen (xml/datetime->gmt-0-pvm loppu)])]])))

(defn- vaikutukset [data]
  [:vaikutukset
   [:vaikutussuunta (::tietyoilmoitus/vaikutussuunta data)]
   (when
     (::tietyoilmoitus/kaistajarjestelyt data)
     [:kaistajarjestelyt (::tietyoilmoitus/jarjestely (::tietyoilmoitus/kaistajarjestelyt data))])
   (nopeusrajoitukset data)
   (tienpinnat (::tietyoilmoitus/tienpinnat data))
   (kiertotie data)
   (liikenteenohjaus data)
   (viivastykset data)
   (rajoitukset data)
   (huomautukset data)
   (pysaytykset data)])

(defn- muodosta-viesti [data viesti-id]
  (let [ilmoittaja (::tietyoilmoitus/ilmoittaja data)]
    [:harja:tietyoilmoitus
     {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
     [:viestiId viesti-id]
     [:harja-tietyoilmoitus-id (::tietyoilmoitus/id data)]
     (when (::tietyoilmoitus/paatietyoilmoitus data)
       [:harja-paatietyoilmoitus-id (::tietyoilmoitus/paatietyoilmoitus data)])
     [:toimenpide (if (:uusi? data) "uusi" "muokkaus")]
     [:kirjattu (xml/datetime->gmt-0-pvm (::muokkaustiedot/luotu data))]
     (henkilo :ilmoittaja ilmoittaja)
     (urakka data)
     (urakoitsija data)
     (urakoitsijan-yhteyshenkilot data)
     (tilaaja data)
     (tilaajan-yhteyshenkilot data)
     (tyotyypit data)
     (when (::tietyoilmoitus/luvan-diaarinumero data)
       [:luvan-diaarinumero (::tietyoilmoitus/luvan-diaarinumero data)])
     (sijainti data)
     (ajankohta data)
     (tyoajat data)
     (vaikutukset data)
     [:lisatietoja (::tietyoilmoitus/lisatietoja data)]]))

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
