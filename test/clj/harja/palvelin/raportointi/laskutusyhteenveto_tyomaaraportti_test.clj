(ns harja.palvelin.raportointi.laskutusyhteenveto-tyomaaraportti-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [taoensso.timbre :as log]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [com.stuartsierra.component :as component]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.suunnittelu.apurit :as uusi-kust-apurit]
            [harja.palvelin.palvelut.valikatselmus.paatos-apurit :as paatos-apurit]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as uusi-kust-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]))

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


(defn pura-tyomaaraportti-mapiksi [raportti]
  (let [tulos
        {:talvihoito_hoitokausi_yht (:talvihoito_hoitokausi_yht raportti)
         :talvihoito_val_aika_yht (:talvihoito_val_aika_yht raportti)
         :lyh_hoitokausi_yht (:lyh_hoitokausi_yht raportti)
         :lyh_val_aika_yht (:lyh_val_aika_yht raportti)
         :sora_hoitokausi_yht (:sora_hoitokausi_yht raportti)
         :sora_val_aika_yht (:sora_val_aika_yht raportti)
         :paallyste_hoitokausi_yht (:paallyste_hoitokausi_yht raportti)
         :paallyste_val_aika_yht (:paallyste_val_aika_yht raportti)
         :yllapito_hoitokausi_yht (:yllapito_hoitokausi_yht raportti)
         :yllapito_val_aika_yht (:yllapito_val_aika_yht raportti)
         :korvausinv_hoitokausi_yht (:korvausinv_hoitokausi_yht raportti)
         :korvausinv_val_aika_yht (:korvausinv_val_aika_yht raportti)
         :hankinnat_hoitokausi_yht (:hankinnat_hoitokausi_yht raportti)
         :hankinnat_val_aika_yht (:hankinnat_val_aika_yht raportti)
         :johtojahallinto_hoitokausi_yht (:johtojahallinto_hoitokausi_yht raportti)
         :johtojahallinto_val_aika_yht (:johtojahallinto_val_aika_yht raportti)
         :erillishankinnat_hoitokausi_yht (:erillishankinnat_hoitokausi_yht raportti)
         :erillishankinnat_val_aika_yht (:erillishankinnat_val_aika_yht raportti)
         :hjpalkkio_hoitokausi_yht (:hjpalkkio_hoitokausi_yht raportti)
         :hjpalkkio_val_aika_yht (:hjpalkkio_val_aika_yht raportti)
         :hoidonjohto_hoitokausi_yht (:hoidonjohto_hoitokausi_yht raportti)
         :hoidonjohto_val_aika_yht (:hoidonjohto_val_aika_yht raportti)

         :muutostyo_hoitokausi_yht (:muutostyo_hoitokausi_yht raportti)
         :muutostyo_val_aika_yht (:muutostyo_val_aika_yht raportti)
         :muutos_erillis_hoitokausi_yht (:muutos_erillis_hoitokausi_yht raportti)
         :muutos_erillis_val_aika_yht (:muutos_erillis_val_aika_yht raportti)
         :jjh_muutos_hoitokausi_yht (:jjh_muutos_hoitokausi_yht raportti)
         :jjh_muutos_val_aika_yht (:jjh_muutos_val_aika_yht raportti)

         :hankinnat_ja_hoidon_hk_yht (:hankinnat_ja_hoidon_hk_yht raportti)
         :hankinnat_ja_hoidon_val_yht (:hankinnat_ja_hoidon_val_yht raportti)
         :tavhin_hoitokausi_yht (:tavhin_hoitokausi_yht raportti)
         :tavhin_val_aika_yht (:tavhin_val_aika_yht raportti)
         :hoitovuoden_alun_indkorj_tavoitehinta (:hoitovuoden_alun_indkorj_tavoitehinta raportti)
         :hoitokauden_tavoitehinta (:hoitokauden_tavoitehinta raportti)
         :tavoitehinta_on_oikaistu (:tavoitehinta_on_oikaistu raportti)
         :tavoitehinta_oikaisu_summa (:tavoitehinta_oikaisu_summa raportti)
         :hk_valikatselmus_siirrot_ed_vuodelta (:hk_valikatselmus_siirrot_ed_vuodelta raportti)
         :budjettia_jaljella (:budjettia_jaljella raportti)
         :lisatyo_talvihoito_hoitokausi_yht (:lisatyo_talvihoito_hoitokausi_yht raportti)
         :lisatyo_talvihoito_val_aika_yht (:lisatyo_talvihoito_val_aika_yht raportti)
         :lisatyo_lyh_hoitokausi_yht (:lisatyo_lyh_hoitokausi_yht raportti)
         :lisatyo_lyh_val_aika_yht (:lisatyo_lyh_val_aika_yht raportti)
         :lisatyo_sora_hoitokausi_yht (:lisatyo_sora_hoitokausi_yht raportti)
         :lisatyo_sora_val_aika_yht (:lisatyo_sora_val_aika_yht raportti)
         :lisatyo_paallyste_hoitokausi_yht (:lisatyo_paallyste_hoitokausi_yht raportti)
         :lisatyo_paallyste_val_aika_yht (:lisatyo_paallyste_val_aika_yht raportti)
         :lisatyo_yllapito_hoitokausi_yht (:lisatyo_yllapito_hoitokausi_yht raportti)
         :lisatyo_yllapito_val_aika_yht (:lisatyo_yllapito_val_aika_yht raportti)
         :lisatyo_korvausinv_hoitokausi_yht (:lisatyo_korvausinv_hoitokausi_yht raportti)
         :lisatyo_korvausinv_val_aika_yht (:lisatyo_korvausinv_val_aika_yht raportti)
         :lisatyo_hoidonjohto_hoitokausi_yht (:lisatyo_hoidonjohto_hoitokausi_yht raportti)
         :lisatyo_hoidonjohto_val_aika_yht (:lisatyo_hoidonjohto_val_aika_yht raportti)
         :lisatyot_hoitokausi_yht (:lisatyot_hoitokausi_yht raportti)
         :lisatyot_val_aika_yht (:lisatyot_val_aika_yht raportti)
         :bonukset_hoitokausi_yht (:bonukset_hoitokausi_yht raportti)
         :bonukset_val_aika_yht (:bonukset_val_aika_yht raportti)
         :sanktiot_hoitokausi_yht (:sanktiot_hoitokausi_yht raportti)
         :sanktiot_val_aika_yht (:sanktiot_val_aika_yht raportti)
         :paatos_tavoitepalkkio_hoitokausi_yht (:paatos_tavoitepalkkio_hoitokausi_yht raportti)
         :paatos_tavoitepalkkio_val_aika_yht (:paatos_tavoitepalkkio_val_aika_yht raportti)
         :paatos_tavoiteh_ylitys_hoitokausi_yht (:paatos_tavoiteh_ylitys_hoitokausi_yht raportti)
         :paatos_tavoiteh_ylitys_val_aika_yht (:paatos_tavoiteh_ylitys_val_aika_yht raportti)
         :paatos_kattoh_ylitys_hoitokausi_yht (:paatos_kattoh_ylitys_hoitokausi_yht raportti)
         :paatos_kattoh_ylitys_val_aika_yht (:paatos_kattoh_ylitys_val_aika_yht raportti)
         :paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht (:paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht raportti)
         :paatos_hoidonjohtopalkkion_muutos_val_aika_yht (:paatos_hoidonjohtopalkkion_muutos_val_aika_yht raportti)
         :muut_kustannukset_hoitokausi_yht (:muut_kustannukset_hoitokausi_yht raportti)
         :muut_kustannukset_val_aika_yht (:muut_kustannukset_val_aika_yht raportti)
         :yhteensa_kaikki_hoitokausi_yht (:yhteensa_kaikki_hoitokausi_yht raportti)
         :yhteensa_kaikki_val_aika_yht (:yhteensa_kaikki_val_aika_yht raportti)
         :perusluku (:perusluku raportti)
         :rahavaraus_nimet (:rahavaraus_nimet raportti)
         :hoitokausi_yht_array (:hoitokausi_yht_array raportti)
         :val_aika_yht_array (:val_aika_yht_array raportti)
         :kaikki_rahavaraukset_hoitokausi_yht (:kaikki_rahavaraukset_hoitokausi_yht raportti)
         :kaikki_rahavaraukset_val_yht (:kaikki_rahavaraukset_val_yht raportti)
         :muut_kulut_hoitokausi (:muut_kulut_hoitokausi raportti)
         :muut_kulut_val_aika (:muut_kulut_val_aika raportti)
         :muut_kulut_hoitokausi_yht (:muut_kulut_hoitokausi_yht raportti)
         :muut_kulut_val_aika_yht (:muut_kulut_val_aika_yht raportti)
         :muut_kulut_ei_tavoite_hoitokausi (:muut_kulut_ei_tavoite_hoitokausi raportti)
         :muut_kulut_ei_tavoite_val_aika (:muut_kulut_ei_tavoite_val_aika raportti)
         :muut_kulut_ei_tavoite_hoitokausi_yht (:muut_kulut_ei_tavoite_hoitokausi_yht raportti)
         :muut_kulut_ei_tavoite_val_aika_yht (:muut_kulut_ei_tavoite_val_aika_yht raportti)
         :pysyvat_muutokset_hoitokausi_yht (:pysyvat_muutokset_hoitokausi_yht raportti)
         :pysyvat_muutokset_val_aika_yht (:pysyvat_muutokset_val_aika_yht raportti)
         :pysyvat_muutokset_ed_hoitokausi (:pysyvat_muutokset_ed_hoitokausi raportti)
         ;; Laskutusraja
         :laskutusraja_yht (:laskutusraja_yht raportti)
         :laskutusrajaan_jaljella (:laskutusrajaan_jaljella raportti)
         :onko_laskutusraja_kaytossa (:onko_laskutusraja_kaytossa raportti)
         :onko_laskutusraja_ylittynyt (:onko_laskutusraja_ylittynyt raportti)
         :laskutusraja_laskutettavaa_yht (:laskutusraja_laskutettavaa_yht raportti)
         :laskutusraja_laskutettavaa_val_aika (:laskutusraja_laskutettavaa_val_aika raportti)
         :laskutusrajan_ylittynyt_yht (:laskutusrajan_ylittynyt_yht raportti)
         :laskutusrajan_ylittynyt_val_aika (:laskutusrajan_ylittynyt_val_aika raportti)
         :laskutettavaa_kaikki_yht (:laskutettavaa_kaikki_yht raportti)
         :laskutettavaa_kaikki_val_aika (:laskutettavaa_kaikki_val_aika raportti)}]
    tulos))


