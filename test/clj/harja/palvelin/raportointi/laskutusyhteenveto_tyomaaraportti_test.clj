(ns harja.palvelin.raportointi.laskutusyhteenveto-tyomaaraportti-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.kyselyt.konversio :as konversio]
            [harja.testi :refer :all]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
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
         :hankinnat_ja_hoidon_hk_yht (nth raportti 22)
         :hankinnat_ja_hoidon_val_yht (nth raportti 23)
         :tavhin_hoitokausi_yht (nth raportti 24)
         :tavhin_val_aika_yht (nth raportti 25)
         :hoitokauden_tavoitehinta (nth raportti 26)
         :tavoitehinta_on_oikaistu (nth raportti 27)
         :hk_valikatselmus_siirrot_ed_vuodelta (nth raportti 28)
         :budjettia_jaljella (nth raportti 29)
         :lisatyo_talvihoito_hoitokausi_yht (nth raportti 30)
         :lisatyo_talvihoito_val_aika_yht (nth raportti 31)
         :lisatyo_lyh_hoitokausi_yht (nth raportti 32)
         :lisatyo_lyh_val_aika_yht (nth raportti 33)
         :lisatyo_sora_hoitokausi_yht (nth raportti 34)
         :lisatyo_sora_val_aika_yht (nth raportti 35)
         :lisatyo_paallyste_hoitokausi_yht (nth raportti 36)
         :lisatyo_paallyste_val_aika_yht (nth raportti 37)
         :lisatyo_yllapito_hoitokausi_yht (nth raportti 38)
         :lisatyo_yllapito_val_aika_yht (nth raportti 39)
         :lisatyo_korvausinv_hoitokausi_yht (nth raportti 40)
         :lisatyo_korvausinv_val_aika_yht (nth raportti 41)
         :lisatyo_hoidonjohto_hoitokausi_yht (nth raportti 42)
         :lisatyo_hoidonjohto_val_aika_yht (nth raportti 43)
         :lisatyot_hoitokausi_yht (nth raportti 44)
         :lisatyot_val_aika_yht (nth raportti 45)
         :bonukset_hoitokausi_yht (nth raportti 46)
         :bonukset_val_aika_yht (nth raportti 47)
         :sanktiot_hoitokausi_yht (nth raportti 48)
         :sanktiot_val_aika_yht (nth raportti 49)
         :paatos_tavoitepalkkio_hoitokausi_yht (nth raportti 50)
         :paatos_tavoitepalkkio_val_aika_yht (nth raportti 51)
         :paatos_tavoiteh_ylitys_hoitokausi_yht (nth raportti 52)
         :paatos_tavoiteh_ylitys_val_aika_yht (nth raportti 53)
         :paatos_kattoh_ylitys_hoitokausi_yht (nth raportti 54)
         :paatos_kattoh_ylitys_val_aika_yht (nth raportti 55)
         :muut_kustannukset_hoitokausi_yht (nth raportti 56)
         :muut_kustannukset_val_aika_yht (nth raportti 57)
         :yhteensa_kaikki_hoitokausi_yht (nth raportti 58)
         :yhteensa_kaikki_val_aika_yht (nth raportti 59)
         :perusluku (nth raportti 60)
         :rahavaraus_nimet (nth raportti 61)
         :hoitokausi_yht_array (nth raportti 62)
         :val_aika_yht_array (nth raportti 63)
         :kaikki_rahavaraukset_hoitokausi_yht (nth raportti 64)
         :kaikki_rahavaraukset_val_yht (nth raportti 65)
         :muut_kulut_hoitokausi (nth raportti 66)
         :muut_kulut_val_aika (nth raportti 67)
         :muut_kulut_hoitokausi_yht (nth raportti 68)
         :muut_kulut_val_aika_yht (nth raportti 69)
         :muut_kulut_ei_tavoite_hoitokausi (nth raportti 70)
         :muut_kulut_ei_tavoite_val_aika (nth raportti 71)
         :muut_kulut_ei_tavoite_hoitokausi_yht (nth raportti 72)
         :muut_kulut_ei_tavoite_val_aika_yht (nth raportti 73)}]

    tulos))


