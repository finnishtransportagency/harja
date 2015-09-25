(ns harja.palvelin.integraatiot.tierekisteri.sanomat.tietolajin-hakukutsu-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.tierekisteri.tietue :refer :all])
  (:import (java.text SimpleDateFormat)
           (harja.palvelin.integraatiot.integraatioloki Integraatioloki)))

(def lisattava-testitietue {:asd 123}) ; FIXME TODO

(def +xsd-polku+ "xsd/tierekisteri/schemas/")

(deftest laheta-kuttsu
  (let [vastaus (lisaa-tietue Integraatioloki
                "https://testisonja.liikennevirasto.fi/harja/tierekisteri/lisaatietue"
                lisattava-testitietue)]
    (is (not (nil? vastaus))))) ; FIXME Testaa tarkemmin

