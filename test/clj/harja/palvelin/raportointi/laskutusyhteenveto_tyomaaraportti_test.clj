(ns harja.palvelin.raportointi.laskutusyhteenveto_tyomaaraportti_test
  (:require [clojure.test :refer :all]
            [clojure.string :as str]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.testi :refer :all]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.kulut :as kulut]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]))

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
                   [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))


(defn pura-tyomaaraportti-mapiksi [raportti]
  (let [tulos
        {:talvihoito_hoitokausi_yht (nth raportti 0)
         :talvihoito_val_aika_yht (nth raportti 1)
         :lyh_hoitokausi_yht (nth raportti 2)
         :lyh_val_aika_yht (nth raportti 3)
         :sora_hoitokausi_yht (nth raportti 4)
         :sora_val_aika_yht (nth raportti 5)
         :paallyste_hoitokausi_yht (nth raportti 6)
         :paallyste_val_aika_yht (nth raportti 7)
         :yllapito_hoitokausi_yht (nth raportti 8)
         :yllapito_val_aika_yht (nth raportti 9)
         :korvausinv_hoitokausi_yht (nth raportti 10)
         :korvausinv_val_aika_yht (nth raportti 11)
         :hankinnat_hoitokausi_yht (nth raportti 12)
         :hankinnat_val_aika_yht (nth raportti 13)
         :johtojahallinto_hoitokausi_yht (nth raportti 14)
         :johtojahallinto_val_aika_yht (nth raportti 15)
         :erillishankinnat_hoitokausi_yht (nth raportti 16)
         :erillishankinnat_val_aika_yht (nth raportti 17)
         :hjpalkkio_hoitokausi_yht (nth raportti 18)
         :hjpalkkio_val_aika_yht (nth raportti 19)
         :hoidonjohto_hoitokausi_yht (nth raportti 20)
         :hoidonjohto_val_aika_yht (nth raportti 21)
         :akilliset_hoitokausi_yht (nth raportti 22)
         :akilliset_val_aika_yht (nth raportti 23)
         :vahingot_hoitokausi_yht (nth raportti 24)
         :vahingot_val_aika_yht (nth raportti 25)
         :tavhin_hoitokausi_yht (nth raportti 26)
         :tavhin_val_aika_yht (nth raportti 27)
         :hoitokauden_tavoitehinta (nth raportti 28)
         :hk_tavhintsiirto_ed_vuodelta (nth raportti 29)
         :budjettia_jaljella (nth raportti 30)
         :lisatyo_talvihoito_hoitokausi_yht (nth raportti 31)
         :lisatyo_talvihoito_val_aika_yht (nth raportti 32)
         :lisatyo_lyh_hoitokausi_yht (nth raportti 33)
         :lisatyo_lyh_val_aika_yht (nth raportti 34)
         :lisatyo_sora_hoitokausi_yht (nth raportti 35)
         :lisatyo_sora_val_aika_yht (nth raportti 36)
         :lisatyo_paallyste_hoitokausi_yht (nth raportti 37)
         :lisatyo_paallyste_val_aika_yht (nth raportti 38)
         :lisatyo_yllapito_hoitokausi_yht (nth raportti 39)
         :lisatyo_yllapito_val_aika_yht (nth raportti 40)
         :lisatyo_korvausinv_hoitokausi_yht (nth raportti 41)
         :lisatyo_korvausinv_val_aika_yht (nth raportti 42)
         :lisatyo_hoindonjohto_hoitokausi_yht (nth raportti 43)
         :lisatyo_hoidonjohto_val_aika_yht (nth raportti 44)
         :lisatyot_hoitokausi_yht (nth raportti 45)
         :lisatyot_val_aika_yht (nth raportti 46)
         :bonukset_hoitokausi_yht (nth raportti 47)
         :bonukset_val_aika_yht (nth raportti 48)
         :sanktiot_hoitokausi_yht (nth raportti 49)
         :sanktiot_val_aika_yht (nth raportti 50)
         :paatos_tavoitepalkkio_hoitokausi_yht (nth raportti 51)
         :paatos_tavoitepalkkio_val_aika_yht (nth raportti 52)
         :paatos_tavoiteh_ylitys_hoitokausi_yht (nth raportti 53)
         :paatos_tavoiteh_ylitys_val_aika_yht (nth raportti 54)
         :paatos_kattoh_ylitys_hoitokausi_yht (nth raportti 55)
         :paatos_kattoh_ylitys_val_aika_yht (nth raportti 56)
         :muut_kustannukset_hoitokausi_yht (nth raportti 57)
         :muut_kustannukset_val_aika_yht (nth raportti 58)
         :yhteensa_kaikki_hoitokausi_yht (nth raportti 59)
         :yhteensa_kaikki_val_aika_yht (nth raportti 60)}]
    tulos))

