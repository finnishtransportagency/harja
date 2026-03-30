(ns harja.palvelin.raportointi.tuotekohtainen-raportti-test
  (:require [clojure.test :refer :all]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]
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
        {:nimi (:nimi rivi)
         :maksuera_numero (:maksuera_numero rivi)
         :tuotekoodi (:tuotekoodi rivi)
         :tpi (:tpi rivi)
         :perusluku (:perusluku rivi)
         :kaikki_laskutettu (:kaikki_laskutettu rivi)
         :kaikki_laskutetaan (:kaikki_laskutetaan rivi)
         :tavoitehintaiset_laskutettu (:tavoitehintaiset_laskutettu rivi)
         :tavoitehintaiset_laskutetaan (:tavoitehintaiset_laskutetaan rivi)
         :lisatyot_laskutettu (:lisatyot_laskutettu rivi)
         :lisatyot_laskutetaan (:lisatyot_laskutetaan rivi)
         :hankinnat_laskutettu (:hankinnat_laskutettu rivi)
         :hankinnat_laskutetaan (:hankinnat_laskutetaan rivi)
         :sakot_laskutettu (:sakot_laskutettu rivi)
         :sakot_laskutetaan (:sakot_laskutetaan rivi)
         :alihank_bon_laskutettu (:alihank_bon_laskutettu rivi)
         :alihank_bon_laskutetaan (:alihank_bon_laskutetaan rivi)
         :johto_ja_hallinto_laskutettu (:johto_ja_hallinto_laskutettu rivi)
         :johto_ja_hallinto_laskutetaan (:johto_ja_hallinto_laskutetaan rivi)
         :jjh_muutokset_laskutettu (:jjh_muutokset_laskutettu rivi)
         :jjh_muutokset_laskutetaan (:jjh_muutokset_laskutetaan rivi)
         :bonukset_laskutettu (:bonukset_laskutettu rivi)
         :bonukset_laskutetaan (:bonukset_laskutetaan rivi)
         :hj_palkkio_laskutettu (:hj_palkkio_laskutettu rivi)
         :hj_palkkio_laskutetaan (:hj_palkkio_laskutetaan rivi)
         :hj_erillishankinnat_laskutettu (:hj_erillishankinnat_laskutettu rivi)
         :hj_erillishankinnat_laskutetaan (:hj_erillishankinnat_laskutetaan rivi)
         :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu (:hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu rivi)
         :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan (:hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan rivi)
         :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu (:hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu rivi)
         :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan (:hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan rivi)
         :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu (:hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu rivi)
         :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan (:hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan rivi)
         :hj_paattaminen_hoidonjohtopalkkion_muutos_laskutettu (:hj_paattaminen_hoidonjohtopalkkion_muutos_laskutettu rivi)
         :hj_paattaminen_hoidonjohtopalkkion_muutos_laskutetaan (:hj_paattaminen_hoidonjohtopalkkion_muutos_laskutetaan rivi)
         :indeksi_puuttuu (:indeksi_puuttuu rivi)
         ;; Urakan rahavaraukset ja arvot
         :rahavaraus_nimet (:rahavaraus_nimet rivi)
         :hoitokausi_yht_array (:hoitokausi_yht_array rivi)
         :val_aika_yht_array (:val_aika_yht_array rivi)
         :kaikki_rahavaraukset_val_yht (:kaikki_rahavaraukset_val_yht rivi)
         :kaikki_rahavaraukset_hoitokausi_yht (:kaikki_rahavaraukset_hoitokausi_yht rivi)}]
    tulos))


