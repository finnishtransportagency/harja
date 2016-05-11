(ns harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]))

(def +xsd-polku+ "xsd/tloik/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn rakenna-urakka [urakka]
  (when urakka
    [:urakka
     [:id (:id urakka)]
     [:nimi (:nimi urakka)]
     [:tyyppi (:tyyppi urakka)]]))

(defn rakenna-urakoitsija [urakka]
  (when urakka
    [:urakoitsija
     [:nimi (:urakoitsija_nimi urakka)]
     [:ytunnus (:urakoitsija_ytunnus urakka)]]))

(defn rakenna-paivystaja [{:keys [etunimi sukunimi matkapuhelin sahkoposti]}]
  [:paivystaja
   [:etunimi etunimi]
   [:sukunimi sukunimi]
   [:matkapuhelin matkapuhelin]
   [:sahkoposti sahkoposti]])

(defn muodosta-viesti [viesti-id ilmoitus-id aika kuittaustyyppi urakka paivystajat virhe]
  [:harja:harja-kuittaus
   {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
   [:aika aika]
   [:kuittaustyyppi kuittaustyyppi]
   [:viestiId viesti-id]
   (when virhe
     [:virhe virhe])
   (when ilmoitus-id
     [:valitystiedot
      [:ilmoitusId ilmoitus-id]
      (rakenna-urakka urakka)
      (rakenna-urakoitsija urakka)
      [:paivystajat (for [p paivystajat] (rakenna-paivystaja p))]])])

(defn muodosta [viesti-id ilmoitus-id aika kuittaustyyppi urakka paivystajat virhe]
  (let [sisalto (muodosta-viesti viesti-id ilmoitus-id aika kuittaustyyppi urakka paivystajat virhe)
        xml (tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "harja-tloik.xsd" xml)
      xml
      (do
        (log/error "Kuittausta ei voida lähettää. Kuittaus XML ei ole validi.")
        nil))))