(defn luo-kulu [urakka-id tyyppi erapaiva suoritushetki koontilaskun-kuukausi summa toimenpideinstanssi-id tehtavaryhma-id]
  {:id nil
   :urakka urakka-id
   :viite "123456781"
   :erapaiva erapaiva
   :kokonaissumma summa
   :tyyppi tyyppi
   :kohdistukset [{:kohdistus-id nil
                   :rivi 1
                   :summa summa
                   :suoritus-alku suoritushetki
                   :suoritus-loppu suoritushetki
                   :toimenpideinstanssi toimenpideinstanssi-id
                   :tehtavaryhma tehtavaryhma-id
                   :tehtava nil}]
   :koontilaskun-kuukausi koontilaskun-kuukausi})

(defn- poista-kulut-aikavalilta [urakka-id hk_alkupvm hk_loppupvm]
  (let [kulut (flatten (q (format "SELECT id FROM kulu k WHERE k.urakka = %s and k.erapaiva BETWEEN '%s'::DATE AND '%s'::DATE;" urakka-id hk_alkupvm hk_loppupvm)))
        _ (u (format "DELETE FROM kulu_kohdistus WHERE kulu IN (%s)" (str/join "," kulut)))
        _ (u (format "DELETE FROM kulu_liite WHERE kulu IN (%s)" (str/join "," kulut)))
        _ (u (format "delete from kulu k where k.urakka = %s and k.erapaiva BETWEEN '%s'::DATE AND '%s'::DATE; " urakka-id hk_alkupvm hk_loppupvm))]))

(defn- poista-bonukset-ja-sanktiot-aikavalilta [urakka-id hk_alkupvm hk_loppupvm]
  (let [toimenpideinstanssit (flatten (q (format "SELECT tpi.id as is
                                                    FROM toimenpideinstanssi tpi
                                                   WHERE tpi.urakka = %s;" urakka-id)))
        _ (u (format "DELETE FROM erilliskustannus WHERE urakka = %s AND pvm BETWEEN '%s'::DATE AND '%s'::DATE;" urakka-id hk_alkupvm hk_loppupvm))
        ;; Sanktioihin ei ole tallennettu urakkaa, niin se pitää niputtaa toimenpideinstanssien kautta
        _ (u (format "DELETE FROM sanktio WHERE toimenpideinstanssi IN (%s) AND perintapvm BETWEEN '%s'::DATE AND '%s'::DATE;" (str/join "," toimenpideinstanssit) hk_alkupvm hk_loppupvm))]))

(deftest raportin-suoritus-urakalle-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vastaus (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                     hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))]
    (is (not (nil? vastaus)) "Saatiin raportti")
    (is (= (count (first vastaus)) 62) "Raportilla on oikea määrä rivejä")))