(defn parsi-tuotekohtainen-laskutus-vastaus [vastaus]
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


(deftest tuotekohtainen-laskutusyhteenveto-2019-oulu-sql-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vastaus (q-map (format "select * from mhu_laskutusyhteenveto_tuotekohtainen('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                     hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        vastaus (parsi-tuotekohtainen-laskutus-vastaus vastaus)
        talvihoito (first vastaus)
        liikenneymp-hoito (second vastaus)
        soratien-hoito (nth vastaus 2)
        mhu-ja-hoidon-johto (nth vastaus 3)
        paallyste (nth vastaus 4)
        mhu-yllapito (nth vastaus 5)
        mhu-korvausinvestointi (nth vastaus 6)

        ;; Talvihoito
        talvihoito-hankinnat (:hankinnat_laskutettu talvihoito)
        talvihoito-lisatyot (:lisatyot_laskutettu talvihoito)
        talvihoito-sanktiot (:sakot_laskutettu talvihoito)
        talvihoito-akilliset-hoitotyot (:akilliset_hoitotyot_hk talvihoito)
        talvihoito-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk talvihoito)
        talvihoito-yhteensa (:kaikki_laskutettu talvihoito)

        ;; Liikenneympäristön hoito
        liikenneymp-hankinnat (:hankinnat_laskutettu liikenneymp-hoito)
        liikenneymp-lisatyot (:lisatyot_laskutettu liikenneymp-hoito)
        liikenneymp-sanktiot (:sakot_laskutettu liikenneymp-hoito)
        liikenneymp-akilliset-hoitotyot (:akilliset_hoitotyot_hk liikenneymp-hoito)
        liikenneymp-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk liikenneymp-hoito)
        liikenneymp-rahavaraukset (:kaikki_rahavaraukset_hoitokausi_yht liikenneymp-hoito)
        liikenneymp-yhteensa (:kaikki_laskutettu liikenneymp-hoito)

        ;; Soratien hoito
        soratien-hankinnat (:hankinnat_laskutettu soratien-hoito)
        soratien-lisatyot (:lisatyot_laskutettu soratien-hoito)
        soratien-sanktiot (:sakot_laskutettu soratien-hoito)
        soratien-akilliset-hoitotyot (:akilliset_hoitotyot_hk soratien-hoito)
        soratien-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk soratien-hoito)
        soratien-yhteensa (:kaikki_laskutettu soratien-hoito)

        ;; Päällyste
        paallyste-hankinnat (:hankinnat_laskutettu paallyste)
        paallyste-lisatyot (:lisatyot_laskutettu paallyste)
        paallyste-sanktiot (:sakot_laskutettu paallyste)
        paallyste-yhteensa (:kaikki_laskutettu paallyste)

        ;; Mhu ylläpito
        mhu-yllapito-hankinnat (:hankinnat_laskutettu mhu-yllapito)
        mhu-yllapito-lisatyot (:lisatyot_laskutettu mhu-yllapito)
        mhu-yllapito-sanktiot (:sakot_laskutettu mhu-yllapito)
        mhu-yllapito-rahavaraus (:kaikki_rahavaraukset_hoitokausi_yht mhu-yllapito)
        mhu-yllapito-tilaajan-rahavaraus (:tilaajan_rahavaraus_kannustinjarjestelmaan_hk mhu-yllapito)
        mhu-yllapito-yhteensa (:kaikki_laskutettu mhu-yllapito)

        ;; Mhu hoidon johto
        mhu-johto-ja-hallintokorvaukset (:johto_ja_hallinto_laskutettu mhu-ja-hoidon-johto)
        mhu-erillishankinnat (:hj_erillishankinnat_laskutettu mhu-ja-hoidon-johto)
        mhu-hj-palkkio (:hj_palkkio_laskutettu mhu-ja-hoidon-johto)
        mhu-bonukset (:bonukset_laskutettu mhu-ja-hoidon-johto)
        mhu-sanktiot (:sakot_laskutettu mhu-ja-hoidon-johto)
        mhu-jjh-muutokset_laskutettu (:jjh_muutokset_laskutettu mhu-ja-hoidon-johto)
        mhu-jjh-muutokset_laskutetaan (:jjh_muutokset_laskutetaan mhu-ja-hoidon-johto)
        mhu-hoitovuoden-paatos-tavoitepalkkio (:hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu mhu-ja-hoidon-johto)
        mhu-rahavaraus-a (:kaikki_rahavaraukset_hoitokausi_yht mhu-ja-hoidon-johto)
        mhu-yhteensa (:kaikki_laskutettu mhu-ja-hoidon-johto)

        ;; Mhu korvausinvestointi
        mhu-korvausinvestointi-hankinnat (:hankinnat_laskutettu mhu-korvausinvestointi)
        mhu-korvausinvestointi-lisatyot (:lisatyot_laskutettu mhu-korvausinvestointi)
        mhu-korvausinvestointi-sanktiot (:sakot_laskutettu mhu-korvausinvestointi)
        mhu-korvausinvestointi-rahavaraus-a (:kaikki_rahavaraukset_hoitokausi_yht mhu-korvausinvestointi)
        mhu-korvausinvestointi-yhteensa (:kaikki_laskutettu mhu-korvausinvestointi)]

    ;; Talvihoito 
    (is (= talvihoito-hankinnat 6000.97M))
    (is (= talvihoito-lisatyot 600.97M))
    (is (= talvihoito-sanktiot -1190.148570M))
    (is (= talvihoito-akilliset-hoitotyot 0.0M))
    (is (= talvihoito-vahinkojen-korjaukset 0.0M))
    (is (= talvihoito-yhteensa 5411.791430M))

    ;; Liikenneympäristön hoito 
    (is (= liikenneymp-hankinnat 2888.88M))
    (is (= liikenneymp-lisatyot 0.0M))
    (is (= liikenneymp-sanktiot -1081.832370M))
    (is (= liikenneymp-akilliset-hoitotyot 4444.44M))
    (is (= liikenneymp-vahinkojen-korjaukset 0.0M))
    (is (= liikenneymp-rahavaraukset 4444.44M))
    (is (= liikenneymp-yhteensa 6251.487630M))

    ;; Soratien hoito
    (is (= soratien-hankinnat 8000.97M))
    (is (= soratien-lisatyot 800.97M))
    (is (= soratien-sanktiot 0.0M))
    (is (= soratien-akilliset-hoitotyot 0.0M))
    (is (= soratien-vahinkojen-korjaukset 0.0M))
    (is (= soratien-yhteensa 8801.94M))

    ;; Päällyste
    (is (= paallyste-hankinnat 10000.97M))
    (is (= paallyste-lisatyot 1000.97M))
    (is (= paallyste-sanktiot 0.0M))
    (is (= paallyste-yhteensa 11001.94M))

    ;; Mhu ylläpito
    (is (= mhu-yllapito-hankinnat 14000.97M))
    (is (= mhu-yllapito-lisatyot 1400.97M))
    (is (= mhu-yllapito-sanktiot 0.0M))
    (is (= mhu-yllapito-rahavaraus 1000.0M))
    (is (= mhu-yllapito-tilaajan-rahavaraus 0.0M))
    (is (= mhu-yllapito-yhteensa 16401.94M))

    ;; Mhu hoidon johto
    (is (= mhu-johto-ja-hallintokorvaukset 10.20M))
    (is (= mhu-erillishankinnat 366.754000M))
    (is (= mhu-hj-palkkio 113.80M))
    (is (= mhu-bonukset 5634.50M))
    (is (= mhu-sanktiot -2081.00M))
    (is (= mhu-jjh-muutokset_laskutettu 0.0M))
    (is (= mhu-jjh-muutokset_laskutetaan 0.0M))
    (is (= mhu-hoitovuoden-paatos-tavoitepalkkio 1500.00M))
    (is (= mhu-rahavaraus-a 0.0M))
    (is (= mhu-yhteensa 5544.254000M))

    ;; Mhu korvausinvenstointi
    (is (= mhu-korvausinvestointi-hankinnat 12000.97M))
    (is (= mhu-korvausinvestointi-lisatyot 1200.97M))
    (is (= mhu-korvausinvestointi-sanktiot 0.0M))
    (is (= mhu-korvausinvestointi-rahavaraus-a 0.0M))
    (is (= mhu-korvausinvestointi-yhteensa 13201.94M))))

