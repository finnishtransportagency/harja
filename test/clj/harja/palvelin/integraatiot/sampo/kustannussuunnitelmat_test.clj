(ns harja.palvelin.integraatiot.sampo.kustannussuunnitelmat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.sampo.kustannussuunnitelma :as kustannussuunnitelma]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml])
  (:import (java.text SimpleDateFormat)))

(def +xsd-polku+ "test/xsd/sampo/outbound/")

(defn parsi-paivamaara [teksti]
  (.parse (SimpleDateFormat. "dd.MM.yyyy") teksti))

(def +maksuera+ {:numero 123456789
                 :maksuera             {:nimi
                                                "Testimaksuera"
                                        :tyyppi "kokonaishintainen"}
                 :toimenpideinstanssi  {:alkupvm         (parsi-paivamaara "12.12.2015")
                                        :loppupvm        (parsi-paivamaara "1.1.2017")
                                        :vastuuhenkilo   "A009717"
                                        :talousosasto    "talousosasto"
                                        :tuotepolku      "polku/tuote"
                                        :toimenpidekoodi {
                                                          :koodi "20112"
                                                          }}
                 :urakka               {:sampoid "PR00020606"}
                 :sopimus              {:sampoid "00LZM-0033600"}
                 :kustannussuunnitelma {:summa 93999M}
                 :tuotenumero          111})

(deftest tarkista-kustannussuunnitelman-validius
  (let [maksuera (html (kustannussuunnitelma/muodosta-kustannussuunnitelma-xml +maksuera+))
        xsd "nikuxog_costPlan.xsd"]
    (is (xml/validoi +xsd-polku+ xsd maksuera) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

(deftest tarkista-lkp-tilinnumeron-paattely
  (is (= "43021" (kustannussuunnitelma/valitse-lkp-tilinumero "20112" nil)) "Oikea LKP-tilinnumero valittu toimenpidekoodin perusteella")
  (is (= "43021" (kustannussuunnitelma/valitse-lkp-tilinumero nil 112)) "Oikea LKP-tilinnumero valittu tuotenumeroon perusteella")
  (is (= "43021" (kustannussuunnitelma/valitse-lkp-tilinumero nil 536)) "Oikea LKP-tilinnumero valittu tuotenumeroon perusteella")
  (is (= "12981" (kustannussuunnitelma/valitse-lkp-tilinumero nil 30)) "Oikea LKP-tilinnumero valittu tuotenumeroon perusteella")
  (is (= "12981" (kustannussuunnitelma/valitse-lkp-tilinumero nil 242)) "Oikea LKP-tilinnumero valittu toimenpidekoodin perusteella")
  (is (= "12981" (kustannussuunnitelma/valitse-lkp-tilinumero nil 318)) "Oikea LKP-tilinnumero valittu toimenpidekoodin perusteella")
  (is (thrown? RuntimeException (kustannussuunnitelma/valitse-lkp-tilinumero nil nil)) "Jos LKP-tuotenumeroa ei voida päätellä, täytyy aiheutua poikkeus")
  (is (thrown? RuntimeException (kustannussuunnitelma/valitse-lkp-tilinumero nil 1)) "Jos LKP-tuotenumeroa ei voida päätellä, täytyy aiheutua poikkeus"))
