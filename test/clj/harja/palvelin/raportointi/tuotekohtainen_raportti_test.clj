(ns harja.palvelin.raportointi.tuotekohtainen-raportti-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konversio]
            [clojure.string :as str]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.http-palvelin :as palvelin]
            [harja.palvelin.raportointi :refer [suorita-raportti] :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :pdf-vienti (component/using
                                      (pdf-vienti/luo-pdf-vienti)
                                      [:http-palvelin])
                        :raportointi (component/using
                                       (raportointi/luo-raportointi)
                                       [:db :pdf-vienti])
                        :raportit (component/using
                                    (raportit/->Raportit)
                                    [:http-palvelin :db :raportointi :pdf-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(defn generoi-avaimet [name prefix]
  ;; Generoi clojure keywordit rahavarauksille
  (-> name
    (str/lower-case)
    (str/replace #"ä" "a")
    (str/replace #"ö" "o")
    (str/replace #"[^a-z0-9]+" "_")
    (str "_" prefix)
    keyword))


(defn pura-laskutusraportti-mapiksi [rivi]
  (let [tulos
        {:nimi (nth rivi 0)
         :maksuera_numero (nth rivi 1)
         :tuotekoodi (nth rivi 2)
         :tpi (nth rivi 3)
         :perusluku (nth rivi 4)
         :kaikki_laskutettu (nth rivi 5)
         :kaikki_laskutetaan (nth rivi 6)
         :tavoitehintaiset_laskutettu (nth rivi 7)
         :tavoitehintaiset_laskutetaan (nth rivi 8)
         :lisatyot_laskutettu (nth rivi 9)
         :lisatyot_laskutetaan (nth rivi 10)
         :hankinnat_laskutettu (nth rivi 11)
         :hankinnat_laskutetaan (nth rivi 12)
         :sakot_laskutettu (nth rivi 13)
         :sakot_laskutetaan (nth rivi 14)
         :alihank_bon_laskutettu (nth rivi 15)
         :alihank_bon_laskutetaan (nth rivi 16)
         :johto_ja_hallinto_laskutettu (nth rivi 17)
         :johto_ja_hallinto_laskutetaan (nth rivi 18)
         :bonukset_laskutettu (nth rivi 19)
         :bonukset_laskutetaan (nth rivi 20)
         :hj_palkkio_laskutettu (nth rivi 21)
         :hj_palkkio_laskutetaan (nth rivi 22)
         :hj_erillishankinnat_laskutettu (nth rivi 23)
         :hj_erillishankinnat_laskutetaan (nth rivi 24)
         :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu (nth rivi 25)
         :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan (nth rivi 26)
         :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu (nth rivi 27)
         :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan (nth rivi 28)
         :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu (nth rivi 29)
         :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan (nth rivi 30)
         :indeksi_puuttuu (nth rivi 31)
         ;; Urakan rahavaraukset ja arvot
         :rahavaraus_nimet (nth rivi 32)
         :hoitokausi_yht_array (nth rivi 33)
         :val_aika_yht_array (nth rivi 34)
         :kaikki_rahavaraukset_val_yht (nth rivi 35)
         :kaikki_rahavaraukset_hoitokausi_yht (nth rivi 36)}]
    tulos))


(defn parsi-tuotekohtainen-raportti [vastaus]
  (map (fn [rivi]
         (let [purettu (pura-laskutusraportti-mapiksi rivi)
               rahavaraukset-nimet (konversio/pgarray->vector (:rahavaraus_nimet purettu))
               rahavaraukset-val-aika (konversio/pgarray->vector (:val_aika_yht_array purettu))
               rahavaraukset-hoitokausi (konversio/pgarray->vector (:hoitokausi_yht_array purettu))

               ;; Rahavaraukset hoitokausi
               purettu-hoitokausi (reduce (fn [acc [nimi arvo]]
                                            (assoc acc (generoi-avaimet nimi "hk") arvo))
                                    purettu
                                    (map vector rahavaraukset-nimet rahavaraukset-hoitokausi))

               ;; Rahavaraukset valittu aika 
               koko-rivi (reduce (fn [acc [nimi arvo]]
                                   (assoc acc (generoi-avaimet nimi "val") arvo))
                           purettu-hoitokausi
                           (map vector rahavaraukset-nimet rahavaraukset-val-aika))]
           koko-rivi))
    vastaus))


(deftest tuotekohtainen-laskutusyhteenveto-raportti-toimii
  (palvelin/julkaise-palvelu (:http-palvelin jarjestelma) :suorita-raportti
    (fn [user raportti]
      (suorita-raportti (:raportointi jarjestelma) user raportti))
    {:trace false})

  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vastaus (q (format "select * from mhu_laskutusyhteenveto_teiden_hoito('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                     hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        ;; "Talvihoito", "Liikenneympäristön hoito", "Soratien hoito", "Päällyste", "MHU Ylläpito",  "MHU ja HJU hoidon johto", "MHU Korvausinvestointi"
        vastaus (parsi-tuotekohtainen-raportti vastaus)
        talvihoito (first vastaus)
        liikenneymp-hoito (second vastaus)
        soratien-hoito (nth vastaus 2)
        mhu-ja-hoidon-johto (nth vastaus 3)
        paallyste (nth vastaus 4)
        mhu-yllapito (nth vastaus 5)
        mhu-korvausinvestointi (nth vastaus 6)

        

        _ (println "\n talvihoito" talvihoito)
        _ (println "\n liikenneymp-hoito" liikenneymp-hoito)
        _ (println "\n soratien-hoito" soratien-hoito)
        _ (println "\n paallyste" paallyste)
        _ (println "\n mhu-yllapito" mhu-yllapito)
        _ (println "\n mhu-ja-hoidon-johto" mhu-ja-hoidon-johto)
        _ (println "\n mhu-korvausinvestointi" mhu-korvausinvestointi)


        talvihoito-hankinnat (:hankinnat_laskutettu talvihoito)
        talvihoito-lisatyot (:lisatyot_laskutettu talvihoito)
        talvihoito-sanktiot (:sakot_laskutettu talvihoito)
        talvihoito-akilliset-hoitotyot (:akilliset_hoitotyot_hk talvihoito)
        talvihoito-tilaajan-rahavaraus (:tilaajan_rahavaraus_kannustinjarjestelmaan_hk talvihoito)
        talvihoito-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk talvihoito)
        talvihoito-muut-tavoitehintaan-vaikuttavat-kulut (:muut_tavoitehintaan_vaikuttavat_kulut_hk talvihoito)
        talvihoito-muut-tavoitehinnan-ulkopuoliset-kulut (:muut_tavoitehinnan_ulkopuoliset_kulut_hk talvihoito)
        talvihoito-yhteensa (:kaikki_laskutettu talvihoito)

        ;; Liikenneympäristön hoito 
        liikenneymp-hankinnat (:hankinnat_laskutettu liikenneymp-hoito)
        liikenneymp-lisatyot (:lisatyot_laskutettu liikenneymp-hoito)
        liikenneymp-sanktiot (:sakot_laskutettu liikenneymp-hoito)
        liikenneymp-akilliset-hoitotyot (:akilliset_hoitotyot_hk liikenneymp-hoito)
        liikenneymp-tilaajan-rahavaraus (:tilaajan_rahavaraus_kannustinjarjestelmaan_hk liikenneymp-hoito)
        liikenneymp-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk liikenneymp-hoito)
        liikenneymp-muut-tavoitehintaan-vaikuttavat-kulut (:muut_tavoitehintaan_vaikuttavat_kulut_hk liikenneymp-hoito)
        liikenneymp-muut-tavoitehinnan-ulkopuoliset-kulut (:muut_tavoitehinnan_ulkopuoliset_kulut_hk liikenneymp-hoito)
        liikenneymp-yhteensa (:kaikki_laskutettu liikenneymp-hoito)

        ;; Soratien hoito
        soratien-hankinnat (:hankinnat_laskutettu soratien-hoito)
        soratien-lisatyot (:lisatyot_laskutettu soratien-hoito)
        soratien-sanktiot (:sakot_laskutettu soratien-hoito)
        soratien-akilliset-hoitotyot (:akilliset_hoitotyot_hk soratien-hoito)
        soratien-tilaajan-rahavaraus (:tilaajan_rahavaraus_kannustinjarjestelmaan_hk soratien-hoito)
        soratien-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk soratien-hoito)
        soratien-muut-tavoitehintaan-vaikuttavat-kulut (:muut_tavoitehintaan_vaikuttavat_kulut_hk soratien-hoito)
        soratien-muut-tavoitehinnan-ulkopuoliset-kulut (:muut_tavoitehinnan_ulkopuoliset_kulut_hk soratien-hoito)
        soratien-yhteensa (:kaikki_laskutettu soratien-hoito)

        ;; Päällyste
        paallyste-hankinnat (:hankinnat_laskutettu paallyste)
        paallyste-lisatyot (:lisatyot_laskutettu paallyste)
        paallyste-sanktiot (:sakot_laskutettu paallyste)
        paallyste-akilliset-hoitotyot (:akilliset_hoitotyot_hk paallyste)
        paallyste-tilaajan-rahavaraus (:tilaajan_rahavaraus_kannustinjarjestelmaan_hk paallyste)
        paallyste-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk paallyste)
        paallyste-muut-tavoitehintaan-vaikuttavat-kulut (:muut_tavoitehintaan_vaikuttavat_kulut_hk paallyste)
        paallyste-muut-tavoitehinnan-ulkopuoliset-kulut (:muut_tavoitehinnan_ulkopuoliset_kulut_hk paallyste)
        paallyste-yhteensa (:kaikki_laskutettu paallyste)

        ;; Mhu hoidon johto
        mhu-johto-ja-hallintokorvaukset (:johto_ja_hallinto_laskutettu mhu-ja-hoidon-johto)
        mhu-erillishankinnat (:hj_erillishankinnat_laskutettu mhu-ja-hoidon-johto)
        mhu-hj-palkkio (:hj_palkkio_laskutettu mhu-ja-hoidon-johto)
        mhu-bonukset (:bonukset_laskutettu mhu-ja-hoidon-johto)
        mhu-sanktiot (:sakot_laskutettu mhu-ja-hoidon-johto)
        mhu-hoitovuoden-paatos-tavoitepalkkio (:hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu mhu-ja-hoidon-johto)
        mhu-akilliset-hoitotyot (:akilliset_hoitotyot_hk mhu-ja-hoidon-johto)
        mhu-rahavaraus-a (:kaikki_rahavaraukset_hoitokausi_yht mhu-ja-hoidon-johto)
        mhu-tilaajan-rahavaraus (:tilaajan_rahavaraus_kannustinjarjestelmaan_hk mhu-ja-hoidon-johto)
        mhu-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk mhu-ja-hoidon-johto)
        mhu-muut-tavoitehintaan-vaikuttavat-kulut (:muut_tavoitehintaan_vaikuttavat_kulut_hk mhu-ja-hoidon-johto)
        mhu-muut-tavoitehinnan-ulkopuoliset-kulut (:muut_tavoitehinnan_ulkopuoliset_kulut_hk mhu-ja-hoidon-johto)
        mhu-yhteensa (:kaikki_laskutettu mhu-ja-hoidon-johto)]

    ;; Talvihoito 
    (is (= talvihoito-hankinnat 6000.97M))
    (is (= talvihoito-lisatyot 600.97M))
    (is (= talvihoito-sanktiot -1190.148570M))
    (is (= talvihoito-akilliset-hoitotyot 0.0M))
    (is (= talvihoito-tilaajan-rahavaraus 0.0M))
    (is (= talvihoito-vahinkojen-korjaukset 0.0M))
    (is (= talvihoito-muut-tavoitehintaan-vaikuttavat-kulut 0.0M))
    (is (= talvihoito-muut-tavoitehinnan-ulkopuoliset-kulut 0.0M))
    (is (= talvihoito-yhteensa 5411.791430M))

    ;; Liikenneympäristön hoito 
    (is (= liikenneymp-hankinnat 2888.88M))
    (is (= liikenneymp-lisatyot 0.0M))
    (is (= liikenneymp-sanktiot -1081.832370M))
    (is (= liikenneymp-akilliset-hoitotyot 4444.44M))
    (is (= liikenneymp-tilaajan-rahavaraus 0.0M))
    (is (= liikenneymp-vahinkojen-korjaukset 0.0M))
    (is (= liikenneymp-muut-tavoitehintaan-vaikuttavat-kulut 0.0M))
    (is (= liikenneymp-muut-tavoitehinnan-ulkopuoliset-kulut 0.0M))
    (is (= liikenneymp-yhteensa 6251.487630M))

    ;; Soratien hoito
    (is (= soratien-hankinnat 8000.97M))
    (is (= soratien-lisatyot 800.97M))
    (is (= soratien-sanktiot 0.0M))
    (is (= soratien-akilliset-hoitotyot 0.0M))
    (is (= soratien-tilaajan-rahavaraus 0.0M))
    (is (= soratien-vahinkojen-korjaukset 0.0M))
    (is (= soratien-muut-tavoitehintaan-vaikuttavat-kulut 0.0M))
    (is (= soratien-muut-tavoitehinnan-ulkopuoliset-kulut 0.0M))
    (is (= soratien-yhteensa 8801.94M))

    ;; Päällyste
    (is (= paallyste-hankinnat 10000.97M))
    (is (= paallyste-lisatyot 1000.97M))
    (is (= paallyste-sanktiot 0.0M))
    (is (= paallyste-akilliset-hoitotyot 0.0M))
    (is (= paallyste-tilaajan-rahavaraus 0.0M))
    (is (= paallyste-vahinkojen-korjaukset 0.0M))
    (is (= paallyste-muut-tavoitehintaan-vaikuttavat-kulut 0.0M))
    (is (= paallyste-muut-tavoitehinnan-ulkopuoliset-kulut 0.0M))
    (is (= paallyste-yhteensa 11001.94M))
    
    ;; Mhu hoidon johto
    (is (= mhu-johto-ja-hallintokorvaukset 10.20M))
    (is (= mhu-erillishankinnat 366.754000M))
    (is (= mhu-hj-palkkio 113.80M))
    (is (= mhu-bonukset 5634.50M))
    (is (= mhu-sanktiot -2081.00M))
    (is (= mhu-hoitovuoden-paatos-tavoitepalkkio 1500.00M))
    (is (= mhu-akilliset-hoitotyot 0.0M))
    (is (= mhu-rahavaraus-a 0.0M))
    (is (= mhu-tilaajan-rahavaraus 0.0M))
    (is (= mhu-vahinkojen-korjaukset 0.0M))
    (is (= mhu-muut-tavoitehintaan-vaikuttavat-kulut 0.0M))
    (is (= mhu-muut-tavoitehinnan-ulkopuoliset-kulut 0.0M))
    (is (= mhu-yhteensa 5544.254000M))
    ))