(defn luo-kulu
  "Luo tällä hetkellä aina tavoitehintaisen kulun. Lisää uusi parametri, jos se on ongelma."
  [urakka-id tyyppi erapaiva kohdistustyyppi koontilaskun-kuukausi summa toimenpideinstanssi-id tehtavaryhma-id rahavaraus]
  {:id nil
   :urakka urakka-id
   :viite "123456781"
   :erapaiva erapaiva
   :kokonaissumma summa
   :tyyppi tyyppi
   :kohdistukset [{:kohdistus-id nil
                   :rivi 1
                   :summa summa
                   :toimenpideinstanssi toimenpideinstanssi-id
                   :tehtavaryhma tehtavaryhma-id
                   :tehtava nil
                   :tyyppi kohdistustyyppi
                   :rahavaraus rahavaraus
                   :tavoitehintainen :true}]
   :koontilaskun-kuukausi koontilaskun-kuukausi})


(deftest raportin-suoritus-urakalle-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        vastaus (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                     hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))]
    (is (not (nil? vastaus)) "Saatiin raportti")
    (is (= (count (first vastaus)) 74) "Raportilla on oikea määrä rivejä")))


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
        talvihoitosumma 1234M

        talvihoitokulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi talvihoitosumma toimenpideinstanssi-id tehtavaryhma-id nil)
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
        toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23116")
        tehtavaryhma-id (hae-tehtavaryhman-id "L - Liikennemerkit ja liikenteenohjauslaitteet")
        lyhsumma 1234M

        lyhkulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi lyhsumma toimenpideinstanssi-id tehtavaryhma-id nil)
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
        summa 1234M

        ;; Sora
        sora-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "23124")
        sora-tehtavaryhma-id (hae-tehtavaryhman-id "C - Sorateiden hoito")
        sorakulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi summa sora-toimenpideinstanssi-id sora-tehtavaryhma-id nil)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen sorakulu})

        ;; Päällyste
        paal-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "20107")
        paal-tehtavaryhma-id (hae-tehtavaryhman-id "Y1 - Kuumapäällyste")
        paalkulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi summa paal-toimenpideinstanssi-id paal-tehtavaryhma-id nil)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen paalkulu})

        ;; Ylläpito
        yl-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "20191")
        yl-tehtavaryhma-id (hae-tehtavaryhman-id "F - Muut, MHU ylläpito")
        ylkulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi summa yl-toimenpideinstanssi-id yl-tehtavaryhma-id nil)
        _ (kutsu-http-palvelua :tallenna-kulu (oulun-2019-urakan-urakoitsijan-urakkavastaava)
            {:urakka-id urakka-id
             :kulu-kohdistuksineen ylkulu})

        ;; Korvausinvestointi
        korvaus-toimenpideinstanssi-id (hae-toimenpideinstanssi-id urakka-id "14301")
        korvaus-tehtavaryhma-id (hae-tehtavaryhman-id "Q - RKR-korjaus")
        korvauskulu (luo-kulu urakka-id "laskutettava" erapaiva "hankintakulu" koontilaskun-kuukausi summa korvaus-toimenpideinstanssi-id korvaus-tehtavaryhma-id nil)
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

