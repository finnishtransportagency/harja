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
          :db (apply tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae (component/using
                      (->Hallintayksikot)
                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest hallintayksikoiden-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hallintayksikot +kayttaja-jvh+ :tie)]

    (is (not (nil? vastaus)))
    (is (>= (count vastaus) 5))
    (mapv (fn [hallintayksikko] (is (string? (:nimi hallintayksikko)))) vastaus)))

(deftest organisaation-haku-idlla-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-organisaatio +kayttaja-jvh+ 1)]

    (is (not (nil? vastaus)))
    (is (string? (:nimi vastaus)))))
