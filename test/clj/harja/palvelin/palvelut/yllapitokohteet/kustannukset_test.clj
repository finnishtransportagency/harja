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


(deftest hae-kustannusten-selitteet-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        odotettu-vastaus '({:selite "Arvoa muutettiin"}
                           {:selite "Indeksimuutos 2017 elokuu"}
                           {:selite "Indeksimuutos syyskuu"}
                           {:selite "Kalustokustannukset"}
                           {:selite "Työvoimakustannukset"}
                           {:selite "Vanha kustannus"})

        vastaus (tee-kutsu {:urakka-id urakka-id} :hae-kustannusten-selitteet)]

    (is (= vastaus odotettu-vastaus))
    (is (= (-> vastaus count) 6))))


(deftest hae-paikkaus-kustannukset-toimii
  (let [urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        alkupvm (c/to-date (t/local-date 2023 10 1))
        loppupvm (c/to-date (t/local-date 2024 9 30))
        odotettu-vastaus-hk-2023 '({:id "kustannus-1", :tyomenetelma "", :kustannustyyppi "Arvonmuutokset", :kokonaiskustannus 1337M, :selite "Arvoa muutettiin"}
                                   {:id "kustannus-2", :tyomenetelma "", :kustannustyyppi "Indeksi- ja kustannustason muutokset", :kokonaiskustannus 80500M, :selite "Indeksimuutos syyskuu"}
                                   {:id "kustannus-3", :tyomenetelma "", :kustannustyyppi "Muut kustannukset", :kokonaiskustannus 200000M, :selite "Työvoimakustannukset"}
                                   {:id "kustannus-4", :tyomenetelma "", :kustannustyyppi "Muut kustannukset", :kokonaiskustannus 75000M, :selite "Kalustokustannukset"}
                                   {:id "kustannus-5", :tyomenetelma "", :kustannustyyppi "Muut kustannukset", :kokonaiskustannus 75000M, :selite "Vanha kustannus"}
                                   {:id "kustannus-6", :tyomenetelma "", :kustannustyyppi "Indeksi- ja kustannustason muutokset", :kokonaiskustannus 75000M, :selite "Indeksimuutos 2017 elokuu"}
                                   {:id "reikapaikkaus-tyomenetelma-16", :tyomenetelma "AB-paikkaus käsin", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-1", :tyomenetelma "AB-paikkaus levittäjällä", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-11", :tyomenetelma "Avarrussaumaus", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-9", :tyomenetelma "Jyrsintäkorjaukset (HJYR/TJYR)", :kustannustyyppi nil, :kokonaiskustannus 34520.0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-10", :tyomenetelma "Kannukaatosaumaus", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-15", :tyomenetelma "Käsin tehtävät paikkaukset pikapaikkausmassalla", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "paikkauskohde-5", :tyomenetelma "Konetiivistetty reikävaluasfalttipaikkaus (REPA)", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-5", :tyomenetelma "Konetiivistetty reikävaluasfalttipaikkaus (REPA)", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-4", :tyomenetelma "KT-valuasfalttipaikkaus (KTVA)", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-19", :tyomenetelma "Massapintaus", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-18", :tyomenetelma "Muu päällysteiden paikkaustyö", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-17", :tyomenetelma "PAB-paikkaus käsin", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-2", :tyomenetelma "PAB-paikkaus levittäjällä", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-13", :tyomenetelma "Reunapalkin ja päällysteen välisen sauman tiivistäminen", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-14", :tyomenetelma "Reunapalkin liikuntasauman tiivistäminen", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-12", :tyomenetelma "Sillan kannen päällysteen päätysauman korjaukset", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-7", :tyomenetelma "Sirotepintauksena tehty lappupaikkaus (SIPA)", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-6", :tyomenetelma "Sirotepuhalluspaikkaus (SIPU)", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-3", :tyomenetelma "SMA-paikkaus levittäjällä", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}
                                   {:id "reikapaikkaus-tyomenetelma-8", :tyomenetelma "Urapaikkaus (UREM/RREM)", :kustannustyyppi nil, :kokonaiskustannus 215000.0M, :selite ""})

        vastaus (tee-kutsu {:aikavali [alkupvm loppupvm]
                            :urakka-id urakka-id} :hae-paikkaus-kustannukset)]

    (is (= (:kustannukset vastaus) odotettu-vastaus-hk-2023))
    (is (= (-> (:kustannukset vastaus) count) 26))))