(deftest raportin-suoritus-urakalle-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vastaus (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                         hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))]
    (is (not (nil? vastaus)) "Saatiin raportti")
    (is (= (count (first vastaus)) 99) "Raportilla on oikea määrä rivejä")))


(deftest tyomaaraportti-talvihoito-hankinnat-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)

        ;; Poistetaan kaikki kulut urakalta
        _ (poista-kulut-aikavalilta urakka-id hk_alkupvm hk_loppupvm)

        ;; Luodaan talvihoitokulut
        erapaiva (pvm/->pvm "15.10.2019") ;#inst "2019-19-15T21:00:00.000-00:00"
        koontilaskun-kuukausi "lokakuu/1-hoitovuosi"
        toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23104")
        tehtavaryhma-id (hae-tehtavaryhman-id "A - Talvihoito")
        tehtava-id nil
        talvihoitosumma 1234M

        talvihoitokulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi talvihoitosumma
                         toimenpideinstanssi-id tehtavaryhma-id tehtava-id nil)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen talvihoitokulu})
        raportti (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                          hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))
        purettu (pura-tyomaaraportti-mapiksi (first raportti))]
    (is (= talvihoitosumma (:talvihoito_hoitokausi_yht purettu)))
    (is (= talvihoitosumma (:talvihoito_val_aika_yht purettu)))))


