(ns harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.tierekisteri.sanomat.vastaus :as vastaus])
  (:import (java.text SimpleDateFormat)))


(deftest lue-onnistunut-vastaus
  (is (:onnistunut (vastaus/lue (slurp "resources/xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml")))))

(deftest lue-epaonnistunut-vastaus
  (let [virhevastaus (vastaus/lue (slurp "resources/xsd/tierekisteri/esimerkit/virhe-vastaus-tietolajia-ei-loydy-response.xml"))]
    (is (not (:onnistunut virhevastaus)))
    (is (= 1 (count (:virheet virhevastaus))))
    (is (= "Tietolajia ei löydy" (first (:virheet virhevastaus))))))

(deftest lue-tietolajien-hakuvastaus
  (let [xml (slurp "resources/xsd/tierekisteri/esimerkit/hae-tietolaji-response.xml")
        vastaus (vastaus/lue xml)
        tietolaji (:tietolaji vastaus)
        ominaisuus (second (:ominaisuudet (:tietolaji vastaus)))
        koodisto (:koodisto (nth  (:ominaisuudet (:tietolaji vastaus)) 2))
        koodi (first koodisto)]

    (is (= "tl506" (:tunniste tietolaji)))
    (is (= 15 (count (:ominaisuudet (:tietolaji vastaus)))))

    (is (= "asetusnr" (:kenttatunniste ominaisuus)))
    (is (= 2 (:jarjestysnumero ominaisuus)))
    (is (= 12 (:pituus ominaisuus)))
    (is (:pakollinen ominaisuus))
    (is (= "Liikennemerkin tieliikenneasetuksen mukainen numero on pakollinen tieto." (:selite ominaisuus)))
    (is (= :merkkijono (:tietotyyppi ominaisuus)))
    (is (= (.parse (SimpleDateFormat. "yyyy-MM-dd") "2015-05-26") (:alkupvm (:voimassaolo ominaisuus))))

    (is (= 3 (count koodisto)))
    (is (= "test" (:koodiryhma koodi)))
    (is (= 1 (:koodi koodi)))
    (is (= "YK" (:lyhenne koodi)))
    (is (= "yksityistie" (:selite koodi)))
    (is (= (.parse (SimpleDateFormat. "yyyy-MM-dd") "2015-05-26") (:muutospvm koodi)))    ))

