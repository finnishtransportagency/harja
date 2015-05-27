(ns harja.palvelin.integraatiot.sampo.kustannussuunnitelmat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.sampo.kustannussuunnitelma :as kustannussuunnitelma]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [harja.testi :refer :all]
            [harja.xml :as xml])
  (:import (java.io ByteArrayInputStream)
           (java.text SimpleDateFormat)))

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

(deftest tarkista-kustannussuunnitelman-validius
  (let [maksuera (html (kustannussuunnitelma/muodosta-kustannussuunnitelma-xml +maksuera+))
        xsd "nikuxog_costPlan.xsd"]
    (is (xml/validoi +xsd-polku+ xsd maksuera) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