(deftest tavoitehinta-toimii
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        kayttaja-id (:id +kayttaja-jvh+)
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        tav_hinta 100000M
        siirto-ed-vuodelta 60000.0M
        ;; Lisää siirretyt kulut Välikatselmuksesta "edelliseltä vuodelta"
        _ (i (format "INSERT INTO urakka_paatos (\"hoitokauden-alkuvuosi\", \"urakka-id\", \"hinnan-erotus\", \"urakoitsijan-maksu\", \"tilaajan-maksu\", siirto, tyyppi, \"lupaus-luvatut-pisteet\", \"lupaus-toteutuneet-pisteet\", \"lupaus-tavoitehinta\", muokattu, \"muokkaaja-id\", \"luoja-id\", luotu, poistettu, erilliskustannus_id, sanktio_id, kulu_id)
        VALUES (2018, %s, null, 39395.784199999995, 0, %s, 'kattohinnan-ylitys', null, null, null, null, null, %s, '2019-11-01 10:12:11.886000', false, null, null, 51);" urakka-id siirto-ed-vuodelta kayttaja-id))

        hoitokauden_tavoitehinta (ffirst (q (format "SELECT COALESCE(ut.tavoitehinta_indeksikorjattu, ut.tavoitehinta, 0) as tavoitehinta
                                    from urakka_tavoite ut
                                    where ut.hoitokausi = %s
                                    and ut.urakka = %s" 1 urakka-id)))
        raportti (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
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
                                 (:hjpalkkio_hoitokausi_yht purettu) (:akilliset_hoitotyot_hk purettu)
                                 (:vahinkojen_korjaukset_hk purettu) (:muut_kulut_hoitokausi_yht purettu))
        budjettia_jaljella (- (:hoitokauden_tavoitehinta purettu) (:tavhin_hoitokausi_yht purettu))]

    (is (= siirto-ed-vuodelta (:hk_valikatselmus_siirrot_ed_vuodelta purettu)))
    ;; Tarkastetaan, että siirto mukana muiden kulujen yhteissummassa
    (is (= (+ siirto-ed-vuodelta (:muut_kulut_hoitokausi purettu)) (:muut_kulut_hoitokausi_yht purettu)))
    (is (= tavhin_hoitokausi_yht (:tavhin_hoitokausi_yht purettu)))
    (is (= budjettia_jaljella (:budjettia_jaljella purettu)))
    (is (= hoitokauden_tavoitehinta (:hoitokauden_tavoitehinta purettu)))))


(deftest tyomaaraportti-bonukset-ja-sanktiot-toimii-ennen-2022
  (let [hk_alkupvm "2019-10-01"
        hk_loppupvm "2020-09-30"
        aikavali_alkupvm "2019-10-01"
        aikavali_loppupvm "2020-09-30"
        urakka-id (hae-oulun-maanteiden-hoitourakan-2019-2024-id)
        sopimus-id (hae-oulun-maanteiden-hoitourakan-2019-2024-sopimus-id)
        tpi-hallinnolliset-toimenpiteet (hae-toimenpideinstanssi-id urakka-id "23151") ;; Hallinnolliset toimenpiteet
        ;; Päivämäärä (käsittelypäivä) ja laskutuskuukausi, voi olla samat näissä testeissä, vaikka oikeasti ne voi vaihdella
        pvm (pvm/->pvm "15.10.2019")
        bonus_summa 1000M
        alihankintabonus_summa 7777M
        sanktio_summa 1500M

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

        raportti (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                      hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))
        purettu (pura-tyomaaraportti-mapiksi (first raportti))]

    (is (= (+ alihankintabonus_summa bonus_summa) (:bonukset_hoitokausi_yht purettu)))
    (is (= (+ alihankintabonus_summa bonus_summa) (:bonukset_val_aika_yht purettu)))
    (is (= (* -1 sanktio_summa) (:sanktiot_hoitokausi_yht purettu)))
    (is (= (* -1 sanktio_summa) (:sanktiot_val_aika_yht purettu)))))


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

        raportti (q (format "select * from ly_raportti_tyomaakokous('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                      hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        purettu (pura-tyomaaraportti-mapiksi (first raportti))]

    (is (= (* 2 bonus_summa) (:bonukset_hoitokausi_yht purettu)))
    (is (= (* 2 bonus_summa) (:bonukset_val_aika_yht purettu)))))
