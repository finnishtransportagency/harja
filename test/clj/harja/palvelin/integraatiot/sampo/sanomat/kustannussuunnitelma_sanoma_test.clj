(ns harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma :as kustannussuunnitelma-sanoma]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml])
  (:import (java.text SimpleDateFormat)))

(def +xsd-polku+ "xsd/sampo/outbound/")

(defn parsi-paivamaara [teksti]
  (.parse (SimpleDateFormat. "dd.MM.yyyy") teksti))

(def +maksuera+ {:numero               123456789
                 :maksuera             {:nimi
                                                "Testimaksuera"
                                        :tyyppi "kokonaishintainen"}
                 :toimenpideinstanssi  {:alkupvm         (parsi-paivamaara "1.10.2014")
                                        :loppupvm        (parsi-paivamaara "30.9.2019")
                                        :vastuuhenkilo   "A009717"
                                        :talousosasto    "talousosasto"
                                        :tuotepolku      "polku/tuote"
                                        :toimenpidekoodi {
                                                          :koodi "20112"
                                                          }}
                 :urakka               {:sampoid "PR00020606"}
                 :sopimus              {:sampoid "00LZM-0033600"}
                 :kustannussuunnitelma {:summa 93999M}
                 :tuotenumero          111
                 :vuosittaiset-summat    [{:alkupvm "2014-10-01T00:00:00.0",
                                           :loppupvm "2014-12-31T02:00:00.0",
                                           :summa 300M}
                                          {:alkupvm "2015-01-01T02:00:00.0",
                                           :loppupvm "2015-12-31T02:00:00.0",
                                           :summa 900M}
                                          {:alkupvm "2016-01-01T02:00:00.0",
                                           :loppupvm "2016-12-31T02:00:00.0",
                                           :summa 0}
                                          {:alkupvm "2017-01-01T02:00:00.0",
                                           :loppupvm "2017-12-31T02:00:00.0",
                                           :summa 0}
                                          {:alkupvm "2018-01-01T02:00:00.0",
                                           :loppupvm "2018-12-31T02:00:00.0",
                                           :summa 0}
                                          {:alkupvm "2019-01-01T02:00:00.0",
                                           :loppupvm "2019-09-30T00:00:00.0",
                                           :summa 0}]})

(deftest tarkista-kustannussuunnitelman-validius
  (let [kustannussuunnitelma (html (kustannussuunnitelma-sanoma/muodosta +maksuera+))
        xsd "nikuxog_costPlan.xsd"]
    (is (xml/validoi +xsd-polku+ xsd kustannussuunnitelma) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

(deftest tarkista-lkp-tilinnumeron-paattely
  (is (= "43021" (kustannussuunnitelma-sanoma/valitse-lkp-tilinumero "20112" nil))
      "Oikea LKP-tilinnumero valittu toimenpidekoodin perusteella")
  (is (= "43021" (kustannussuunnitelma-sanoma/valitse-lkp-tilinumero nil 112))
      "Oikea LKP-tilinnumero valittu tuotenumeroon perusteella")
  (is (= "43021" (kustannussuunnitelma-sanoma/valitse-lkp-tilinumero nil 536))
      "Oikea LKP-tilinnumero valittu tuotenumeroon perusteella")
  (is (= "12981" (kustannussuunnitelma-sanoma/valitse-lkp-tilinumero nil 30))
      "Oikea LKP-tilinnumero valittu tuotenumeroon perusteella")
  (is (= "12981" (kustannussuunnitelma-sanoma/valitse-lkp-tilinumero nil 242))
      "Oikea LKP-tilinnumero valittu toimenpidekoodin perusteella")
  (is (= "12981" (kustannussuunnitelma-sanoma/valitse-lkp-tilinumero nil 318))
      "Oikea LKP-tilinnumero valittu toimenpidekoodin perusteella")
  (is (thrown? RuntimeException (kustannussuunnitelma-sanoma/valitse-lkp-tilinumero nil nil))
      "Jos LKP-tuotenumeroa ei voida päätellä, täytyy aiheutua poikkeus")
  (is (thrown? RuntimeException (kustannussuunnitelma-sanoma/valitse-lkp-tilinumero nil 1))
      "Jos LKP-tuotenumeroa ei voida päätellä, täytyy aiheutua poikkeus"))

(deftest tarkista-kulun-jakaminen-vuosille
  (let [segmentit (kustannussuunnitelma-sanoma/luo-summat
                    [{:alkupvm  "2014-10-01T00:00:00.0",
                      :loppupvm "2014-12-31T02:00:00.0",
                      :summa    300M}
                     {:alkupvm  "2015-01-01T02:00:00.0",
                      :loppupvm "2015-12-31T02:00:00.0",
                      :summa    900M}
                     {:alkupvm  "2016-01-01T02:00:00.0",
                      :loppupvm "2016-12-31T02:00:00.0",
                      :summa    0}
                     {:alkupvm  "2017-01-01T02:00:00.0",
                      :loppupvm "2017-12-31T02:00:00.0",
                      :summa    0}
                     {:alkupvm  "2018-01-01T02:00:00.0",
                      :loppupvm "2018-12-31T02:00:00.0",
                      :summa    0}
                     {:alkupvm  "2019-01-01T02:00:00.0",
                      :loppupvm "2019-09-30T00:00:00.0",
                      :summa    0}])]
    (println segmentit)
    (is (= 6 (count segmentit)) "Segmentit on jaoteltu 3 vuodelle")

    (let [segmentti (second (second segmentit))]
      (is (= 900M (:value segmentti)))
      (is (= "2015-01-01T02:00:00.0" (:start segmentti)))
      (is (= "2015-12-31T02:00:00.0" (:finish segmentti))))))

(deftest tarkista-kustannussuunnitelmajakson-muodostus
  (is (= "1.1.2015-31.12.2015"
         (kustannussuunnitelma-sanoma/tee-kustannussuunnitelmajakso
           (parsi-paivamaara "12.12.2015")))))