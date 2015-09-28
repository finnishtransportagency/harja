(ns harja.palvelin.integraatiot.tierekisteri.sanomat.lisaa-tietue-test
  (:require [taoensso.timbre :as log]
            [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.tietueen-lisayskutsu :refer :all]
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

(def +xsd+ "xsd/tierekisteri/schemas/")

(deftest tarkista-kutsu
  (let [kutsu-xml (muodosta-kutsu lisattava-testitietue)
        xsd "lisaaTietue.xsd"]
    (is (xml/validoi +xsd+ xsd kutsu-xml) "Muodostettu kutsu on XSD-skeeman mukainen")))
