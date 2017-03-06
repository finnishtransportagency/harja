(ns harja.palvelin.integraatiot.sonja.sahkoposti.sanomat
  (:require [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [harja.pvm :as pvm]
            [slingshot.slingshot :refer [throw+]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [hiccup.compiler :refer [HtmlRenderer]]))

(def ^:const +xsd-polku+ "xsd/sahkoposti/")
(def ^:const +sahkoposti-xsd+ "sahkoposti.xsd")
(def ^:const +sahkoposti-ns+ "http://www.liikennevirasto.fi/xsd/harja/sahkoposti")

(defn- validoi [xml-viesti]
  (when-not (xml/validi-xml? +xsd-polku+ +sahkoposti-xsd+ xml-viesti)
    (log/error "Vastaanotettu sähköposti XML-tiedosto ei ole sahkoposti.xsd skeeman mukainen.")
    (throw+ {:type virheet/+invalidi-xml+})))

(defn- lue-xml [xml-viesti]
  (validoi xml-viesti)
  (let [x (xml/lue xml-viesti)]
    (fn [& polku]
      (apply z/xml1-> x (concat polku [z/text])))))

(defn lue-sahkoposti [xml-viesti]
  (let [v (lue-xml xml-viesti)]
    {:viesti-id (v :viestiId)
     :vastaanottaja (v :vastaanottajat
                       :vastaanottaja)
     :lahettaja (v :lahettaja)
     :otsikko (v :otsikko)
     :sisalto (v :sisalto)}))

(defn sahkoposti
  "Luo sähköpostiviestin JMS sanoman"
  [viesti-id lahettaja vastaanottaja otsikko sisalto]
  [:sahkoposti:sahkoposti {:xmlns:sahkoposti +sahkoposti-ns+}
   [:viestiId viesti-id]
   [:vastaanottajat [:vastaanottaja vastaanottaja]]
   [:lahettaja lahettaja]
   [:otsikko otsikko]
   [:sisalto (xml/tee-c-data-elementti sisalto)]])

(defn lue-kuittaus [xml-viesti]
  (let [v (lue-xml xml-viesti)]
    {:viesti-id (v :viestiId)
     :aika (xml/parsi-xsd-datetime (v :aika))
     :onnistunut (xml/parsi-totuusarvo (v :onnistunut))}))

(defn kuittaus
  "Tee annetulle vastaanotetulle sähköpostiviestille kuittausviesti"
  [{viesti-id :viesti-id} virheet]
  [:sahkoposti:kuittaus {:xmlns:sahkoposti +sahkoposti-ns+}
   [:viestiId viesti-id]
   [:aika (xml/formatoi-xsd-datetime (pvm/nyt))]
   [:onnistunut (nil? virheet)]
   (when virheet
     (for [virhe virheet]
       [:virheet virhe]))])

(defn kirjoita-kuittaus [kuittaus]
  (xml/tee-xml-sanoma kuittaus))
