(ns harja.palvelin.raportointi.laskutusyhteenveto_tuotekohtainen-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.kyselyt.budjettisuunnittelu :as budjetti-q]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.suunnittelu.apurit :as uusi-kust-apurit]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as uusi-kust-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-tuotekohtainen :as tuotekohtainen]))

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
                      [:http-palvelin :db :raportointi :pdf-vienti])
          :kulut (component/using
                   (kulut/->Kulut)
                   [:http-palvelin :db])
          :uusi-kustannussuunnitelma (component/using
                                       (kust-palvelu/->UusiKustannussuunnitelmaPalvelu)
                                       [:http-palvelin :db])
          :tarjous (component/using
                     (tarjous-palvelu/->Tarjous)
                     [:http-palvelin :db])))))

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

(defn pura-tuotekohtainen-raportti-mapiksi [raportti]
  (select-keys
    raportti
    [:bonukset_laskutetaan
     :maksuera_numero
     :jjh_muutokset_laskutettu
     :laskutettavaa_kaikki_yht
     :onko_laskutusraja_kaytossa
     :kaikki_laskutetaan
     :kaikki_rahavaraukset_hoitokausi_yht
     :kaikki_laskutettu
     :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutetaan
     :laskutusrajan_ylittynyt_yht
     :hj_palkkio_laskutettu
     :lisatyot_laskutettu
     :laskutusraja_laskutettavaa_val_aika
     :hoitokausi_yht_array
     :bonukset_laskutettu
     :sakot_laskutetaan
     :hj_erillishankinnat_laskutetaan
     :laskutusrajaan_jaljella
     :kaikki_rahavaraukset_val_yht
     :laskutusraja_laskutettavaa_yht
     :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutetaan
     :hj_paattaminen_hoidonjohtopalkkion_muutos_laskutettu
     :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutettu
     :johto_ja_hallinto_laskutetaan
     :hankinnat_laskutettu
     :nimi
     :hj_paattaminen_hoidonjohtopalkkion_muutos_laskutetaan
     :alihank_bon_laskutetaan
     :hj_hoitovuoden_paattaminen_tavoitehinnan_ylitys_laskutetaan
     :lisatyot_laskutetaan
     :perusluku
     :hj_hoitovuoden_paattaminen_tavoitepalkkio_laskutettu
     :hankinnat_laskutetaan
     :indeksi_puuttuu
     :jjh_muutokset_laskutetaan
     :tavoitehintaiset_laskutettu
     :laskutettavaa_kaikki_val_aika
     :hj_hoitovuoden_paattaminen_kattohinnan_ylitys_laskutettu
     :hj_erillishankinnat_laskutettu
     :tuotekoodi
     :johto_ja_hallinto_laskutettu
     :hj_palkkio_laskutetaan
     :laskutusraja_yht
     :val_aika_yht_array
     :alihank_bon_laskutettu
     :sakot_laskutettu
     :onko_laskutusraja_ylittynyt
     :tavoitehintaiset_laskutetaan
     :laskutusrajan_ylittynyt_val_aika
     :tpi
     :rahavaraus_nimet]))


