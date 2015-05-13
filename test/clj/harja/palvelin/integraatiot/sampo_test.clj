(ns harja.palvelin.integraatiot.sampo-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.sampo :refer [->Sampo] :as sampo]
            [harja.palvelin.integraatiot.sampo.maksuera :as maksuera]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.jms :refer [feikki-sonja]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [clojure.core.async :refer [<! >! go] :as async]
            [harja.xml :as xml])
  (:import (java.io ByteArrayInputStream)
           (java.text SimpleDateFormat)))

(def +lahetysjono+ "lahetysjono")
(def +kuittausjono+ "kuittausjono")

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (apply tietokanta/luo-tietokanta testitietokanta)
                        :sonja (feikki-sonja)
                        :sampo (component/using (->Sampo +lahetysjono+ +kuittausjono+) [:db :sonja])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(def +xsd-polku+ "test/xsd/sampo/outbound/")

(defn parsi-paivamaara [teksti]
  (.parse (SimpleDateFormat. "dd.MM.yyyy") teksti))

(def +maksuera+ {:nimi                "Testimaksuera"
                 :tyyppi              "kokonaishintainen"
                 :numero              123456789
                 :toimenpideinstanssi {:alkupvm       (parsi-paivamaara "12.12.2015")
                                       :loppupvm      (parsi-paivamaara "1.1.2017")
                                       :vastuuhenkilo "A009717"
                                       :talousosasto  "talousosasto"
                                       :tuotepolku    "polku/tuote"}
                 :urakka              {:sampoid "PR00020606"}
                 :sopimus             {:sampoid "00LZM-0033600"}})



(deftest tarkista-maksueran-validius
  (let [maksuera (html (maksuera/muodosta-maksuera-xml +maksuera+))
        xsd "nikuxog_product.xsd"]
    (is (xml/validoi +xsd-polku+ xsd maksuera) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

(deftest tarkista-maksueran-sisalto
  (let [maksuera-xml (xml-zip (parse (ByteArrayInputStream. (.getBytes (html (maksuera/muodosta-maksuera-xml +maksuera+)) "UTF-8"))))]
    (is (= "2015-12-12T00:00:00.0" (z/xml1-> maksuera-xml :Products :Product (z/attr :start))))
    (is (= "2017-01-01T00:00:00.0" (z/xml1-> maksuera-xml :Products :Product (z/attr :finish))))
    (is (= "A009717" (z/xml1-> maksuera-xml :Products :Product (z/attr :managerUserName))))
    (is (= "Testimaksuera" (z/xml1-> maksuera-xml :Products :Product (z/attr :name))))
    (is (= "HA123456789" (z/xml1-> maksuera-xml :Products :Product (z/attr :objectID))))
    (is (= "PR00020606" (z/xml1-> maksuera-xml :Products :Product :InvestmentAssociations :Allocations :ParentInvestment (z/attr :InvestmentID))))
    (is (= "kulu2015" (z/xml1-> maksuera-xml :Products :Product :InvestmentResources :Resource (z/attr :resourceID))))
    (is (= "kulu2015" (z/xml1-> maksuera-xml :Products :Product :InvestmentTasks :Task :Assignments :TaskLabor (z/attr :resourceID))))
    (is (= "Testimaksuera" (z/xml1-> maksuera-xml :Products :Product :InvestmentTasks :Task (z/attr :name))))
    (is (= "polku/tuote" (z/xml1-> maksuera-xml :Products :Product :OBSAssocs :OBSAssoc (z/attr= :id "LiiviKP") (z/attr :unitPath))))
    (is (= "polku/tuote" (z/xml1-> maksuera-xml :Products :Product :OBSAssocs :OBSAssoc (z/attr= :id "tuote2013") (z/attr :unitPath))))
    (is (= "00LZM-0033600" (z/xml1-> maksuera-xml :Products :Product :CustomInformation :ColumnValue (z/attr= :name "vv_tilaus") z/text)))
    (is (= "2" (z/xml1-> maksuera-xml :Products :Product :CustomInformation :ColumnValue (z/attr= :name "vv_me_type") z/text)))
    (is (= "123456789" (z/xml1-> maksuera-xml :Products :Product :CustomInformation :ColumnValue (z/attr= :name "vv_inst_no") z/text)))
    (is (= "AL123456789" (z/xml1-> maksuera-xml :Products :Product :CustomInformation :instance (z/attr :instanceCode))))))

(deftest yrita-laheta-maksuera-jota-ei-ole-olemassa
  (is (= {:virhe :maksueran-lukitseminen-epaonnistui} (sampo/laheta-maksuera-sampoon (:sampo jarjestelma) 666))))

(deftest laheta-maksuera
  (let [ch (async/chan)]
    (println jarjestelma)
    (sonja/kuuntele (:sonja jarjestelma) +lahetysjono+ #(async/put! ch (.getText %)))
    (println "ennen lähetystä")
    (is (sampo/laheta-maksuera-sampoon (:sampo jarjestelma) 1) "Lähetys onnistui")
    (println "lahetyksen jalkeen")
    (let [[sampoon-lahetetty-xml luettu-ch] (async/alts!! [ch (async/timeout 1000)])]
      (is (= luettu-ch ch) "Sampo lähetys ei mennyt kanavaan sekunnissa")
      (is (= "hephep" sampoon-lahetetty-xml)))))