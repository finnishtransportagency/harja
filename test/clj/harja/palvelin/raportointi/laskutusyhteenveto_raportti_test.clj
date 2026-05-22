(ns harja.palvelin.raportointi.laskutusyhteenveto-raportti-test
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.testi :refer :all]
            [harja.palvelin.palvelut.urakat :refer :all]
            [harja.palvelin.raportointi :as raportointi]
            [harja.palvelin.palvelut.raportit :as raportit]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.toimenpidekoodit :refer :all]


            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.palvelin.palvelut.kulut.kulut :as kulut]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.apurit :as uusi-kust-apurit]
            [harja.palvelin.palvelut.valikatselmus.paatos-apurit :as paatos-apurit]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as uusi-kust-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]
            ))

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


(defn- arvo-raportin-nnesta-elementista [vastaus n]
  (second (first (second (second (last (nth (nth (last vastaus) n) 3)))))))


(deftest raportin-suoritus-urakalle-toimii-hoitokausi-2014-2015
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :laskutusyhteenveto
                   :konteksti "urakka"
                   :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                   :parametrit {:urakkatyyppi :hoito
                                :alkupvm (c/to-date (t/local-date 2014 10 1))
                                :loppupvm (c/to-date (t/local-date 2015 9 30))}})]
    (is (vector? vastaus))
    (let [odotettu-otsikko "Oulun alueurakka 2014-2019, 01.10.2014-30.09.2015"
          saatu-otsikko (second (nth vastaus 2))

          oulun-au-talvihoito-kok-hint-maksueranumero (first (first (nth (nth (last vastaus) 0) 3)))

          kok-hint (arvo-raportin-nnesta-elementista vastaus 0)
          yks-hint (arvo-raportin-nnesta-elementista vastaus 1)
          sanktiot (arvo-raportin-nnesta-elementista vastaus 2)
          talvisuolasakot (arvo-raportin-nnesta-elementista vastaus 3)
          muutos-ja-lisatyot (arvo-raportin-nnesta-elementista vastaus 4)

          numero (ffirst (q "SELECT numero
                             FROM maksuera
                             WHERE nimi = 'Oulu Talvihoito TP ME 2014-2019' AND
                                   tyyppi = 'kokonaishintainen';"))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")

      (is (= (str "Talvihoito (#" numero ")") oulun-au-talvihoito-kok-hint-maksueranumero))

      (is (=marginaalissa? kok-hint 162010.00M))
      (is (=marginaalissa? yks-hint -4000.0M))
      (is (=marginaalissa? sanktiot -29760.00000M))
      (is (=marginaalissa? talvisuolasakot 1000.0M))
      (is (=marginaalissa? muutos-ja-lisatyot 3000.0M)))))


(deftest raportin-suoritus-urakalle-toimii-hoitokausi-2016-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :laskutusyhteenveto
                   :konteksti "urakka"
                   :urakka-id (hae-oulun-alueurakan-2014-2019-id)
                   :parametrit {:urakkatyyppi :hoito
                                :alkupvm (c/to-date (t/local-date 2016 10 1))
                                :loppupvm (c/to-date (t/local-date 2017 9 30))}})]
    (is (vector? vastaus))
    (let [odotettu-otsikko "Oulun alueurakka 2014-2019, 01.10.2016-30.09.2017"
          saatu-otsikko (second (nth vastaus 2))
          yks-hint (arvo-raportin-nnesta-elementista vastaus 0)
          sanktiot (arvo-raportin-nnesta-elementista vastaus 1)

          indeksitarkistukset-yks-hint (arvo-raportin-nnesta-elementista vastaus 2)
          indeksitarkistukset-sanktiot (arvo-raportin-nnesta-elementista vastaus 3)
          indeksitarkistukset-muut-kuin-kokhint (arvo-raportin-nnesta-elementista vastaus 4)
          indeksitarkistukset-kaikki (arvo-raportin-nnesta-elementista vastaus 5)
          kaikki-paitsi-kokhint-yhteensa (arvo-raportin-nnesta-elementista vastaus 6)
          kaikki-yhteensa (arvo-raportin-nnesta-elementista vastaus 7)
          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))]
      (is (= odotettu-otsikko saatu-otsikko) "otsikko")

      (is (=marginaalissa? yks-hint 7882.50M))
      (is (=marginaalissa? sanktiot -1900.67M))
      (is (=marginaalissa? indeksitarkistukset-yks-hint 2310.387931034483003250M))
      (is (=marginaalissa? indeksitarkistukset-sanktiot -571.6564M))
      (is (=marginaalissa? indeksitarkistukset-muut-kuin-kokhint 1738.731531034483003250M))
      (is (=marginaalissa? indeksitarkistukset-kaikki 1738.731531034483003250M))
      (is (=marginaalissa? kaikki-paitsi-kokhint-yhteensa 7720.565531034483003250M))
      (is (= (fmt/desimaaliluku kaikki-yhteensa 2)
            (fmt/desimaaliluku nurkkasumma 2)
            "7720,57")) "Loppusumma oikein")))