(deftest tyomaaraportti-liikenneymparistonhoito-hankinnat-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)

        ;; Poistetaan kaikki kulut urakalta
        _ (poista-kulut-aikavalilta urakka-id hk_alkupvm hk_loppupvm)

        ;; Luodaan liikenneympäristönhoitokulut
        erapaiva (pvm/->pvm "15.10.2019") ;#inst "2019-19-15T21:00:00.000-00:00"
        koontilaskun-kuukausi "lokakuu/1-hoitovuosi"
        toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23116")
        tehtavaryhma-id (hae-tehtavaryhman-id "N - Nurmetukset ja muut vihertyöt")
        tehtava-id nil
        lyhsumma 1234M

        lyhkulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi lyhsumma
                  toimenpideinstanssi-id tehtavaryhma-id tehtava-id nil)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen lyhkulu})


        raportti (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                          hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        purettu (pura-tyomaaraportti-mapiksi (first raportti))]
    (is (= lyhsumma (:lyh_hoitokausi_yht purettu)))
    (is (= lyhsumma (:lyh_val_aika_yht purettu)))))


(deftest tyomaaraportti-muut-tpit-hankinnat-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)

        ;; Poistetaan kaikki kulut urakalta
        _ (poista-kulut-aikavalilta urakka-id hk_alkupvm hk_loppupvm)

        ;; Luodaan kulut
        erapaiva (pvm/->pvm "15.10.2019") ;#inst "2019-19-15T21:00:00.000-00:00"
        koontilaskun-kuukausi "lokakuu/1-hoitovuosi"
        summa 1234M

        ;; Sora
        sora-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23124")
        sora-tehtavaryhma-id (hae-tehtavaryhman-id "C - Sorateiden hoito") ;; Sorateiden hoito, vaatii myös tehtävid:n kululle
        tehtava-id -1 ;; Valitaa "Muu tehtävä"
        sorakulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi summa
                   sora-toimenpideinstanssi-id sora-tehtavaryhma-id tehtava-id nil)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen sorakulu})

        ;; Päällyste
        paal-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "20107")
        paal-tehtavaryhma-id (hae-tehtavaryhman-id "Y1 - Kuumapäällyste")
        tehtava-id -1 ;; Valitaa "Muu tehtävä"
        paalkulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi summa
                   paal-toimenpideinstanssi-id paal-tehtavaryhma-id tehtava-id nil)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen paalkulu})

        ;; Ylläpito
        yl-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "20191")
        yl-tehtavaryhma-id (hae-tehtavaryhman-id "F - Muut, MHU ylläpito")
        tehtava-id nil
        ylkulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi summa
                 yl-toimenpideinstanssi-id yl-tehtavaryhma-id tehtava-id nil)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen ylkulu})

        ;; Korvausinvestointi
        korvaus-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "14301")
        korvaus-tehtavaryhma-id (hae-tehtavaryhman-id "Q - RKR-korjaus")
        tehtava-id (hae-tehtavan-id-nimella "Soratien runkokelirikkokorjaukset")
        korvauskulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi summa
                      korvaus-toimenpideinstanssi-id korvaus-tehtavaryhma-id tehtava-id nil)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen korvauskulu})

        raportti (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                          hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        purettu (pura-tyomaaraportti-mapiksi (first raportti))]
    (is (= summa (:sora_hoitokausi_yht purettu)))
    (is (= summa (:sora_val_aika_yht purettu)))

    (is (= summa (:paallyste_hoitokausi_yht purettu)))
    (is (= summa (:paallyste_val_aika_yht purettu)))

    (is (= summa (:yllapito_hoitokausi_yht purettu)))
    (is (= summa (:yllapito_val_aika_yht purettu)))

    (is (= summa (:korvausinv_hoitokausi_yht purettu)))
    (is (= summa (:korvausinv_val_aika_yht purettu)))

    ;; Hankinnat yhteensä
    (is (= (* 4 summa) (:hankinnat_hoitokausi_yht purettu)))
    (is (= (* 4 summa) (:hankinnat_val_aika_yht purettu)))))

