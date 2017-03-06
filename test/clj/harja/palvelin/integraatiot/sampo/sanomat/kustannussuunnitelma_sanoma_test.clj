(ns harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma :as kustannussuunnitelma-sanoma]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [harja.testi :refer :all]
            [harja.tyokalut.xml :as xml]
            [taoensso.timbre :as log])
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
                 :vuosittaiset-summat  [{:alkupvm  "2014-01-01T00:00:00.0",
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
                                         :loppupvm "2019-12-31T00:00:00.0",
                                         :summa    0}]
                 :lkp-tilinumero "43020000"})

(deftest tarkista-kustannussuunnitelman-validius
  (let [kustannussuunnitelma (html (kustannussuunnitelma-sanoma/kustannussuunnitelma-xml +maksuera+))
        xsd "nikuxog_costPlan.xsd"]
    (is (xml/validi-xml? +xsd-polku+ xsd kustannussuunnitelma) "Muodostettu XML-tiedosto on XSD-skeeman mukainen")))

(deftest tarkista-kulun-jakaminen-vuosille
  (let [segmentit (kustannussuunnitelma-sanoma/summat
                    [{:alkupvm  "2014-01-01T00:00:00.0",
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
    (log/debug segmentit)
    (is (= 6 (count segmentit)) "Segmentit on jaoteltu 3 vuodelle")

    (let [segmentti (second (second segmentit))]
      (is (= 900M (:value segmentti)))
      (is (= "2015-01-01T02:00:00.0" (:start segmentti)))
      (is (= "2015-12-31T02:00:00.0" (:finish segmentti))))))

(deftest tarkista-kustannussuunnitelmajakson-muodostus
  (is (= "1.1.2015-31.12.2015"
         (kustannussuunnitelma-sanoma/kustannussuunnitelmajakso
           (parsi-paivamaara "12.12.2015")))))
