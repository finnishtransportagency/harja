(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tierekisteri.tietue :refer :all])
  (:import (java.text SimpleDateFormat)
           (harja.palvelin.integraatiot.integraatioloki Integraatioloki)))

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

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(deftest laheta-kuttsu
  (let [vastaus (lisaa-tietue Integraatioloki
                "https://testisonja.liikennevirasto.fi/harja/tierekisteri/lisaatietue"
                lisattava-testitietue)]
    (is (not (nil? vastaus))))) ; FIXME Testaa tarkemmin