(deftest tallenna-yllapito-kustannus-toimii
  (let [vuosi 2024
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")
        vastaus-ennen (tee-kutsu {:vuosi vuosi
                                  :urakka-id urakka-id} :hae-paikkaus-kustannukset)
        odotettu-vastaus {:id "reikapaikkaus-tyomenetelma-16", :tyomenetelma "AB-paikkaus käsin", :kustannustyyppi nil, :kokonaiskustannus 0M, :selite ""}

        _ (tee-kutsu {:urakka-id urakka-id
                      :selite "Päällystettiin Kuusamon luontopolku"
                      :luoja nil
                      :kustannustyyppi "Muut kustannukset"
                      :vuosi vuosi
                      :summa 142000} :tallenna-yllapito-kustannus)

        odotettu-tallennus {:id "kustannus-7", :tyomenetelma "", :kustannustyyppi "Muut kustannukset", :kokonaiskustannus 142000M, :selite "Päällystettiin Kuusamon luontopolku"}

        vastaus-tallennettu (tee-kutsu {:vuosi vuosi
                                        :urakka-id urakka-id} :hae-paikkaus-kustannukset)]

    (is (= (nth (:kustannukset vastaus-ennen) 4) odotettu-vastaus))
    (is (= (nth (:kustannukset vastaus-tallennettu) 4) odotettu-tallennus))
    (is (= (count (:kustannukset vastaus-tallennettu)) (+ (count (:kustannukset vastaus-ennen)) 1)))))


(deftest paivita-yllapito-kustannus-toimii
  (let [vuosi 2024
        urakka-id (hae-urakan-id-nimella "Muhoksen päällystysurakka")

        vastaus-ennen (tee-kutsu {:vuosi vuosi
                                  :urakka-id urakka-id} :hae-paikkaus-kustannukset)

        selite "Korjattiin Juuman karhunkierrosta"
        _ (tee-kutsu {:urakka-id urakka-id
                      :selite selite
                      :luoja nil
                      :kustannustyyppi "Muut kustannukset"
                      :vuosi vuosi
                      :summa 15000}
            :tallenna-yllapito-kustannus)

        vastaus-tallennettu (tee-kutsu {:vuosi vuosi
                                        :urakka-id urakka-id} :hae-paikkaus-kustannukset)

        kustannus-id (ffirst
                       (q (str "SELECT id FROM paikkauskustannukset WHERE selite = '" selite "' AND urakka = '" urakka-id "';")))

        kustannus-avain (str "kustannus-" kustannus-id)

        tallennettu (first
                      (filter #(= kustannus-avain (:id %))
                        (:kustannukset vastaus-tallennettu)))

        uusi-selite "Korjattiin Juuman karhunkierrosta + basecamppia"
        uusi-summa 15555

        ;; :paivita-yllapito-kustannukset odottaa :muokatut-vektoria
        _ (tee-kutsu {:urakka-id urakka-id
                      :vuosi vuosi
                      :muokatut [{:id kustannus-id
                                  :selite uusi-selite
                                  :kustannustyyppi "Muut kustannukset"
                                  :kokonaiskustannus uusi-summa
                                  :poistettu false}]}
            :paivita-yllapito-kustannukset)

        vastaus-muokattu (tee-kutsu {:vuosi vuosi
                                     :urakka-id urakka-id} :hae-paikkaus-kustannukset)

        muokattu (first
                   (filter #(= kustannus-avain (:id %))
                     (:kustannukset vastaus-muokattu)))]

    ;; Tallennettu uusi rivi 
    (is (= 15000M (:kokonaiskustannus tallennettu)))
    (is (= selite (:selite tallennettu)))

    ;; Sama rivi mutta muokattuna  
    (is (= 15555M (:kokonaiskustannus muokattu)))
    (is (= uusi-selite (:selite muokattu)))
    (is (= "Muut kustannukset" (:kustannustyyppi muokattu)))

    ;; Päivitys ei luo uutta riviä
    (is (= (count (:kustannukset vastaus-tallennettu))
          (count (:kustannukset vastaus-muokattu))))

    ;; Alkuperäinen tallennus loi yhden uuden rivin
    (is (= (inc (count (:kustannukset vastaus-ennen)))
          (count (:kustannukset vastaus-tallennettu))))))