(deftest tavoitehinta-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        hoitokauden-alkuvuosi 2018 ;; Päätös pitää olla edellisenä vuotena, jotta se näkyy siirroissa.
        kayttaja-id (:id +kayttaja-jvh+)
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        siirto-ed-vuodelta 60000.0M
        ;; Lisää siirretyt kulut Välikatselmuksesta "edelliseltä vuodelta"
        ;; Tehdään kattohinnan ylitys
        kattohinta 5M
        toteutuneet-kustannukset 5M
        ylityksen-maara 10M
        urakoitsija-maksaa 50M
        siirtorajoitus-prosentti (:kattohintaylityksen_siirron_prosenttirajoitus urakan-parametrit)
        maksimi-siirrettava-maara ylityksen-maara ;; koska rajoitus ei ole käytössä, niin voidaan siirtää koko ylitys
        viimeinen_hoitokausi false
        kulu-id 1
        paatos (paatos-apurit/kattohinnan-ylityspaatos urakka-id hoitokauden-alkuvuosi kattohinta toteutuneet-kustannukset
                 ylityksen-maara urakoitsija-maksaa siirto-ed-vuodelta kulu-id viimeinen_hoitokausi maksimi-siirrettava-maara siirtorajoitus-prosentti kayttaja-id)

        _ (paatos-kyselyt/tee-kattohinnan-ylityspaatos (:db jarjestelma) paatos)

        hoitokauden_tavoitehinta (ffirst (q (format "SELECT COALESCE(ut.tavoitehinta_indeksikorjattu, ut.tavoitehinta, 0) as tavoitehinta
                                    from urakka_tavoite ut
                                    where ut.hoitokausi = %s
                                    and ut.urakka = %s" 1 urakka-id)))
        raportti (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                          hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))
        purettu (pura-tyomaaraportti-mapiksi (first raportti))

        rahavaraukset-nimet (konversio/pgarray->vector (:rahavaraus_nimet purettu))
        rahavaraukset-val-aika (konversio/pgarray->vector (:val_aika_yht_array purettu))
        rahavaraukset-hoitokausi (konversio/pgarray->vector (:hoitokausi_yht_array purettu))

        ;; Pura rahavaraukset mukaan
        purettu-hoitokausi (reduce (fn [acc [nimi arvo]]
                                     (assoc acc (generoi-avaimet nimi "hk") arvo))
                             purettu
                             (map vector rahavaraukset-nimet rahavaraukset-hoitokausi))

        purettu (reduce (fn [acc [nimi arvo]]
                          (assoc acc (generoi-avaimet nimi "val") arvo))
                  purettu-hoitokausi
                  (map vector rahavaraukset-nimet rahavaraukset-val-aika))
        ;; Tavoitehintaan kuuluvat kustannukset yhteensä
        tavhin_hoitokausi_yht (+ (:talvihoito_hoitokausi_yht purettu) (:lyh_hoitokausi_yht purettu)
                                (:sora_hoitokausi_yht purettu) (:paallyste_hoitokausi_yht purettu)
                                (:yllapito_hoitokausi_yht purettu) (:korvausinv_hoitokausi_yht purettu)
                                (:johtojahallinto_hoitokausi_yht purettu) (:erillishankinnat_hoitokausi_yht purettu)
                                (:hjpalkkio_hoitokausi_yht purettu) (:kaikki_rahavaraukset_hoitokausi_yht purettu)
                                (:muut_kulut_hoitokausi_yht purettu))
        budjettia_jaljella (- (:hoitokauden_tavoitehinta purettu) (:tavhin_hoitokausi_yht purettu))]

    (is (= siirto-ed-vuodelta (:hk_valikatselmus_siirrot_ed_vuodelta purettu)))
    ;; Tarkastetaan, että siirto mukana muiden kulujen yhteissummassa
    (is (= (+ siirto-ed-vuodelta (:muut_kulut_hoitokausi purettu)) (:muut_kulut_hoitokausi_yht purettu)))
    (is (= tavhin_hoitokausi_yht (:tavhin_hoitokausi_yht purettu)))
    (is (= budjettia_jaljella (:budjettia_jaljella purettu)))
    (is (= hoitokauden_tavoitehinta (:hoitokauden_tavoitehinta purettu)))))


