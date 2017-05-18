(ns harja.palvelin.integraatiot.sahke.sanomat.urakkasanoma
  (:require [harja.tyokalut.xml :as xml]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi :as urakkatyyppi]))

(defn urakka-hiccup [{:keys [id
                             tyyppi
                             alkupvm
                             loppupvm
                             urakkanumero
                             nimi
                             hanke-id
                             yhteyshenkilo-id
                             urakoitsija-y-tunnus
                             urakoitsijanimi]}
                     viesti-id]
  [:Sampo2harja
   {:xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
    :xsi:noNamespaceSchemaLocation "SampToharja.xsd"}
   [:Project
    {:id (str "HAR-" id)
     :financialDepartmentHash "-"
     :vv_corporate_id urakoitsija-y-tunnus
     :schedule_finish (xml/formatoi-aikaleima loppupvm)
     :name nimi
     :vv_alueurakkanro (str (urakkatyyppi/rakenna-sampon-tyyppi tyyppi) "-" urakkanumero)
     :resourceId (str "HAR-" yhteyshenkilo-id)
     :schedule_start (xml/formatoi-aikaleima alkupvm)
     :company_name urakoitsijanimi
     :message_Id viesti-id
     :programId (str "HAR-" hanke-id)
     :vv_transferred_harja (xml/formatoi-aikaleima (pvm/nyt))}
    [:documentLinks]]])

(defn muodosta [urakka viesti-id]
  (let [xml (xml/tee-xml-sanoma (urakka-hiccup urakka viesti-id))]
    (when (not (xml/validi-xml? "xsd/sampo/inbound/" "Sampo2Harja.xsd" xml))
      (throw (new RuntimeException
                  "Sähkeeseen lähetettävä XML-sanoma ei ole XSD-skeeman Sampo2Harja.xsd mukaan validi.")))
    xml))