;; Oulun MHU:n toimenpideinstanssit ja toimenpidekoodi taulun koodit
;Oulu MHU Talvihoito TP,23104
;Oulu MHU Liikenneympäristön hoito TP,23116
;Oulu MHU Soratien hoito TP,23124
;Oulu MHU Hallinnolliset toimenpiteet TP,23151
;Oulu MHU Päällystepaikkaukset TP,20107
;Oulu MHU MHU Ylläpito TP,20191
;Oulu MHU MHU Korvausinvestointi TP,14301


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
        suoritushetki (pvm/->pvm "15.10.2019")
        toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23104")
        tehtavaryhma-id (hae-tehtavaryhman-id "Talvihoito (A)")
        talvihoitosumma 1234M

        talvihoitokulu (luo-kulu urakka-id "laskutettava" erapaiva suoritushetki koontilaskun-kuukausi talvihoitosumma toimenpideinstanssi-id tehtavaryhma-id)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen talvihoitokulu})
        raportti (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
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
        suoritushetki (pvm/->pvm "15.10.2019")
        toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23116")
        tehtavaryhma-id (hae-tehtavaryhman-id "Liikennemerkit ja liikenteenohjauslaitteet (L)")
        lyhsumma 1234M

        lyhkulu (luo-kulu urakka-id "laskutettava" erapaiva suoritushetki koontilaskun-kuukausi lyhsumma toimenpideinstanssi-id tehtavaryhma-id)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen lyhkulu})


        raportti (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
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
        suoritushetki (pvm/->pvm "15.10.2019")
        summa 1234M

        ;; Sora
        sora-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23124")
        sora-tehtavaryhma-id (hae-tehtavaryhman-id "Sorateiden hoito (C)")
        sorakulu (luo-kulu urakka-id "laskutettava" erapaiva suoritushetki koontilaskun-kuukausi summa sora-toimenpideinstanssi-id sora-tehtavaryhma-id)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen sorakulu})

        ;; Päällyste
        paal-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "20107")
        paal-tehtavaryhma-id (hae-tehtavaryhman-id "Kuumapäällyste (Y1)")
        paalkulu (luo-kulu urakka-id "laskutettava" erapaiva suoritushetki koontilaskun-kuukausi summa paal-toimenpideinstanssi-id paal-tehtavaryhma-id)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen paalkulu})

        ;; Ylläpito
        yl-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "20191")
        yl-tehtavaryhma-id (hae-tehtavaryhman-id "Muut, MHU ylläpito (F)")
        ylkulu (luo-kulu urakka-id "laskutettava" erapaiva suoritushetki koontilaskun-kuukausi summa yl-toimenpideinstanssi-id yl-tehtavaryhma-id)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen ylkulu})

        ;; Korvausinvestointi
        korvaus-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "14301")
        korvaus-tehtavaryhma-id (hae-tehtavaryhman-id "RKR-korjaus (Q)")
        korvauskulu (luo-kulu urakka-id "laskutettava" erapaiva suoritushetki koontilaskun-kuukausi summa korvaus-toimenpideinstanssi-id korvaus-tehtavaryhma-id)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen korvauskulu})

        raportti (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
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

;; Johto ja hallinto on testattu muualla
;; Erillishankinnat on testattu muualla
;; Hoidonjohdon palkkio on testattu muualla

;; Testataan siis äkilliset ja vahingot
(deftest tyomaaraportti-akilliset-ja-vahingot-toimii
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
        suoritushetki (pvm/->pvm "15.10.2019")
        summa 1234M

        ;; Äkillinen hoitotyö
        akillinen-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23124")
        akillinen-tehtavaryhma-id (hae-tehtavaryhman-id "Äkilliset hoitotyöt, Soratiet (T1)")
        akillinenkulu (luo-kulu urakka-id "laskutettava" erapaiva suoritushetki koontilaskun-kuukausi summa akillinen-toimenpideinstanssi-id akillinen-tehtavaryhma-id)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen akillinenkulu})

        ;; Vahingot
        vahingot-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23124")
        vahingot-tehtavaryhma-id (hae-tehtavaryhman-id "Vahinkojen korjaukset, Soratiet (T2)")
        vahingotkulu (luo-kulu urakka-id "laskutettava" erapaiva suoritushetki koontilaskun-kuukausi summa vahingot-toimenpideinstanssi-id vahingot-tehtavaryhma-id)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen vahingotkulu})

        raportti (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                      hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        purettu (pura-tyomaaraportti-mapiksi (first raportti))]

    (is (= summa (:akilliset_hoitokausi_yht purettu)))
    (is (= summa (:akilliset_val_aika_yht purettu)))

    (is (= summa (:vahingot_hoitokausi_yht purettu)))
    (is (= summa (:vahingot_val_aika_yht purettu)))))