(deftest tyomaaraportti-bonukset-ja-sanktiot-nakyvyys-toimii
  (testing "Bonukset ja sanktiot näkyy MHU19 laskutusyhteenvedossa"
    (let [hk_alkupvm "2023-10-01"
          hk_loppupvm "2024-09-30"
          aikavali_alkupvm "2023-10-01"
          aikavali_loppupvm "2024-09-30"
          urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
          sopimus-id (hae-oulun-maanteiden-hoitourakan-2019-2024-sopimus-id)
          tpi-hallinnolliset-toimenpiteet (hae-toimenpideinstanssi-id urakka-id "23151") ;; Hallinnolliset toimenpiteet
          ;; Päivämäärä (käsittelypäivä) ja laskutuskuukausi, voi olla samat näissä testeissä, vaikka oikeasti ne voi vaihdella
          pvm (pvm/->pvm "15.10.2023")
          bonus_summa 1000M
          sanktio_summa 1500M
          alihankintabonus_summa 7777M
          bonukset-yht (+ alihankintabonus_summa bonus_summa)

          ;; Poistetaan kaikki bonukset ja sanktiot urakalta
          _ (poista-bonukset-ja-sanktiot-aikavalilta urakka-id hk_alkupvm hk_loppupvm)

          _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, laskutuskuukausi, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
                 sopimus-id tpi-hallinnolliset-toimenpiteet pvm pvm alihankintabonus_summa urakka-id "alihankintabonus"))
          ;; Luodaan asiakastyytyvaisyysbonus
          _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, laskutuskuukausi, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
                 sopimus-id tpi-hallinnolliset-toimenpiteet pvm pvm bonus_summa urakka-id "asiakastyytyvaisyysbonus"))

          ;; Luodaan sanktio
          _ (u (format "INSERT INTO sanktio (maara, perintapvm, toimenpideinstanssi, tyyppi, suorasanktio, sakkoryhma)
                      VALUES (%s,'%s'::DATE, %s, %s, %s, '%s'::sanktiolaji)"
                 sanktio_summa pvm tpi-hallinnolliset-toimenpiteet 2 true "A"))

          raportti (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                            hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))
          purettu (pura-tyomaaraportti-mapiksi (first raportti))]

      (is (= bonukset-yht (:bonukset_hoitokausi_yht purettu)) "Kirjattu bonus MHU19 täsmää tulosta (hk yht)")
      (is (= bonukset-yht (:bonukset_val_aika_yht purettu)) "Kirjattu bonus MHU19 täsmää tulosta (val aika)")
      (is (= (* -1 sanktio_summa) (:sanktiot_hoitokausi_yht purettu)) "Kirjattu sanktio MHU19 täsmää tulosta (hk yht)")
      (is (= (* -1 sanktio_summa) (:sanktiot_val_aika_yht purettu)) "Kirjattu sanktio MHU19 täsmää tulosta (val aika)")

      ;; Nämä lasketaan kustannuksiin sekä näytetään laskutusyhteenvedolla
      (is (=
            (- bonukset-yht sanktio_summa)
            (:muut_kustannukset_hoitokausi_yht purettu)) "Sanktiot & bonukset näytetään MHU19 raportilla (val aika)")
      (is (=
            (- bonukset-yht sanktio_summa)
            (:muut_kulut_ei_tavoite_hoitokausi_yht purettu)) "Sanktiot & bonukset näytetään MHU19 raportilla (hk yht)")))

  (testing "Bonuksia & sanktioita ei näytetä MHU25 laskutuksessa"
    (let [hk_alkupvm "2025-10-01"
          hk_loppupvm "2026-09-30"
          aikavali_alkupvm "2025-10-01"
          aikavali_loppupvm "2026-09-30"
          urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
          sopimus-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-sopimus-id)
          tpi-hallinnolliset-toimenpiteet (hae-toimenpideinstanssi-id urakka-id "23151")
          pvm (pvm/->pvm "15.10.2025")
          bonus_summa 1000M
          sanktio_summa 1500M
          alihankintabonus_summa 7777M

          ;; Poistetaan kaikki bonukset ja sanktiot urakalta
          _ (poista-bonukset-ja-sanktiot-aikavalilta urakka-id hk_alkupvm hk_loppupvm)

          _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, laskutuskuukausi, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
                 sopimus-id tpi-hallinnolliset-toimenpiteet pvm pvm alihankintabonus_summa urakka-id "alihankintabonus"))
          ;; Luodaan asiakastyytyvaisyysbonus
          _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, laskutuskuukausi, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
                 sopimus-id tpi-hallinnolliset-toimenpiteet pvm pvm bonus_summa urakka-id "asiakastyytyvaisyysbonus"))

          ;; Luodaan sanktio
          _ (u (format "INSERT INTO sanktio (maara, perintapvm, toimenpideinstanssi, tyyppi, suorasanktio, sakkoryhma)
                      VALUES (%s,'%s'::DATE, %s, %s, %s, '%s'::sanktiolaji)"
                 sanktio_summa pvm tpi-hallinnolliset-toimenpiteet 2 true "A"))

          raportti (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                            hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

          purettu (pura-tyomaaraportti-mapiksi (first raportti))]

      ;; Arvot tallennetaan
      (is (= (+ alihankintabonus_summa bonus_summa) (:bonukset_hoitokausi_yht purettu)) "Kirjattu bonus MHU25 täsmää tulosta (hk yht)")
      (is (= (+ alihankintabonus_summa bonus_summa) (:bonukset_val_aika_yht purettu)) "Kirjattu bonus MHU25 täsmää tulosta (val aika)")
      (is (= (* -1 sanktio_summa) (:sanktiot_hoitokausi_yht purettu)) "Kirjattu sanktio MHU25 täsmää tulosta (hk yht)")
      (is (= (* -1 sanktio_summa) (:sanktiot_val_aika_yht purettu)) "Kirjattu sanktio MHU25 täsmää tulosta (val aika)")

      ;; Mutta niitä ei lasketa toteutuneisiin, eivätkä näy raportilla 
      (is (=
            0.0M
            (:muut_kustannukset_hoitokausi_yht purettu)) "Sanktioita & bonuksia ei näytetä MHU25 raportilla (val aika)")
      (is (=
            0.0M
            (:muut_kulut_ei_tavoite_hoitokausi_yht purettu)) "Sanktioita & bonuksia ei näytetä MHU25 raportilla (hk yht)"))))