(defn parsi-tuotekohtainen-laskutus-vastaus [vastaus]
  (map (fn [rivi]
         (let [purettu (pura-tuotekohtainen-raportti-mapiksi rivi)
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
        _ (is (= 6230M (:kirjallisesti-sovitut-muutokset urakka-tavoite)))

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


(deftest tuotekohtainen-laskutusraja-2025-mhu+toimii
  (let [hk_alkupvm "2025-10-01"
        hk_loppupvm "2026-09-30"
        aikavali_alkupvm "2025-10-01"
        aikavali_loppupvm "2026-09-30"
        urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        hae-yhteenveto (fn []
             (-> (q-map (format "select * from mhu_laskutusyhteenveto_tuotekohtainen('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                   hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))
                 (tuotekohtainen/koosta-yhteenveto 0.0M)))

        ;; ----------------------------------------------------------------
        ;; Vahvista kustannussuunnitelma jotta saadaan laskutusraja arvot
        vahvistetut-vuodet #{}
        hoitovuoden-alkuvuosi 2025
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (uusi-kust-apurit/poista-tarjoukset-tietokannasta! urakka-id)
        h-tietomalli (uusi-kust-apurit/poista-yhteenvetorivi-toimenpiteilta uusi-kust-apurit/hankinnat-tietomalli)
        toimenpiteet (uusi-kust-kyselyt/hae-urakan-toimenpiteet (:db jarjestelma) {:urakkaid urakka-id})
        h-tietomalli (uusi-kust-apurit/paivita-hankintojen-toimenpideinstanssi-id h-tietomalli toimenpiteet)

        erillishankinnat-yht (apply +
                               (map :summa (:erillishankinnat uusi-kust-apurit/erillishankinnat-tietomalli)))
        hoidonjohto-yht (apply +
                          (map :summa (:hoidonjohtopalkkiot uusi-kust-apurit/hoidonjohtopalkkiot-tietomalli)))
        jjh-yht (apply +
                  (map :summa (:johto-ja-hallintokorvaukset-2025 uusi-kust-apurit/johto-ja-hallinto-tietomalli-2025)))

        ;; Kirjaa kaikki kustiksen osiot 
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat
            (:db jarjestelma) +kayttaja-jvh+ urakka-id
            hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))

        _ (uusi-kust-kyselyt/tallenna-erillishankinnat
            (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat uusi-kust-apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)

        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot
            (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot uusi-kust-apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)

        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset
            (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2025 uusi-kust-apurit/johto-ja-hallinto-tietomalli-2025) hoitovuoden-alkuvuosi)

        tarjous (uusi-kust-apurit/generoi-tarjous-tasmaa-kustannuksia
                  urakka-id
                  erillishankinnat-yht
                  hoidonjohto-yht
                  jjh-yht)

        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan (:db jarjestelma) urakka-id (:id +kayttaja-jvh+) tarjous vahvistetut-vuodet)

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+
                  {:urakka-id urakka-id
                   :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                   :vahvista? true})

        virhe (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])
        _ (is (some? vastaus) "Vastaus pitäisi olla olemassa")
        _ (is (empty? virhe) "Virhettä ei pitäisi olla vastauksessa")
        _ (is (= (set virhe) #{}) "Virhettä ei pitäisi olla vastauksessa")


        ;; ----------------------------------------------------------------
        ;; Kustis on vahvistettu, kirjaa talvihoitokulu
        _ (poista-kulut-aikavalilta urakka-id hk_alkupvm hk_loppupvm)

        ;; Luodaan talvihoitokulut
        erapaiva (pvm/->pvm "15.10.2025")
        koontilaskun-kuukausi "lokakuu/1-hoitovuosi"
        toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23104")
        tehtavaryhma-id (hae-tehtavaryhman-id "A - Talvihoito")
        tehtava-id nil
        talvihoitosumma 1234M
        tiedot-ennen (hae-yhteenveto)

        talvihoitokulu (luo-kulu
                         urakka-id "laskutettava" erapaiva "hankintakulu"
                         koontilaskun-kuukausi talvihoitosumma toimenpideinstanssi-id tehtavaryhma-id tehtava-id nil)

        _ (kutsu-http-palvelua :tallenna-kulu +kayttaja-jvh+
            {:urakka-id urakka-id
             :kulu-kohdistuksineen talvihoitokulu})

        ;; ----------------------------------------------------------------
        ;; Laskutusrajan arvot pitäisi olla saatavilla sekä näyttää oikealta
        tiedot (hae-yhteenveto)

        {:keys [onko_laskutusraja_kaytossa
                _laskutusrajan_ylittynyt_yht
                _hk_valikatselmus_siirrot_ed_vuodelta
                _laskutusraja_laskutettavaa_val_aika
                laskutusrajaan_jaljella
                laskutusraja_laskutettavaa_yht
                _nimi
                _kaikki-yhteensa-laskutetaan
                _kaikki-tavoitehintaiset-laskutetaan
                _kaikki-tavoitehintaiset-laskutettu
                _kaikki-yhteensa-laskutettu
                onko_laskutusraja_ylittynyt
                _laskutusrajan_ylittynyt_val_aika]} tiedot]

    (is (false? onko_laskutusraja_ylittynyt) "Laskutusrajan ei pitäisi olla ylittynyt")
    (is (true? onko_laskutusraja_kaytossa) "Lasktutusrajan pitäisi olla käytössä MHU25 urakalla")
          (is (= (- (:laskutusrajaan_jaljella tiedot-ennen) talvihoitosumma)
               laskutusrajaan_jaljella)
            "Laskutusrajan pitäisi alentua kulun perusteella")
          (is (= (+ (:laskutusraja_laskutettavaa_yht tiedot-ennen) talvihoitosumma)
               laskutusraja_laskutettavaa_yht)
            "Laskutettavan summan pitäisi muuttua kirjatun kulun verran")))


(deftest tuotekohtainen-mhu2021-ei-nayta-laskutusrajaa
  (let [hk_alkupvm "2021-10-01"
        hk_loppupvm "2022-09-30"
        aikavali_alkupvm "2021-10-01"
        aikavali_loppupvm "2022-09-30"

        hoitovuoden-alkuvuosi 2021
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (hae-urakan-id-nimella "Iin MHU 2021-2026")

        _ (u (format "DELETE FROM kiinteahintainen_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               sopimus-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM kustannusarvioitu_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               sopimus-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM johto_ja_hallintokorvaus WHERE \"urakka-id\" = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               urakka-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))

        ;; ----------------------------------------------------------------
        ;; Vahvista kustannussuunnitelma jotta saadaan laskutusraja arvot
        ;; Lisätään ensin kilpailutettavat hankinnat
        h-tietomalli (uusi-kust-apurit/poista-yhteenvetorivi-toimenpiteilta uusi-kust-apurit/hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat uusi-kust-apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot uusi-kust-apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2019 uusi-kust-apurit/johto-ja-hallinto-tietomalli-2019) hoitovuoden-alkuvuosi)

        ;; Varmista, että kustannussuunnitelmaa ei ole vielä vahvistettu
        kustannussuunnitelma (kutsu-palvelua (:http-palvelin jarjestelma) :hae-kustannussuunnitelman-tiedot
                               +kayttaja-jvh+
                               {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})

        _ (is (false? (get-in kustannussuunnitelma [:kustannussuunnitelma :vahvistettu?]))
            "Kustannussuunnitelman pitäisi olla vahvistamaton ennen vahvistusta")

        ;; Rahavaraukset vaativat tarjouksen täyttämisen.
        kayttaja-id (:id +kayttaja-jvh+)

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista uusi-kust-apurit/tarjous-tietomalli-2019)
        tarjous (uusi-kust-apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
        vahvistetut-vuodet #{}
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan (:db jarjestelma) urakka-id kayttaja-id tarjous vahvistetut-vuodet)

        ;; Vahvistetaan tavoite ja kattohinta
        tiedot {:urakka-id urakka-id
                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                :vahvista? true}

        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))

        _ (is (nil? (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])) "Vahvistuksessa ei pitäisi olla virhettä")
        _ (is (not (nil? (get-in vastaus [:tarjous]))) "Vastauksessa pitäisi olla tarjous")


        ;; ----------------------------------------------------------------
        ;; Kirjaa talvihoitokulu
        _ (poista-kulut-aikavalilta urakka-id hk_alkupvm hk_loppupvm)
        erapaiva (pvm/->pvm "15.10.2021")
        koontilaskun-kuukausi "lokakuu/1-hoitovuosi"
        toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23104")
        tehtavaryhma-id (hae-tehtavaryhman-id "A - Talvihoito")
        tehtava-id nil
        talvihoitosumma 1234M

        talvihoitokulu (luo-kulu
                         urakka-id "laskutettava" erapaiva "hankintakulu"
                         koontilaskun-kuukausi talvihoitosumma toimenpideinstanssi-id tehtavaryhma-id tehtava-id nil)

        _ (kutsu-http-palvelua :tallenna-kulu +kayttaja-jvh+
            {:urakka-id urakka-id
             :kulu-kohdistuksineen talvihoitokulu})

        tiedot (q-map (format "select * from mhu_laskutusyhteenveto_tuotekohtainen('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                        hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))


        ;; ----------------------------------------------------------------
        ;; Laskutusrajan arvot pitäisi näyttää oikealta
        ;; ----------------------------------------------------------------
        ;; Laskutusrajan arvot pitäisi olla saatavilla sekä näyttää oikealta
        purettu (pura-tuotekohtainen-raportti-mapiksi (first tiedot))
        tiedot (tuotekohtainen/koosta-yhteenveto tiedot 0.0M)

        {:keys [onko_laskutusraja_kaytossa
                laskutusrajan_ylittynyt_yht
                _hk_valikatselmus_siirrot_ed_vuodelta
                laskutusraja_laskutettavaa_val_aika
                _laskutusrajaan_jaljella
                laskutusraja_laskutettavaa_yht
                _nimi
                _kaikki-yhteensa-laskutetaan
                _kaikki-tavoitehintaiset-laskutetaan
                _kaikki-tavoitehintaiset-laskutettu
                _kaikki-yhteensa-laskutettu
                _laskutusraja_yht
                onko_laskutusraja_ylittynyt
                _laskutusrajan_ylittynyt_val_aika]} tiedot]


    (is (false? onko_laskutusraja_ylittynyt) "Laskutusrajan ei pitäisi olla ylittynyt")
    (is (false? onko_laskutusraja_kaytossa) "Lasktutusrajan ei pitäisi olla käytössä MHU 21- urakalla")

    ;; Laskutusrajan lukuja ei pitäisi tällä urakalla näkyä
    (is (= laskutusraja_laskutettavaa_yht 0.0M) "Laskutettavaa ei ole")
    (is (= laskutusrajan_ylittynyt_yht 0.0M) "Laskutusraja ei ole ylittynyt")
    (is (= laskutusraja_laskutettavaa_val_aika 0.0M) "Laskutettavaa (valittu aika) ei ole")

    ;; Kirjattu talvihoito pitäisi näkyä 
    (is (= talvihoitosumma (:hankinnat_laskutettu purettu)) "Kirjattu kulu näkyy")
    (is (= talvihoitosumma (:hankinnat_laskutetaan purettu)) "Kirjattu kulu näkyy")

    ;; Testidatan arvoja, ei syötetty tässä
    (is (= (:sakot_laskutetaan purettu) -1000.0M) "Sanktiot näkyy mhu21 urakalla")

    ;; Toteutuneet kustannukset yhteensä 
    ;; Koska sakkoja on tuhat, pitäisi olla 1000 - 1234 (me syötettiin 1234e)
    (is (= (:kaikki_laskutettu purettu) 234.0M) "Kaikki pitäisi olla sanktion & bonusten verran")
    (is (= (:kaikki_laskutetaan purettu) 234.0M))) "Kaikki pitäisi olla sanktion & bonusten verran")