(deftest tavoitehinta-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        tav_hinta 100000M
        _ (u (format "update urakka_tavoite
                         set tavoitehinta_siirretty_indeksikorjattu = %s
                       where hoitokausi = 1 AND urakka = %s" tav_hinta urakka-id))

        hoitokauden_tavoitehinta (ffirst (q (format "SELECT COALESCE(ut.tavoitehinta_indeksikorjattu, ut.tavoitehinta, 0) as tavoitehinta
                                    from urakka_tavoite ut
                                    where ut.hoitokausi = %s
                                    and ut.urakka = %s" 1 urakka-id)))
        raportti (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                      hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        purettu (pura-tyomaaraportti-mapiksi (first raportti))

        tavhin_hoitokausi_yht (+ (:talvihoito_hoitokausi_yht purettu) (:lyh_hoitokausi_yht purettu)
                                (:sora_hoitokausi_yht purettu) (:paallyste_hoitokausi_yht purettu)
                                (:yllapito_hoitokausi_yht purettu) (:korvausinv_hoitokausi_yht purettu)
                                (:johtojahallinto_hoitokausi_yht purettu) (:erillishankinnat_hoitokausi_yht purettu)
                                (:hjpalkkio_hoitokausi_yht purettu) (:akilliset_hoitokausi_yht purettu)
                                (:vahingot_hoitokausi_yht purettu))
        budjettia_jaljella (- (+ (:hk_tavhintsiirto_ed_vuodelta purettu) (:hoitokauden_tavoitehinta purettu))
                             (:tavhin_hoitokausi_yht purettu))]

    (is (= tav_hinta (:hk_tavhintsiirto_ed_vuodelta purettu)))
    (is (= tavhin_hoitokausi_yht (:tavhin_hoitokausi_yht purettu)))
    (is (= budjettia_jaljella (:budjettia_jaljella purettu)))
    (is (= hoitokauden_tavoitehinta (:hoitokauden_tavoitehinta purettu)))))


(deftest tyomaaraportti-bonukset-ja-sanktiot-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        sopimus-id (hae-oulun-maanteiden-hoitourakan-2019-2024-sopimus-id)
        tpi (hae-toimenpideinstanssi-id urakka-id "23151") ;; Hallinnolliset toimenpiteet
        pvm (pvm/->pvm "15.10.2019")
        bonus_summa 1000M
        sanktio_summa 1500M
        ;; Poistetaan kaikki bonukset ja sanktiot urakalta
        _ (poista-bonukset-ja-sanktiot-aikavalilta urakka-id hk_alkupvm hk_loppupvm)

        ;; Luodaan bonus
        _ (u (format "INSERT INTO erilliskustannus (sopimus, toimenpideinstanssi, pvm, rahasumma, urakka, tyyppi)
                      VALUES (%s, %s, '%s'::DATE, %s, %s, '%s'::erilliskustannustyyppi)"
               sopimus-id tpi pvm bonus_summa urakka-id "alihankintabonus"))

        ;; Luodaan sanktio
        _ (u (format "INSERT INTO sanktio (maara, perintapvm, toimenpideinstanssi, tyyppi, suorasanktio, sakkoryhma)
                      VALUES (%s,'%s'::DATE, %s, %s, %s, '%s'::sanktiolaji)"
               sanktio_summa pvm tpi 2 true "A"))

        raportti (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                      hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        _ (println "raportti: " (pr-str raportti))

        purettu (pura-tyomaaraportti-mapiksi (first raportti))]

    (is (= bonus_summa (:bonukset_hoitokausi_yht purettu)))
    (is (= bonus_summa (:bonukset_val_aika_yht purettu)))
    (is (= (* -1 sanktio_summa) (:sanktiot_hoitokausi_yht purettu)))
    (is (= (* -1 sanktio_summa) (:sanktiot_val_aika_yht purettu)))))
