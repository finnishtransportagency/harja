(ns harja.palvelin.palvelut.hallintayksikot-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.hallintayksikot :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae (component/using
                      (->Hallintayksikot)
                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest hallintayksikoiden-haku-toimii
  (testing "Tie-hallintayksiköiden haku"
    (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hallintayksikot +kayttaja-jvh+ {:liikennemuoto :tie})]

     (is (not (nil? vastaus)))
     (is (every? (comp (partial = "T") :liikennemuoto) vastaus))
     (is (>= (count vastaus) 5))
     (mapv (fn [hallintayksikko] (is (string? (:nimi hallintayksikko)))) vastaus)))

  (testing "Vesi-hallintayksiköiden haku"
    (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hallintayksikot +kayttaja-jvh+ {:liikennemuoto :vesi})]

      (is (not (nil? vastaus)))
      (is (every? (comp (partial = "V") :liikennemuoto) vastaus))
      (is (>= (count vastaus) 3))
      (mapv (fn [hallintayksikko] (is (string? (:nimi hallintayksikko)))) vastaus)))

  (testing "Kaikkien hallintayksiköiden haku"
    (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hallintayksikot +kayttaja-jvh+ {:liikennemuoto nil})]

      (is (not (nil? vastaus)))
      (is (>= (count vastaus) 5))
      (mapv (fn [hallintayksikko] (is (string? (:nimi hallintayksikko)))) vastaus))))

(deftest organisaation-haku-idlla-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-organisaatio +kayttaja-jvh+ 1)]

    (is (not (nil? vastaus)))
    (is (string? (:nimi vastaus)))))
