(ns harja.palvelin.palvelut.yhteyshenkilot-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yhteyshenkilot :refer :all]
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
                      (->Yhteyshenkilot)
                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest urakan-yhteyshenkiloiden-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-yhteyshenkilot +kayttaja-jvh+ 1)]

    (log/debug vastaus)
    (is (not (nil? vastaus)))
    (is (>= (count vastaus) 1))))