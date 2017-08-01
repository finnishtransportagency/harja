(ns harja.palvelin.palvelut.kayttajatiedot-test
  (:require [clojure.test :refer :all]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.haku :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kayttajatiedot (component/using
                            (kayttajatiedot/->Kayttajatiedot)
                            [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :once jarjestelma-fixture)

(deftest yhteydenpito-vastaanottajat-toimii
  (let [tulos (kutsu-palvelua (:http-palvelin jarjestelma)
                                      :yhteydenpito-vastaanottajat +kayttaja-jvh+ nil)]

    (is (= (count tulos) 14))
    (is (= (vec (distinct (mapcat keys tulos))) [:etunimi :sukunimi :sahkoposti]))))




