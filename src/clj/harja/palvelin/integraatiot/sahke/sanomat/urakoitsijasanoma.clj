(ns harja.palvelin.integraatiot.sahke.sanomat.urakoitsijasanoma
  (:require [harja.tyokalut.xml :as xml]))

(defn urakoitsija-hiccup [{:keys [id
                                  nimi
                                  ytunnus
                                  katuosoite
                                  postinumero]}
                          viesti-id]
  [:sampo2harja
   {:xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
    :xsi:nonamespaceschemalocation "SampToharja.xsd"}
   [:company
    {:id (str "HAR-" id)
     :messageid viesti-id
     :name nimi
     :vv_corporate_id ytunnus}
    [:contactinformation
     {:address katuosoite
      :city "-"
      :postal_code postinumero
      :type "main"}
     [:contactpersons
      {:id ""
       :first_name ""
       :family_name ""
       :yhthlo_puh ""
       :yht_sposti ""}]]]])

(defn muodosta [urakoitsija viesti-id]
  (let [xml (xml/tee-xml-sanoma (urakoitsija-hiccup urakoitsija viesti-id))]
    (when (not (xml/validi-xml? "xsd/sampo/inbound/" "Sampo2Harja.xsd" xml))
      (throw (new RuntimeException
                  "Sähkeeseen lähetettävä XML-sanoma ei ole XSD-skeeman Sampo2Harja.xsd mukaan validi.")))
    xml))
