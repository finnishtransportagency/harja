(ns harja.palvelin.integraatiot.tierekisteri.sanomat.lisaa-tietue-test
  (:require [taoensso.timbre :as log]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu :as tietue]
            [harja.tyokalut.xml :as xml]))

(def lisattava-testitietue {:lisaaja {:henkilo      "Keijo Käsittelijä"
                                      :jarjestelma  "FastMekka"
                                      :organisaatio "Asfaltia Oy"
                                      :yTunnus      "1234567-8"}
                            :tietue  {:tunniste    "1245rgfsd"
                                      :alkupvm     "2015-03-03+02:00"
                                      :loppupvm    "2015-03-03+02:00"
                                      :karttapvm   "2015-03-03+02:00"
                                      :piiri       "1"
                                      :kuntoluokka "1"
                                      :urakka      "100"
                                      :sijainti    {:tie {:numero  "1"
                                                          :aet     "1"
                                                          :aosa    "1"
                                                          :let     "1"
                                                          :losa    "1"
                                                          :ajr     "1"
                                                          :puoli   "1"
                                                          :alkupvm "2015-03-03+02:00"}}
                                      :tietolaji   {:tietolajitunniste "tl506"
                                                    :arvot             "998 2 0 1 0 1 1 Testi liikennemerkki Omistaja O 4 123456789 40"}}

                            :lisatty "2015-05-26+03:00"})

(def +testi-xml+ "\"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>\\n<ns2:lisaaTietue xmlns:ns2=\\\"http://www.solita.fi/harja/tierekisteri/lisaaTietue\\\"><lisaaja><henkilo>Keijo Käsittelijä</henkilo><jarjestelma>FastMekka</jarjestelma><organisaatio>Asfaltia Oy</organisaatio><yTunnus>1234567-8</yTunnus></lisaaja><tietue><tunniste>1245rgfsd</tunniste><alkupvm>2015-03-03+02:00</alkupvm><loppupvm>2015-03-03+02:00</loppupvm><karttapvm>2015-03-03+02:00</karttapvm><piiri>1</piiri><kuntoluokka>1</kuntoluokka><urakka>100</urakka><sijainti><tie><numero>1</numero><aet>1</aet><aosa>1</aosa><let>1</let><losa>1</losa><ajr>1</ajr><puoli>1</puoli><alkupvm>2015-03-03+02:00</alkupvm></tie></sijainti><tietolaji><tietolajitunniste>tl506</tietolajitunniste><arvot>998 2 0 1 0 1 1 Testi liikennemerkki Omistaja O 4 123456789 40</arvot></tietolaji></tietue><lisatty>2015-05-26+03:00</lisatty></ns2:lisaaTietue>\"\n")

(deftest tarkista-kutsu
  (let [kutsu-xml (tietue/muodosta-kutsu lisattava-testitietue)
        xsd "lisaaTietue.xsd"]
    (is (xml/validoi +xsd-polku+ xsd kutsu-xml) "Muodostettu kutsu on XSD-skeeman mukainen")))
