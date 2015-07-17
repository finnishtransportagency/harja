(ns harja.palvelin.integraatiot.tloik.sanomat.viestikuittaus-sanoma
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]))

(def +xsd-polku+ "resources/xsd/tloik/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn muodosta-viesti [viesti-id aika kuittaustyyppi vastaanottaja virhe]
  [:viestikuittaus
   [:aika aika]
   [:kuittaustyyppi kuittaustyyppi]
   (when vastaanottaja
     [:vastaanottaja
      [:nimi (:nimi vastaanottaja)]
      [:ytunnus (:ytunnus vastaanottaja)]])
   [:viestiId viesti-id]
   (when virhe [:virhe virhe])])

(defn muodosta [viesti-id aika kuittaustyyppi vastaanottaja virhe]
  (let [sisalto (muodosta-viesti viesti-id aika kuittaustyyppi vastaanottaja virhe)
        xml (tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "viestikuittaus.xsd" xml)
      xml
      (do
        (log/error "Kuittausta ei voida lähettää. Kuittaus XML ei ole validi.")
        nil))))