(deftest tyomaaraportti-bonukset-ja-sanktiot-toimii-jalkeen-2022
  (let [hk_alkupvm "2022-10-01"
        hk_loppupvm "2023-09-30"
        aikavali_alkupvm "2022-10-01"
        aikavali_loppupvm "2023-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        sopimus-id (hae-oulun-maanteiden-hoitourakan-2019-2024-sopimus-id)
        tpi (hae-toimenpideinstanssi-id urakka-id "23151") ;; Hallinnolliset toimenpiteet
        pvm (pvm/->pvm "15.10.2022")
        bonus_summa 1000M
        ;; Poistetaan kaikki bonukset ja sanktiot urakalta
        _ (poista-bonukset-ja-sanktiot-aikavalilta urakka-id hk_alkupvm hk_loppupvm)

        ;; Luodaan alihankintabonus
        _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, laskutuskuukausi, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
               sopimus-id tpi pvm pvm bonus_summa urakka-id "alihankintabonus"))
        ;; Luodaan asiakastyytyvaisyysbonus
        _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, laskutuskuukausi, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
               sopimus-id tpi pvm pvm bonus_summa urakka-id "asiakastyytyvaisyysbonus"))

        raportti (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                          hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        purettu (pura-tyomaaraportti-mapiksi (first raportti))]

    (is (= (* 2 bonus_summa) (:bonukset_hoitokausi_yht purettu)))
    (is (= (* 2 bonus_summa) (:bonukset_val_aika_yht purettu)))))


