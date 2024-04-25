(ns harja.palvelin.palvelut.yllapitokohteet.mpu-kustannukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
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


(deftest hae-paikkaus-kustannukset-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        alkupvm (c/to-date (t/local-date 2023 10 1))
        loppupvm (c/to-date (t/local-date 2024 9 30))
        odotettu-vastaus-hk-2023 '({:id 16, :kokonaiskustannus 0M, :tyomenetelma "AB-paikkaus käsin"}
                                   {:id 1, :kokonaiskustannus 0M, :tyomenetelma "AB-paikkaus levittäjällä"}
                                   {:id 11, :kokonaiskustannus 0M, :tyomenetelma "Avarrussaumaus"}
                                   {:id 9, :kokonaiskustannus 34520.0M, :tyomenetelma "Jyrsintäkorjaukset (HJYR/TJYR)"}
                                   {:id 10, :kokonaiskustannus 0M, :tyomenetelma "Kannukaatosaumaus"}
                                   {:id 15, :kokonaiskustannus 0M, :tyomenetelma "Käsin tehtävät paikkaukset pikapaikkausmassalla"}
                                   {:id 5, :kokonaiskustannus 0M, :tyomenetelma "Konetiivistetty reikävaluasfalttipaikkaus (REPA)"}
                                   {:id 4, :kokonaiskustannus 0M, :tyomenetelma "KT-valuasfalttipaikkaus (KTVA)"}
                                   {:id 19, :kokonaiskustannus 0M, :tyomenetelma "Massapintaus"}
                                   {:id 18, :kokonaiskustannus 0M, :tyomenetelma "Muu päällysteiden paikkaustyö"}
                                   {:id 17, :kokonaiskustannus 0M, :tyomenetelma "PAB-paikkaus käsin"}
                                   {:id 2, :kokonaiskustannus 0M, :tyomenetelma "PAB-paikkaus levittäjällä"}
                                   {:id 13, :kokonaiskustannus 0M, :tyomenetelma "Reunapalkin ja päällysteen välisen sauman tiivistäminen"}
                                   {:id 14, :kokonaiskustannus 0M, :tyomenetelma "Reunapalkin liikuntasauman tiivistäminen"}
                                   {:id 12, :kokonaiskustannus 0M, :tyomenetelma "Sillan kannen päällysteen päätysauman korjaukset"}
                                   {:id 7, :kokonaiskustannus 0M, :tyomenetelma "Sirotepintauksena tehty lappupaikkaus (SIPA)"}
                                   {:id 6, :kokonaiskustannus 0M, :tyomenetelma "Sirotepuhalluspaikkaus (SIPU)"}
                                   {:id 3, :kokonaiskustannus 0M, :tyomenetelma "SMA-paikkaus levittäjällä"}
                                   {:id 8, :kokonaiskustannus 215000.0M, :tyomenetelma "Urapaikkaus (UREM/RREM)"})

        vastaus (tee-kutsu {:aikavali [alkupvm loppupvm]
                            :urakka-id urakka-id} :hae-paikkaus-kustannukset)]

    (is (= vastaus odotettu-vastaus-hk-2023))
    (is (= (-> vastaus count) 19))))
