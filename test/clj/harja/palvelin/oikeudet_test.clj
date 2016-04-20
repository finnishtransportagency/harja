(ns harja.palvelin.oikeudet-test
    (:require [clojure.test :refer :all]
              [taoensso.timbre :as log]
              [harja.domain.roolit :as roolit]
              [harja.testi :refer :all]))

;; PENDING: nämä testit ovat vanhentuneet Sähke käyttäjähallinnan myötä


#_(deftest vaadi-rooli-urakassa-on-jvh
  (let [kayttaja +kayttaja-jvh+
        urakka 1
        rooli "jarjestelmavastuuhenkilo"]
    (roolit/vaadi-rooli-urakassa kayttaja rooli urakka)))

#_(deftest vaadi-rooli-urakassa-ei-ole-jvh
  (let [kayttaja +kayttaja-tero+
        urakka 1
        rooli "jarjestelmavastuuhenkilo"]
    (is (thrown? RuntimeException (roolit/vaadi-rooli-urakassa kayttaja rooli urakka)))))

#_(deftest rooli-urakassa-ei-ole-jvh
  (let [kayttaja +kayttaja-tero+
        urakka 1
        rooli "jarjestelmavastuuhenkilo"]
    (is (= (roolit/rooli-urakassa? kayttaja rooli urakka) false))))

#_(deftest rooli-urakassa-on-jvh
  (let [kayttaja +kayttaja-jvh+
        urakka 1
        rooli "jarjestelmavastuuhenkilo"]
    (is (= (roolit/rooli-urakassa? kayttaja rooli urakka) true))))