(deftest tyomaaraportti-laskutusraja-2025-mhu+toimii
  (let [hk_alkupvm "2025-10-01"
        hk_loppupvm "2026-09-30"
        aikavali_alkupvm "2025-10-01"
        aikavali_loppupvm "2026-09-30"
        urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)

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

        talvihoitokulu (luo-kulu
                         urakka-id "laskutettava" erapaiva "hankintakulu"
                         koontilaskun-kuukausi talvihoitosumma toimenpideinstanssi-id tehtavaryhma-id tehtava-id nil)

        _ (kutsu-http-palvelua :tallenna-kulu +kayttaja-jvh+
            {:urakka-id urakka-id
             :kulu-kohdistuksineen talvihoitokulu})

        raportti (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                          hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        ;; ----------------------------------------------------------------
        ;; Laskutusrajan arvot pitäisi olla saatavilla sekä näyttää oikealta
        purettu (pura-tyomaaraportti-mapiksi (first raportti))

        laskutusraja (:laskutusraja_yht purettu)
        jaljella (:laskutusrajaan_jaljella purettu)
        kaytossa (:onko_laskutusraja_kaytossa purettu)
        ylittynyt (:onko_laskutusraja_ylittynyt purettu)
        laskutettavaa (:laskutusraja_laskutettavaa_yht purettu)]

    (is (false? ylittynyt) "Laskutusrajan ei pitäisi olla ylittynyt")
    (is (true? kaytossa) "Lasktutusrajan pitäisi olla käytössä MHU+ urakalla")
    (is (= jaljella (- laskutusraja talvihoitosumma)) "Laskutusraja pitäisi alentua kulun perusteella")
    (is (= laskutettavaa talvihoitosumma) "Laskutettavaa pitäisi olla kirjatun kulun verran")

    (is (= talvihoitosumma (:talvihoito_hoitokausi_yht purettu)))
    (is (= talvihoitosumma (:talvihoito_val_aika_yht purettu)))))


