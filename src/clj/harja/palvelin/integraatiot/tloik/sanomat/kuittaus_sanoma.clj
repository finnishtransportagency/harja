(ns harja.palvelin.integraatiot.tloik.sanomat.kuittaus-sanoma
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]))

(def +xsd-polku+ "xsd/tloik/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn muodosta-viesti [viesti-id aika kuittaustyyppi vastaanottaja virhe]
  [:kuittaus
   [:aika aika]
   [:kuittaustyyppi kuittaustyyppi]
   [:viestiId viesti-id]
   ;; todo: lisää välitystiedot
   (when virhe [:virhe virhe])])

(defn muodosta [viesti-id aika kuittaustyyppi vastaanottaja virhe]
  (let [sisalto (muodosta-viesti viesti-id aika kuittaustyyppi vastaanottaja virhe)
        xml (tee-xml-sanoma sisalto)]
    (if (xml/validoi +xsd-polku+ "kuittaus.xsd" xml)
      xml
      (do
        (log/error "Kuittausta ei voida lähettää. Kuittaus XML ei ole validi.")
        nil))))