(deftest tuotekohtainen-laskutusyhteenveto-2025-ii-sql-toimii
  (let [hk_alkupvm "2025-10-01"
        hk_loppupvm "2026-09-30"
        aikavali_alkupvm "2025-10-01"
        aikavali_loppupvm "2026-09-30"
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        urakan-tiedot (first (urakat-q/hae-urakan-tiedot (:db jarjestelma) {:id urakka-id}))
        vastaus (q-map (format "select * from mhu_laskutusyhteenveto_tuotekohtainen('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                         hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        ;; Haetaan budjetoidut summat
        hoitokausi (pvm/paivamaara->mhu-hoitovuosi-nro (:alkupvm urakan-tiedot) (pvm/luo-pvm-dec-kk 2025 10 1))
        urakka-tavoite (first (filter #(= (:hoitokausi %) hoitokausi) (budjetti-q/hae-budjettitavoite (:db jarjestelma) {:urakka urakka-id})))

        vastaus (parsi-tuotekohtainen-laskutus-vastaus vastaus)
        talvihoito (first vastaus)
        liikenneymp-hoito (second vastaus)
        soratien-hoito (nth vastaus 2)
        mhu-ja-hoidon-johto (nth vastaus 3)
        paallyste (nth vastaus 4)
        mhu-yllapito (nth vastaus 5)
        mhu-korvausinvestointi (nth vastaus 6)

        ;; Kirjallisesti sovitut muutokset
        _ (is (= 6230M (:muutos-summa urakka-tavoite)))

        ;; Talvihoito
        talvihoito-hankinnat (:hankinnat_laskutettu talvihoito)
        talvihoito-lisatyot (:lisatyot_laskutettu talvihoito)
        talvihoito-sanktiot (:sakot_laskutettu talvihoito)
        talvihoito-akilliset-hoitotyot (:akilliset_hoitotyot_hk talvihoito)
        talvihoito-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk talvihoito)
        talvihoito-yhteensa (:kaikki_laskutettu talvihoito)

        ;; Liikenneympäristön hoito
        liikenneymp-hankinnat (:hankinnat_laskutettu liikenneymp-hoito)
        liikenneymp-lisatyot (:lisatyot_laskutettu liikenneymp-hoito)
        liikenneymp-sanktiot (:sakot_laskutettu liikenneymp-hoito)
        liikenneymp-akilliset-hoitotyot (:akilliset_hoitotyot_hk liikenneymp-hoito)
        liikenneymp-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk liikenneymp-hoito)
        liikenneymp-rahavaraukset (:kaikki_rahavaraukset_hoitokausi_yht liikenneymp-hoito)
        liikenneymp-yhteensa (:kaikki_laskutettu liikenneymp-hoito)

        ;; Soratien hoito
        soratien-hankinnat (:hankinnat_laskutettu soratien-hoito)
        soratien-lisatyot (:lisatyot_laskutettu soratien-hoito)
        soratien-sanktiot (:sakot_laskutettu soratien-hoito)
        soratien-akilliset-hoitotyot (:akilliset_hoitotyot_hk soratien-hoito)
        soratien-vahinkojen-korjaukset (:vahinkojen_korjaukset_hk soratien-hoito)
        soratien-yhteensa (:kaikki_laskutettu soratien-hoito)

        ;; Päällyste
        paallyste-hankinnat (:hankinnat_laskutettu paallyste)
        paallyste-lisatyot (:lisatyot_laskutettu paallyste)
        paallyste-sanktiot (:sakot_laskutettu paallyste)
        paallyste-yhteensa (:kaikki_laskutettu paallyste)

        ;; Mhu ylläpito
        mhu-yllapito-hankinnat (:hankinnat_laskutettu mhu-yllapito)
        mhu-yllapito-lisatyot (:lisatyot_laskutettu mhu-yllapito)
        mhu-yllapito-sanktiot (:sakot_laskutettu mhu-yllapito)
        mhu-yllapito-rahavaraus (:kaikki_rahavaraukset_hoitokausi_yht mhu-yllapito)
        mhu-yllapito-tilaajan-rahavaraus (:tilaajan_rahavaraus_kannustinjarjestelmaan_hk mhu-yllapito)
        mhu-yllapito-yhteensa (:kaikki_laskutettu mhu-yllapito)

        ;; Mhu hoidon johto
        mhu-johto-ja-hallintokorvaukset (:johto_ja_hallinto_laskutettu mhu-ja-hoidon-johto)
        mhu-erillishankinnat (:hj_erillishankinnat_laskutettu mhu-ja-hoidon-johto)
        mhu-hj-palkkio (:hj_palkkio_laskutettu mhu-ja-hoidon-johto)
        mhu-bonukset (:bonukset_laskutettu mhu-ja-hoidon-johto)
        mhu-sanktiot (:sakot_laskutettu mhu-ja-hoidon-johto)
        mhu-jjh-muutokset_laskutettu (:jjh_muutokset_laskutettu mhu-ja-hoidon-johto)
        mhu-jjh-muutokset_laskutetaan (:jjh_muutokset_laskutetaan mhu-ja-hoidon-johto)
        mhu-hoitovuoden-paatos-tavoitepalkkio (:hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu mhu-ja-hoidon-johto)
        mhu-rahavaraus-a (:kaikki_rahavaraukset_hoitokausi_yht mhu-ja-hoidon-johto)
        mhu-yhteensa (:kaikki_laskutettu mhu-ja-hoidon-johto)

        ;; Mhu korvausinvestointi
        mhu-korvausinvestointi-hankinnat (:hankinnat_laskutettu mhu-korvausinvestointi)
        mhu-korvausinvestointi-lisatyot (:lisatyot_laskutettu mhu-korvausinvestointi)
        mhu-korvausinvestointi-sanktiot (:sakot_laskutettu mhu-korvausinvestointi)
        mhu-korvausinvestointi-rahavaraus-a (:kaikki_rahavaraukset_hoitokausi_yht mhu-korvausinvestointi)
        mhu-korvausinvestointi-yhteensa (:kaikki_laskutettu mhu-korvausinvestointi)]

    ;; Talvihoito
    (is (= talvihoito-hankinnat 0.0M))
    (is (= talvihoito-lisatyot 0.0M))
    (is (= talvihoito-sanktiot 0.0M))
    (is (= talvihoito-akilliset-hoitotyot 0.0M))
    (is (= talvihoito-vahinkojen-korjaukset 0.0M))
    (is (= talvihoito-yhteensa 0.0M))

    ;; Liikenneympäristön hoito
    (is (= liikenneymp-hankinnat 1467.0M))
    (is (= liikenneymp-lisatyot 0.0M))
    (is (= liikenneymp-sanktiot 0.0M))
    (is (= liikenneymp-akilliset-hoitotyot 100000M))
    (is (= liikenneymp-vahinkojen-korjaukset 1000M))
    (is (= liikenneymp-rahavaraukset 101000.0M))
    (is (= liikenneymp-yhteensa 102467.0M))

    ;; Soratien hoito
    (is (= soratien-hankinnat 4200.0M))
    (is (= soratien-lisatyot 0.0M))
    (is (= soratien-sanktiot 0.0M))
    (is (= soratien-akilliset-hoitotyot 0.0M))
    (is (= soratien-vahinkojen-korjaukset 0.0M))
    (is (= soratien-yhteensa 4200.0M))

    ;; Päällyste
    (is (= paallyste-hankinnat 0.0M))
    (is (= paallyste-lisatyot 0.0M))
    (is (= paallyste-sanktiot 0.0M))
    (is (= paallyste-yhteensa 0.0M))

    ;; Mhu ylläpito
    (is (= mhu-yllapito-hankinnat 12551.0M))
    (is (= mhu-yllapito-lisatyot 0.0M))
    (is (= mhu-yllapito-sanktiot 0.0M))
    (is (= mhu-yllapito-rahavaraus 0.0M))
    (is (= mhu-yllapito-tilaajan-rahavaraus 0.0M))
    (is (= mhu-yllapito-yhteensa 12551.0M))

    ;; Mhu hoidon johto
    (is (= mhu-johto-ja-hallintokorvaukset 1230.0M))
    (is (= mhu-erillishankinnat 0.0M))
    (is (= mhu-hj-palkkio 0.0M))
    (is (= mhu-bonukset 0.0M))
    (is (= mhu-sanktiot 0.0M))
    (is (= mhu-jjh-muutokset_laskutettu 1230.0M))
    (is (= mhu-jjh-muutokset_laskutetaan 1230.0M))
    (is (= mhu-hoitovuoden-paatos-tavoitepalkkio 0.0M))
    (is (= mhu-rahavaraus-a 0.0M))
    (is (= mhu-yhteensa 1230.0M))

    ;; Mhu korvausinvenstointi
    (is (= mhu-korvausinvestointi-hankinnat 0.0M))
    (is (= mhu-korvausinvestointi-lisatyot 0.0M))
    (is (= mhu-korvausinvestointi-sanktiot 0.0M))
    (is (= mhu-korvausinvestointi-rahavaraus-a 0.0M))
    (is (= mhu-korvausinvestointi-yhteensa 0.0M))))