(deftest tyomaaraportti-mhu2021-ei-nayta-laskutusrajaa
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

        raportti (q-map (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                          hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        ;; ----------------------------------------------------------------
        ;; Laskutusrajan arvot pitäisi näyttää oikealta
        purettu (pura-tyomaaraportti-mapiksi (first raportti))
        kaytossa (:onko_laskutusraja_kaytossa purettu)
        ylittynyt (:onko_laskutusraja_ylittynyt purettu)]

    (is (false? (boolean ylittynyt)) "Laskutusrajan ei pitäisi olla ylittynyt")
    (is (false? kaytossa) "Lasktutusrajan ei pitäisi olla käytössä MHU 21- urakalla")

    ;; Laskutusrajan lukuja ei pitäisi tällä urakalla näkyä
    (is (= (:laskutettavaa_kaikki_yht purettu) 0.0M))
    (is (= (:laskutusrajan_ylittynyt_yht purettu) 0.0M))
    (is (= (:laskutettavaa_kaikki_val_aika purettu) 0.0M))
    (is (= (:laskutusraja_laskutettavaa_yht purettu) 0.0M))
    (is (= (:laskutusraja_laskutettavaa_val_aika purettu) 0.0M))

    ;; Kirjattu talvihoito pitäisi näkyä 
    (is (= talvihoitosumma (:talvihoito_hoitokausi_yht purettu)))
    (is (= talvihoitosumma (:talvihoito_val_aika_yht purettu)))))
