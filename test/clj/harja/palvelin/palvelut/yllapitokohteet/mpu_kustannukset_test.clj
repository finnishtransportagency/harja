(ns harja.palvelin.palvelut.yllapitokohteet.mpu-kustannukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.mpu-kustannukset :as mpu-kustannukset]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kustannukset (component/using
                          (mpu-kustannukset/->MPUKustannukset)
                          [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(defn- tee-kutsu [params kutsu]
  (kutsu-palvelua (:http-palvelin jarjestelma) kutsu +kayttaja-jvh+ params))


(deftest hae-mpu-kustannukset-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        vastaus (tee-kutsu {:vuosi 2024
                            :urakka-id urakka-id} :hae-mpu-kustannukset)]

    (is (= (-> vastaus count) 5))
    (is (= (-> vastaus first) {:selite "Arvonmuutokset", :summa 1337M}))
    (is (= (-> vastaus second) {:selite "Indeksi- ja kustannustason muutokset", :summa 80085M}))
    (is (= (-> vastaus (nth 2)) {:selite "Kalustokustannukset", :summa 75000M}))
    (is (= (-> vastaus (nth 3)) {:selite "Muu kustannus", :summa 1000000M}))
    (is (= (-> vastaus (nth 4)) {:selite "Työvoimakustannukset", :summa 200000M}))))
