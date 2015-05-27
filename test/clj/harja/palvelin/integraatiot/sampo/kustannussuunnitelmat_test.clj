(ns harja.palvelin.integraatiot.sampo.kustannussuunnitelmat-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.sampo.kustannussuunnitelma :as kustannussuunnitelma]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [harja.testi :refer :all]
            [harja.xml :as xml])
  (:import (java.text SimpleDateFormat)))

(def +xsd-polku+ "test/xsd/sampo/outbound/")

(defn parsi-paivamaara [teksti]
  (.parse (SimpleDateFormat. "dd.MM.yyyy") teksti))

(def +kokonaishintainenmaksuera+ {:nimi                   "Testimaksuera"
                                  :tyyppi                 "kokonaishintainen"
                                  :numero                 123456789
                                  :toimenpideinstanssi    {:alkupvm         (parsi-paivamaara "12.12.2015")
                                                           :loppupvm        (parsi-paivamaara "1.1.2017")
                                                           :vastuuhenkilo   "A009717"
                                                           :talousosasto    "talousosasto"
                                                           :tuotepolku      "polku/tuote"
                                                           :toimenpidekoodi {
                                                                             :koodi       "20112"
                                                                             :tuotenumero 111}}
                                  :urakka                 {:sampoid "PR00020606"}
                                  :sopimus                {:sampoid "00LZM-0033600"}
                                  :kokonaishintaiset-tyot [{:summa 1000}
                                                           {:summa 1234}]})

(def +yksikkohintainenmaksuera+ {:nimi                  "Testimaksuera"
                                 :tyyppi                "yksikkohintainen"
                                 :numero                123456789
                                 :toimenpideinstanssi   {:alkupvm         (parsi-paivamaara "12.12.2015")
                                                         :loppupvm        (parsi-paivamaara "1.1.2017")
                                                         :vastuuhenkilo   "A009717"
                                                         :talousosasto    "talousosasto"
                                                         :tuotepolku      "polku/tuote"
                                                         :toimenpidekoodi {
                                                                           :koodi       "20112"
                                                                           :tuotenumero 111}}
                                 :urakka                {:sampoid "PR00020606"}
                                 :sopimus               {:sampoid "00LZM-0033600"}
                                 :yksikkohintaiset-tyot [{:maara        1
                                                          :yksikkohinta 12.2}
                                                         {:maara        3
                                                          :yksikkohinta 34.3}]})


(deftest tarkista-kustannussuunnitelman-validius
  (let [maksuera (html (kustannussuunnitelma/muodosta-kustannussuunnitelma-xml +kokonaishintainenmaksuera+))
        xsd "nikuxog_costPlan.xsd"]
    (is (xml/validoi +xsd-polku+ xsd maksuera) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

(deftest tarkista-lpk-tilinnumeron-paattely
  (is (= "43021" (kustannussuunnitelma/valitse-lkp-tilinumero "20112" nil)) "Oikea LPK-tilinnumero valittu toimenpidekoodin perusteella")
  (is (= "43021" (kustannussuunnitelma/valitse-lkp-tilinumero nil 112)) "Oikea LPK-tilinnumero valittu tuotenumeroon perusteella")
  (is (= "43021" (kustannussuunnitelma/valitse-lkp-tilinumero nil 536)) "Oikea LPK-tilinnumero valittu tuotenumeroon perusteella")
  (is (= "12981" (kustannussuunnitelma/valitse-lkp-tilinumero nil 30)) "Oikea LPK-tilinnumero valittu tuotenumeroon perusteella")
  (is (= "12981" (kustannussuunnitelma/valitse-lkp-tilinumero nil 242)) "Oikea LPK-tilinnumero valittu toimenpidekoodin perusteella")
  (is (= "12981" (kustannussuunnitelma/valitse-lkp-tilinumero nil 318)) "Oikea LPK-tilinnumero valittu toimenpidekoodin perusteella"))

(deftest tarkista-summien-laskenta
  (is (= 1 (kustannussuunnitelma/laske-summa {:tyyppi "sakko"})) "Sakoille summa on aina 1")
  (is (= 2234 (kustannussuunnitelma/laske-summa +kokonaishintainenmaksuera+)) "Summa lasketaan oikein kokonaishintaisista töistä")
  (is (= 115.1 (kustannussuunnitelma/laske-summa +yksikkohintainenmaksuera+))))