(deftest raportin-suoritus-pop-elylle-toimii-hoitokausi-2014-2015-kun-092015-indeksiarvo-puuttuu
  (let [_ (u (str "DELETE FROM indeksi WHERE nimi = 'MAKU 2005' AND kuukausi = 9 AND vuosi = 2015"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :laskutusyhteenveto
                   :konteksti "elinvoimakeskus"
                   :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                   :parametrit {:urakkatyyppi :hoito
                                :alkupvm (c/to-date (t/local-date 2014 10 1))
                                :loppupvm (c/to-date (t/local-date 2015 9 30))}})]
    (is (vector? vastaus))
    (let [odotettu-otsikko "Pohjois-Suomi, 01.10.2014-30.09.2015"
          saatu-otsikko (second (nth vastaus 2))
          varoitus-indeksiarvojen-puuttumisesta (nth vastaus 3)

          vastaus (butlast (butlast vastaus))
          kok-hint (arvo-raportin-nnesta-elementista vastaus 0)
          yks-hint (arvo-raportin-nnesta-elementista vastaus 1)
          sanktiot (arvo-raportin-nnesta-elementista vastaus 2)
          talvisuolasakot (arvo-raportin-nnesta-elementista vastaus 3)
          muutos-ja-lisatyot (arvo-raportin-nnesta-elementista vastaus 4)]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")
      (is (= varoitus-indeksiarvojen-puuttumisesta
            [:varoitusteksti "Seuraavissa urakoissa indeksilaskentaa ei voitu täysin suorittaa, koska tarpeellisia indeksiarvoja puuttuu: Oulun alueurakka 2014-2019, Kajaanin alueurakka 2014-2019"]))
      (is (=marginaalissa? kok-hint 324020.0M))
      (is (=marginaalissa? yks-hint 6000.0M))
      (is (=marginaalissa? sanktiot -8000.0M))
      (is (=marginaalissa? talvisuolasakot -59520.0M))
      (is (=marginaalissa? muutos-ja-lisatyot 12000.0M)))))


(deftest raportin-suoritus-pop-elylle-toimii-hoitokausi-2016-2017
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :suorita-raportti
                  +kayttaja-jvh+
                  {:nimi :laskutusyhteenveto
                   :konteksti "elinvoimakeskus"
                   :elinvoimakeskus-id (hae-pohjois-suomen-evk-id)
                   :parametrit {:urakkatyyppi :hoito
                                :alkupvm (c/to-date (t/local-date 2016 10 1))
                                :loppupvm (c/to-date (t/local-date 2017 9 30))}})]
    (is (vector? vastaus))
    (let [odotettu-otsikko "Pohjois-Suomi, 01.10.2016-30.09.2017"
          saatu-otsikko (second (nth vastaus 2))
          vastaus (butlast (butlast vastaus))

          yks-hint (arvo-raportin-nnesta-elementista vastaus 0)
          sanktiot (arvo-raportin-nnesta-elementista vastaus 1)

          yks-hint-indeksitarkistukset (arvo-raportin-nnesta-elementista vastaus 2)
          sanktioiden-indeksitarkistukset (arvo-raportin-nnesta-elementista vastaus 3)
          muiden-kuin-kokhint-indeksitarkistukset (arvo-raportin-nnesta-elementista vastaus 4)
          kaikki-indeksitarkistukset (arvo-raportin-nnesta-elementista vastaus 5)
          kaikki-paitsi-kokhint-yhteensa (arvo-raportin-nnesta-elementista vastaus 6)
          kaikki-yhteensa (arvo-raportin-nnesta-elementista vastaus 7)
          nurkkasumma (:arvo (second (second (last (last (last (last vastaus)))))))]

      (is (= odotettu-otsikko saatu-otsikko) "otsikko")

      (is (=marginaalissa? yks-hint 7882.50M))
      (is (=marginaalissa? sanktiot -1900.67M))
      (is (=marginaalissa? yks-hint-indeksitarkistukset 2310.39M))
      (is (=marginaalissa? sanktioiden-indeksitarkistukset -571.6564M))
      (is (=marginaalissa? muiden-kuin-kokhint-indeksitarkistukset 1738.731531034483003250M))
      (is (=marginaalissa? kaikki-indeksitarkistukset 1738.731531034483003250M))
      (is (=marginaalissa? kaikki-paitsi-kokhint-yhteensa 7720.565531034483003250M))

      (is (= (fmt/desimaaliluku kaikki-yhteensa 2)
            (fmt/desimaaliluku nurkkasumma 2)
            "7720,57") "Loppusumma oikein"))))


(deftest tuotekohtainen-laskutusraja-2025-mhu+toimii
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

        raportti (q-map (format "select * from mhu_laskutusyhteenveto_tuotekohtainen('%s'::DATE, '%s'::DATE, '%s'::DATE, '%s'::DATE, %s)"
                          hk_alkupvm hk_loppupvm aikavali_alkupvm aikavali_loppupvm urakka-id))

        ;; ----------------------------------------------------------------
        ;; Laskutusrajan arvot pitäisi olla saatavilla sekä näyttää oikealta
        purettu (pura-tuotekohtainen-raportti-mapiksi (first raportti))

        laskutusraja (:laskutusraja_yht purettu)
        jaljella (:laskutusrajaan_jaljella purettu)
        kaytossa (:onko_laskutusraja_kaytossa purettu)
        ylittynyt (:onko_laskutusraja_ylittynyt purettu)
        laskutettavaa (:laskutusraja_laskutettavaa_yht purettu)]

    (is (false? ylittynyt) "Laskutusrajan ei pitäisi olla ylittynyt")
    (is (true? kaytossa) "Lasktutusrajan pitäisi olla käytössä MHU+ urakalla")
    (is (= jaljella (- laskutusraja talvihoitosumma)) "Laskutusraja pitäisi alentua kulun perusteella")
    (is (= laskutettavaa talvihoitosumma) "Laskutettavaa pitäisi olla kirjatun kulun verran")

    (is (= talvihoitosumma (:hankinnat_laskutettu purettu)))
    (is (= talvihoitosumma (:hankinnat_laskutetaan purettu)))))



