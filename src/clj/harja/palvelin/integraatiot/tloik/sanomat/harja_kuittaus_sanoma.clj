(ns harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [harja.tyokalut.merkkijono :as merkkijono]
            [clojure.string :as str]))

(def +xsd-polku+ "xsd/tloik/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn urakkatyyppi [urakkatyyppi]
  (case (str/lower-case urakkatyyppi)
    "siltakorjaus" "silta"
    "tekniset-laitteet" "tekniset laitteet"
    "teiden-hoito" "hoito" ;; Palauta teiden-hoito urakkatyyppi aina hoitona. Teiden hoito on relevantti vain Harjan sisällä.
    urakkatyyppi))

(defn rakenna-urakka [urakka]
  (when urakka
    [:urakka
     [:id (:id urakka)]
     [:nimi (merkkijono/leikkaa 256 (:nimi urakka))]
     [:tyyppi (urakkatyyppi (:tyyppi urakka))]]))

(defn rakenna-urakoitsija [urakka]
  (when urakka
    [:urakoitsija
     [:nimi (merkkijono/leikkaa 128 (:urakoitsija_nimi urakka))]
     [:ytunnus (merkkijono/leikkaa 9 (:urakoitsija_ytunnus urakka))]]))

(defn rakenna-paivystaja [{:keys [etunimi sukunimi matkapuhelin tyopuhelin sahkoposti]}]
  [:paivystaja
   [:etunimi (merkkijono/leikkaa 32 etunimi)]
   [:sukunimi (merkkijono/leikkaa 32 sukunimi)]
   [:matkapuhelin (merkkijono/leikkaa 32 (or matkapuhelin tyopuhelin))]
   [:sahkoposti (merkkijono/leikkaa 64 sahkoposti)]])

(defn muodosta-viesti [viesti-id ilmoitus-id aika kuittaustyyppi urakka paivystajat virhe]
  [:harja:harja-kuittaus
   {:xmlns:harja "http://www.liikennevirasto.fi/xsd/harja"}
   [:aika aika]
   [:kuittaustyyppi kuittaustyyppi]
   [:viestiId viesti-id]
   (when virhe
     [:virhe (merkkijono/leikkaa 255 virhe)])
   (when ilmoitus-id
     [:valitystiedot
      [:ilmoitusId ilmoitus-id]
      (rakenna-urakka urakka)
      (rakenna-urakoitsija urakka)
      [:paivystajat (for [p paivystajat] (rakenna-paivystaja p))]])])

(defn muodosta [viesti-id ilmoitus-id aika kuittaustyyppi urakka paivystajat virhe]
  (let [sisalto (muodosta-viesti viesti-id ilmoitus-id aika kuittaustyyppi urakka paivystajat virhe)
        xml (tee-xml-sanoma sisalto)]
    (if (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" xml)
      xml
      (do
        (log/error (format "Kuittausta T-LOIK:n ei voida lähettää viesti id:lle %s. Kuittaus XML ei ole validi."
                           viesti-id))
        nil))))
