(ns harja.palvelin.integraatiot.sampo.sanomat.sampo-sanoma
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml])
  (:import (java.text SimpleDateFormat ParseException)
           (java.sql Date)))

(def +xsd-polku+ "xsd/sampo/inbound/")

(defn parsi-paivamaara [teksti]
  (try (new Date (.getTime (.parse (SimpleDateFormat. "yyyy-MM-dd'T'HH:mm:ss.SSS") teksti)))
       (catch ParseException e
         (log/error e "Virhe parsiessa päivämäärää: " teksti)
         nil)))

(defn hae-viesti-id [data]
  (or (z/xml1-> data (z/attr :messageId))
      (z/xml1-> data (z/attr :message_Id))
      (z/xml1-> data (z/attr :messageid))
      (z/xml1-> data (z/attr :message_id))))

(defn lue-hanke [program]
  {:viesti-id (hae-viesti-id program)
   :sampo-id (z/xml1-> program (z/attr :id))
   :nimi (z/xml1-> program (z/attr :name))
   :alkupvm (parsi-paivamaara (z/xml1-> program (z/attr :schedule_start)))
   :loppupvm (parsi-paivamaara (z/xml1-> program (z/attr :schedule_finish)))
   :alueurakkanro (z/xml1-> program (z/attr :vv_alueurakkanro))
   :yhteyshenkilo-sampo-id (z/xml1-> program (z/attr :resourceId))})

(defn lue-urakka [project]
  {:viesti-id (hae-viesti-id project)
   :sampo-id (z/xml1-> project (z/attr :id))
   :nimi (z/xml1-> project (z/attr :name))
   :alkupvm (parsi-paivamaara (z/xml1-> project (z/attr :schedule_start)))
   :loppupvm (parsi-paivamaara (z/xml1-> project (z/attr :schedule_finish)))
   :hanke-sampo-id (z/xml1-> project (z/attr :programId))
   :yhteyshenkilo-sampo-id (z/xml1-> project (z/attr :resourceId))
   :ely-hash (z/xml1-> project (z/attr :financialDepartmentHash))
   :alueurakkanro (z/xml1-> project (z/attr :vv_alueurakkanro))
   :urakoitsijan-nimi (z/xml1-> project (z/attr :company_name))
   :urakoitsijan-ytunnus (z/xml1-> project (z/attr :vv_corporate_id))})

(defn lue-sopimus [order]
  {:viesti-id (hae-viesti-id order)
   :sampo-id (z/xml1-> order (z/attr :id))
   :nimi (z/xml1-> order (z/attr :name))
   :alkupvm (parsi-paivamaara (z/xml1-> order (z/attr :schedule_start)))
   :loppupvm (parsi-paivamaara (z/xml1-> order (z/attr :schedule_finish)))
   :urakka-sampo-id (z/xml1-> order (z/attr :projectId))
   :urakoitsija-sampo-id (z/xml1-> order (z/attr :contractPartyId))})

(defn lue-toimenpideinstanssi [operation]
  {:viesti-id (hae-viesti-id operation)
   :sampo-id (z/xml1-> operation (z/attr :id))
   :nimi (z/xml1-> operation (z/attr :name))
   :alkupvm (parsi-paivamaara (z/xml1-> operation (z/attr :schedule_start)))
   :loppupvm (parsi-paivamaara (z/xml1-> operation (z/attr :schedule_finish)))
   :vastuuhenkilo-id (z/xml1-> operation (z/attr :managerId))
   :talousosasto-id (z/xml1-> operation (z/attr :financialDepartmentHash))
   :talousosasto-polku (z/xml1-> operation (z/attr :financialDepartmentOBS))
   :tuote-id (z/xml1-> operation (z/attr :productHash))
   :tuote-polku (z/xml1-> operation (z/attr :productOBS))
   :urakka-sampo-id (z/xml1-> operation (z/attr :projectId))
   :sampo-toimenpidekoodi (z/xml1-> operation (z/attr :vv_operation))})

(defn lue-organisaatio [company]
  {:viesti-id (hae-viesti-id company)
   :sampo-id (z/xml1-> company (z/attr :id))
   :nimi (z/xml1-> company (z/attr :name))
   :y-tunnus (z/xml1-> company (z/attr :vv_corporate_id))
   :katuosoite (z/xml1-> (z/xml1-> company) :contactInformation (z/attr :address))
   :postinumero (z/xml1-> (z/xml1-> company) :contactInformation (z/attr :postal_Code))
   :kaupunki (z/xml1-> (z/xml1-> company) :contactInformation (z/attr :city))})

(defn lue-yhteyshenkilo [resource]
  {:viesti-id (hae-viesti-id resource)
   :sampo-id (z/xml1-> resource (z/attr :id))
   :etunimi (z/xml1-> resource (z/attr :first_name))
   :sukunimi (z/xml1-> resource (z/attr :last_name))
   :sahkoposti (z/xml1-> (z/xml1-> resource) :contactInformation (z/attr :email))})

(defn lue-viesti [viesti]
  (when (not (xml/validi-xml? +xsd-polku+ "Sampo2Harja.xsd" viesti))
    (throw (new RuntimeException "XML-sanoma ei ole XSD-skeeman Sampo2Harja.xsd mukaan validi.")))

  (let [data (xml/lue viesti)]
    {:hankkeet (z/xml-> data :Program lue-hanke)
     :urakat (z/xml-> data :Project lue-urakka)
     :sopimukset (z/xml-> data :Order lue-sopimus)
     :toimenpideinstanssit (z/xml-> data :Operation lue-toimenpideinstanssi)
     :organisaatiot (z/xml-> data :Company lue-organisaatio)
     :yhteyshenkilot (z/xml-> data :Resource lue-yhteyshenkilo)}))
