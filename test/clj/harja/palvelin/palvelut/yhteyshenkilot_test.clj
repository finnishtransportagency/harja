(ns harja.palvelin.palvelut.yhteyshenkilot-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.yhteyshenkilot :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))


(defn jarjestelma-fixture [testit]
  (pystyta-harja-tarkkailija!)
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae (component/using
                      (->Yhteyshenkilot)
                      [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop)
  (lopeta-harja-tarkkailija!))


(use-fixtures :once (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest urakan-yhteyshenkiloiden-haku-toimii
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-yhteyshenkilot +kayttaja-jvh+ 1)]

    (is (not (nil? vastaus)))
    (is (>= (count vastaus) 1))))

(deftest urakan-paivystajien-haku-toimii
  (u "INSERT INTO paivystys (vastuuhenkilo, varahenkilo, alku, loppu, urakka, yhteyshenkilo) VALUES (true, false, '2005-10-10','2030-06-06', 1, 1)")
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :hae-urakan-paivystajat +kayttaja-jvh+ 1)]
    (log/info "VASTAUS: " vastaus)
    (is (not (nil? vastaus)))
    (is (>= (count vastaus) 1))
    (mapv (fn [yhteyshenkilo] (do
                                (is (string? (:etunimi yhteyshenkilo)))
                                (is (string? (:sukunimi yhteyshenkilo))))) vastaus)))