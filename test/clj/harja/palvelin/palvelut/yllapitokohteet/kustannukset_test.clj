(ns harja.palvelin.palvelut.yllapitokohteet.kustannukset-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [com.stuartsierra.component :as component]
            [harja.palvelin.palvelut.yllapitokohteet.kustannukset-palvelu :as kustannukset-palvelu]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kustannukset (component/using
                          (kustannukset-palvelu/->Kustannukset)
                          [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))


(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(defn- tee-kutsu [params kutsu]
  (kutsu-palvelua (:http-palvelin jarjestelma) kutsu +kayttaja-jvh+ params))


(deftest hae-mpu-selitteet-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        odotettu-vastaus '({:selite "Arvoa muutettiin"} 
                           {:selite "Indeksimuutos 2017 elokuu"} 
                           {:selite "Indeksimuutos syyskuu"} 
                           {:selite "Kalustokustannukset"} 
                           {:selite "Työvoimakustannukset"} 
                           {:selite "Vanha kustannus"})

        vastaus (tee-kutsu {:urakka-id urakka-id} :hae-mpu-selitteet)]

    (is (= vastaus odotettu-vastaus))
    (is (= (-> vastaus count) 6))))


(deftest hae-paikkaus-kustannukset-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        alkupvm (c/to-date (t/local-date 2023 10 1))
        loppupvm (c/to-date (t/local-date 2024 9 30))
        odotettu-vastaus-hk-2023 '({:id "paikkauskohde-kustannus-1", :tyomenetelma "", :kustannustyyppi "Arvonmuutokset", :kokonaiskustannus 1337M, :selite "Arvoa muutettiin"}
                                   {:id "paikkauskohde-kustannus-2", :tyomenetelma "", :kustannustyyppi "Indeksi- ja kustannustason muutokset", :kokonaiskustannus 80500M, :selite "Indeksimuutos syyskuu"}
                                   {:id "paikkauskohde-kustannus-3", :tyomenetelma "", :kustannustyyppi "Muut kustannukset", :kokonaiskustannus 200000M, :selite "Työvoimakustannukset"}
                                   {:id "paikkauskohde-kustannus-4", :tyomenetelma "", :kustannustyyppi "Muut kustannukset", :kokonaiskustannus 75000M, :selite "Kalustokustannukset"}
                                   {:id "paikkauskohde-kustannus-5", :tyomenetelma "", :kustannustyyppi "Muut kustannukset", :kokonaiskustannus 75000M, :selite "Vanha kustannus"}
                                   {:id "paikkauskohde-kustannus-6", :tyomenetelma "", :kustannustyyppi "Indeksi- ja kustannustason muutokset", :kokonaiskustannus 75000M, :selite "Indeksimuutos 2017 elokuu"}
                                   {:id "paikkauskohde-tyomenetelma-16", :tyomenetelma "AB-paikkaus käsin", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-1", :tyomenetelma "AB-paikkaus levittäjällä", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-11", :tyomenetelma "Avarrussaumaus", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-9", :tyomenetelma "Jyrsintäkorjaukset (HJYR/TJYR)", :kustannustyyppi nil, :kokonaiskustannus 34520.0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-10", :tyomenetelma "Kannukaatosaumaus", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-15", :tyomenetelma "Käsin tehtävät paikkaukset pikapaikkausmassalla", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-5", :tyomenetelma "Konetiivistetty reikävaluasfalttipaikkaus (REPA)", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-4", :tyomenetelma "KT-valuasfalttipaikkaus (KTVA)", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-19", :tyomenetelma "Massapintaus", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-18", :tyomenetelma "Muu päällysteiden paikkaustyö", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-17", :tyomenetelma "PAB-paikkaus käsin", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-2", :tyomenetelma "PAB-paikkaus levittäjällä", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-13", :tyomenetelma "Reunapalkin ja päällysteen välisen sauman tiivistäminen", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-14", :tyomenetelma "Reunapalkin liikuntasauman tiivistäminen", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-12", :tyomenetelma "Sillan kannen päällysteen päätysauman korjaukset", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-7", :tyomenetelma "Sirotepintauksena tehty lappupaikkaus (SIPA)", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-6", :tyomenetelma "Sirotepuhalluspaikkaus (SIPU)", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-3", :tyomenetelma "SMA-paikkaus levittäjällä", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-tyomenetelma-8", :tyomenetelma "Urapaikkaus (UREM/RREM)", :kustannustyyppi nil, :kokonaiskustannus 215000.0M, :selite ""})
        
        vastaus (tee-kutsu {:aikavali [alkupvm loppupvm]
                            :urakka-id urakka-id} :hae-paikkaus-kustannukset)]

    (is (= (:kustannukset vastaus) odotettu-vastaus-hk-2023))
    (is (= (-> (:kustannukset vastaus) count) 25))))


(deftest tallenna-mpu-kustannus-toimii
  (let [vuosi 2024
        vastaus-maara-ennen 25
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")

        vastaus-ennen (tee-kutsu {:vuosi vuosi
                                  :urakka-id urakka-id} :hae-paikkaus-kustannukset)

        odotettu-vastaus {:id "paikkauskohde-tyomenetelma-16", :tyomenetelma "AB-paikkaus käsin", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}

        _ (tee-kutsu {:urakka-id urakka-id
                      :selite "Päällystettiin Kuusamon luontopolku"
                      :luoja nil
                      :kustannustyyppi "Muut kustannukset"
                      :vuosi vuosi
                      :summa 142000} :tallenna-mpu-kustannus)

        odotettu-tallennus {:id "paikkauskohde-tyomenetelma-7", :tyomenetelma "", :kustannustyyppi "Muut kustannukset", :kokonaiskustannus 142000M, :selite "Päällystettiin Kuusamon luontopolku"}

        vastaus-tallennettu (tee-kutsu {:vuosi vuosi
                                        :urakka-id urakka-id} :hae-paikkaus-kustannukset)]

    (is (= (nth (:kustannukset vastaus-ennen) 4) odotettu-vastaus))
    (is (= (count (:kustannukset vastaus-ennen)) vastaus-maara-ennen))

    (is (= (nth (:kustannukset vastaus-tallennettu) 4) odotettu-tallennus))
    (is (= (count (:kustannukset vastaus-tallennettu)) (+ vastaus-maara-ennen 1)))))
