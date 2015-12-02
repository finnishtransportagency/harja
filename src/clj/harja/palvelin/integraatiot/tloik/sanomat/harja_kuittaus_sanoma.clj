(ns harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]))

(def +xsd-polku+ "xsd/tloik/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn muodosta-viesti [viesti-id ilmoitus-id aika kuittaustyyppi urakka vastaanottaja virhe]
  [:harja:harja-kuittaus
   {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
   [:aika aika]
   [:kuittaustyyppi kuittaustyyppi]
   [:viestiId viesti-id]
   [:valitystiedot
    [:ilmoitusId ilmoitus-id]
    [:urakka
     [:id (:id urakka)]
     [:nimi (:nimi urakka)]
     [:tyyppi (:tyyppi urakka)]]
    [:urakoitsija
     [:nimi (:urakoitsija_nimi urakka)]
     [:ytunnus (:urakoitsija_ytunnus urakka)]]
    ;; todo: täytä päivystäjätiedot sitten, kun viestit voidaan lähettää tekstiviestillä tai sähköpostilla
    #_[:paivystaja
     [:etunimi "Päivi"]
     [:sukunimi "Päivystäjä"]
     [:matkapuhelin "0986578749309"]
     [:sahkoposti "paivi.paivystaja@puulaaki.fi"]]]
   (when virhe [:virhe virhe])])

(defn muodosta [viesti-id ilmoitus-id aika kuittaustyyppi urakka vastaanottaja virhe]
  (let [sisalto (muodosta-viesti viesti-id ilmoitus-id aika kuittaustyyppi urakka vastaanottaja virhe)
        xml (tee-xml-sanoma sisalto)]
    (println "----> XML:" xml)
    (if (xml/validoi +xsd-polku+ "harja-tloik.xsd" xml)
      xml
      (do
        (log/error "Kuittausta ei voida lähettää. Kuittaus XML ei ole validi.")
        nil))))
