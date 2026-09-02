(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-vahvistus-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.rahavaraukset :as rahavaraus-kyselyt]
            [harja.kyselyt.tehtavaryhmat :as tehtavaryhma-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.apurit :as apurit]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodi-kyselyt]
            [harja.palvelin.palvelut.muutos.muutos-palvelu :as muutos-palvelu]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as uusi-kust-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :muutokset (component/using
                       (muutos-palvelu/->Muutos {:kehitysmoodi true})
                       [:http-palvelin :db])
          :uusi-kustannussuunnitelma (component/using
                                       (kust-palvelu/->UusiKustannussuunnitelmaPalvelu)
                                       [:http-palvelin :db])
          :tarjous (component/using
                     (tarjous-palvelu/->Tarjous)
                     [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))


(deftest kustannussuunnitelma-vahvistus-2025-toimii
  (let [vahvistetut-vuodet #{}
        kattohintakerroin 1.1
        hoitovuoden-alkuvuosi 2025
        urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        toimenpiteet (uusi-kust-kyselyt/hae-urakan-toimenpiteet (:db jarjestelma) {:urakkaid urakka-id})
        h-tietomalli (apurit/paivita-hankintojen-toimenpideinstanssi-id h-tietomalli toimenpiteet)

        erillishankinnat-yht (apply +
                               (map :summa (:erillishankinnat apurit/erillishankinnat-tietomalli)))
        hoidonjohto-yht (apply +
                          (map :summa (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli)))
        jjh-yht (apply +
                  (map :summa (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025)))

        vahvista-kustis-fn (fn [vahvista?]
                             (kutsu-palvelua (:http-palvelin jarjestelma)
                               :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+
                               {:urakka-id urakka-id
                                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                                :vahvista? vahvista?}))

        ;; Kirjaa kaikki kustiksen osiot 
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat
            (:db jarjestelma) +kayttaja-jvh+ urakka-id
            hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))

        _ (uusi-kust-kyselyt/tallenna-erillishankinnat
            (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)

        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot
            (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)

        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset
            (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025) hoitovuoden-alkuvuosi)]


    (testing "Vahvistus ei onnistu, suunnitelma ei täsmää (hoidonjohto)"
      (let [tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia
                      urakka-id
                      erillishankinnat-yht
                      (+ hoidonjohto-yht 200) ;; Tällä hoidonjohtopalkkiot pitäisi epäonnistua
                      jjh-yht)

            ;; Osiot tallennettu, tallenna tarjous
            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan (:db jarjestelma) urakka-id (:id +kayttaja-jvh+) tarjous vahvistetut-vuodet)

            ;; Nyt on osiot ja tarjous, kutsu vahvistusta
            vastaus (vahvista-kustis-fn true)
            virhe (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])]
        (is (some? vastaus) "Vastaus pitäisi olla olemassa")
        (is (not (nil? virhe)) "Vahvistusvirheen pitäisi olla olemassa")
        (is (= (set virhe) #{"”Hoidonjohtopalkkio”-osiossa erittelyt eivät täsmää tarjouksen kanssa."}) "Hoidonjohtopalkkiot ovat puutteellisena")))


    (testing "Vahvistus ei onnistu, suunnitelma ei täsmää (jjh)"
      (let [tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia
                      urakka-id
                      erillishankinnat-yht
                      hoidonjohto-yht
                      (+ jjh-yht 200))

            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan (:db jarjestelma) urakka-id (:id +kayttaja-jvh+) tarjous vahvistetut-vuodet)

            vastaus (vahvista-kustis-fn true)
            virhe (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])]
        (is (some? vastaus) "Vastaus pitäisi olla olemassa")
        (is (not (nil? virhe)) "Vahvistusvirheen pitäisi olla olemassa")
        (is (= (set virhe) #{"”Johto-ja-hallintokorvaukset”-osiossa erittelyt eivät täsmää tarjouksen kanssa."}) "Johto-ja-hallintokorvaukset ovat puutteellisena")))


    (testing "Vahvistus ei onnistu, suunnitelma ei täsmää (Erillishankinnat)"
      (let [tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia
                      urakka-id
                      (+ erillishankinnat-yht 1)
                      hoidonjohto-yht
                      jjh-yht)

            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan (:db jarjestelma) urakka-id (:id +kayttaja-jvh+) tarjous vahvistetut-vuodet)

            vastaus (vahvista-kustis-fn true)
            virhe (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])]
        (is (some? vastaus) "Vastaus pitäisi olla olemassa")
        (is (not (nil? virhe)) "Vahvistusvirheen pitäisi olla olemassa")
        (is (= (set virhe) #{"”Erillishankinnat”-osiossa erittelyt eivät täsmää tarjouksen kanssa."}) "Erillishankinnat ovat puutteellisena")))


    (testing "Vahvistus ei onnistu, suunnitelma ei täsmää (monta virhettä)"
      (let [tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia
                      urakka-id
                      (+ erillishankinnat-yht 200)
                      (+ hoidonjohto-yht 200)
                      (+ jjh-yht 200))

            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan (:db jarjestelma) urakka-id (:id +kayttaja-jvh+) tarjous vahvistetut-vuodet)

            vastaus (vahvista-kustis-fn true)
            virhe (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])]
        (is (some? vastaus) "Vastaus pitäisi olla olemassa")
        (is (not (nil? virhe)) "Vahvistusvirheen pitäisi olla olemassa")
        (is (= (set virhe) #{"”Erillishankinnat”-osiossa erittelyt eivät täsmää tarjouksen kanssa."
                             "”Hoidonjohtopalkkio”-osiossa erittelyt eivät täsmää tarjouksen kanssa."
                             "”Johto-ja-hallintokorvaukset”-osiossa erittelyt eivät täsmää tarjouksen kanssa."}) "Useat kentät ovat puutteellisena")))


    (testing "Vahvistus onnistuu, suunnitelma täsmää tarjousta"
      (let [tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia
                      urakka-id
                      erillishankinnat-yht
                      hoidonjohto-yht
                      jjh-yht)

            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan (:db jarjestelma) urakka-id (:id +kayttaja-jvh+) tarjous vahvistetut-vuodet)

            vastaus (vahvista-kustis-fn true)
            virhe (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])]

        (is (some? vastaus) "Vastaus pitäisi olla olemassa")
        (is (empty? virhe) "Virhettä ei pitäisi olla vastauksessa")
        (is (= (set virhe) #{}) "Virhettä ei pitäisi olla vastauksessa")))))


(deftest kustannussuunnitelma-hoitovuoden-alun-tavoitehinta-2025-toimii
  (let [vahvistetut-vuodet #{}
        kattohintakerroin 1.1
        hoitovuoden-alkuvuosi 2025
        urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        +hoitokaudet+ (mapv (fn [vuosi]
                              [(pvm/hoitokauden-alkupvm vuosi)
                               (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))]) (range 2025 2030))
        ;; Poistetaan kaikki tarjoukseen liittyvä tietokannasta
        _ (apurit/poista-tarjoukset-tietokannasta! urakka-id)

        ;; Tyhjennä kustannussuunnitelman tiedot kokonaan 
        _ (u (format "DELETE FROM kiinteahintainen_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               urakka-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM kustannusarvioitu_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               urakka-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM johto_ja_hallintokorvaus WHERE \"urakka-id\" = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               urakka-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))


        muutos-payload {:tyyppi "pysyva"
                        :voimassa_alkaen #inst "2025-10-01T10:07:32.000-00:00",
                        :syy "Esko kävi muuttamassa suunnitelmaa",
                        :nimi "Eskon muutos"
                        :tehtavat_ja_maarat [{:tehtava 17345, :uusi? true, :maaramuutos 10, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 11235, :uusi? true, :maaramuutos 111, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 17350, :uusi? true, :maaramuutos 222, :hoitokauden_alkuvuosi 2026}
                                             {:tehtava 6953, :maaramuutos 111, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 6953, :maaramuutos 222, :hoitokauden_alkuvuosi 2026}],
                        :kustannusvaikutukset [{:toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :summa 1000, :hoitokauden_alkuvuosi 2025}
                                               {:toimenpideinstanssi 129, :kustannuslaji "hankintakustannukset", :summa 1000, :hoitokauden_alkuvuosi 2026}
                                               {:summa 1000, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 132, :hoitokauden_alkuvuosi 2025}
                                               {:summa 1000, :kustannuslaji "hankintakustannukset", :toimenpideinstanssi 132, :hoitokauden_alkuvuosi 2026}]}

        tallenna-muutos-fn (fn []
                             (kutsu-palvelua (:http-palvelin jarjestelma)
                               :tallenna-muutos
                               +kayttaja-jvh+
                               {:urakka-id urakka-id
                                :valittu-hoitokausi (first +hoitokaudet+)
                                :muutos muutos-payload}))


        vahvista-kustis-fn (fn [vahvista? hoitovuoden-alkuvuosi]
                             (kutsu-palvelua (:http-palvelin jarjestelma)
                               :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+
                               {:urakka-id urakka-id
                                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                                :vahvista? vahvista?}))

        hae-tavoitehinta-fn (fn [hoitokausi]
                              (:tavoitehinta
                                (first (q-map
                                         (format "SELECT tavoitehinta
                                                    FROM urakka_tavoite
                                                    WHERE urakka = %s AND hoitokausi = %s" urakka-id hoitokausi)))))]

    (testing "Tavoitehinta päivittyy oikein kun muutos tallennetaan"
      (let [_ (tallenna-muutos-fn)
            muutos-payload-eurot-2025 (->>
                                        (:kustannusvaikutukset muutos-payload)
                                        (filter #(= 2025 (:hoitokauden_alkuvuosi %)))
                                        (map :summa)
                                        (apply +))
            tavoitehinta-2026 (hae-tavoitehinta-fn 2)]
        (is (= (bigdec muutos-payload-eurot-2025) (bigdec tavoitehinta-2026))
          "Tavoitehinnan pitäisi olla edellisen vuoden pysyvät muutokset (tarjous 0)")))


    (testing "Tavoitehinta päivittyy oikein kun tarjous tallennetaan"
      (let [jjh 260.00M
            hoidonjohto 300.00M
            erillishankinnat 500.00M
            tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia urakka-id erillishankinnat hoidonjohto jjh)

            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan (:db jarjestelma) urakka-id (:id +kayttaja-jvh+) tarjous vahvistetut-vuodet)

            muutos-payload-eurot-2025 (->>
                                        (:kustannusvaikutukset muutos-payload)
                                        (filter #(= 2025 (:hoitokauden_alkuvuosi %)))
                                        (map :summa)
                                        (apply +))

            tavoitehinta-2026 (hae-tavoitehinta-fn 2)

            odotettu-tavoitehinta (+ muutos-payload-eurot-2025
                                    erillishankinnat
                                    hoidonjohto
                                    jjh)]
        (is (= (bigdec odotettu-tavoitehinta) (bigdec tavoitehinta-2026))
          "Tavoitehinnan pitäisi olla =(edellisen vuoden pysyvät muutokset + tarjous)")))


    (testing "Tavoitehinta päivittyy myös vahvistuksen yhteydessä"
      (let [jjh 260.00M
            hoidonjohto 300.00M
            erillishankinnat 500.00M
            tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia urakka-id erillishankinnat hoidonjohto jjh)
            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan (:db jarjestelma) urakka-id (:id +kayttaja-jvh+) tarjous vahvistetut-vuodet)

            muutos-payload-eurot-2025 (->>
                                        (:kustannusvaikutukset muutos-payload)
                                        (filter #(= 2025 (:hoitokauden_alkuvuosi %)))
                                        (map :summa)
                                        (apply +))

            odotettu-tavoitehinta (+ muutos-payload-eurot-2025
                                    erillishankinnat
                                    hoidonjohto
                                    jjh)

            _ (u (format "DELETE FROM urakka_tavoite WHERE urakka = %s AND hoitokausi = %s" urakka-id 2))
            tavoitehinta-2026-ennen-vahvistusta (hae-tavoitehinta-fn 2)
            _ (is (nil? tavoitehinta-2026-ennen-vahvistusta) "Tavoitehinta poistettiin, pitäisi olla nil")


            _ (vahvista-kustis-fn true 2026)
            tavoitehinta-2026-vahvistuksen-jalkeen (hae-tavoitehinta-fn 2)]
        (is (= (bigdec odotettu-tavoitehinta) (bigdec tavoitehinta-2026-vahvistuksen-jalkeen))
          "Tavoitehinnan pitäisi olla =(edellisen vuoden pysyvät muutokset + tarjous) vahvistuksen jälkeen")))))


(deftest vahvista-tavoite-ja-kattohinta-ei-onnistu-2021
  (let [db (:db jarjestelma)
        urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        hoitovuoden-alkuvuosi 2024
        tiedot {:urakka-id urakka-id
                :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                :vahvista? true}
        ;; Poistetaan kaikki tiedot, niin vahvistus ei voi onnistua
        _ (u (format "DELETE FROM kiinteahintainen_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               sopimus-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM kustannusarvioitu_tyo WHERE sopimus = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               sopimus-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        _ (u (format "DELETE FROM johto_ja_hallintokorvaus WHERE \"urakka-id\" = %s AND ((vuosi = %s AND kuukausi IN (10,11,12))
        OR (vuosi = %s AND kuukausi IN (1,2,3,4,5,6,7,8,9)))"
               urakka-id hoitovuoden-alkuvuosi (inc hoitovuoden-alkuvuosi)))
        ;; Kustisksen vahvistus vaatii tarjouksen tallentamisen, joten tallennetaan alkuun simppeli tarjous, niin ei jää siitä kiinni
        kattohintakerroin 1.1
        vahvistetut-vuodet #{}

        tehtavaryhma-erillishankinnat (first (tehtavaryhma-kyselyt/hae-tehtavaryhma-tunnisteella db "37d3752c-9951-47ad-a463-c1704cf22f4c"))
        tehtava-hoidonjohtopalkkio (first (toimenpidekoodi-kyselyt/hae-tehtava-tunnisteella db "53647ad8-0632-4dd3-8302-8dfae09908c8"))
        tarjous (apurit/paivita-tarjoustietomallin-idt apurit/tarjous-tietomalli-2019 tehtavaryhma-erillishankinnat tehtava-hoidonjohtopalkkio)
        _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id kayttaja-id tarjous vahvistetut-vuodet)

        vastaus (try
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                  (catch Exception e
                    (println "Tapahtui virhe:" (.getMessage e))
                    {:error (.getMessage e)}))
        _ (is (= (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe]) ["“Kilpailutettavat hankinnat” puuttuu."
                                                                            "”Erillishankinnat” puuttuu"
                                                                            "”Hoidonjohtopalkkio” puuttuu."
                                                                            "”Johto-ja-hallintokorvaukset” puuttuu"]))

        _ (u (format "update urakka set indeksi = null WHERE id = %s" urakka-id)) ;; Poistetaan urakan indeksi
        vastaus-indeksi (try
                          (kutsu-palvelua (:http-palvelin jarjestelma)
                            :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ tiedot)
                          (catch Exception e
                            (println "Tapahtui virhe:" (.getMessage e))
                            {:error (.getMessage e)}))

        _ (is (= (get-in vastaus-indeksi [:kustannussuunnitelma :vahvistus-virhe])
                ["Hoitovuoden 2024 indeksikerroin ei ole vielä saatavilla"
                 "“Kilpailutettavat hankinnat” puuttuu."
                 "”Erillishankinnat” puuttuu"
                 "”Hoidonjohtopalkkio” puuttuu."
                 "”Johto-ja-hallintokorvaukset” puuttuu"]))]))


(deftest vahvista-ja-kumoa-tavoite-ja-kattohinta-toimii-2021
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        hoitovuoden-alkuvuosi 2021
        ;; Lisätään ensin kilpailutettavat hankinnat
        ;; ;; Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019) hoitovuoden-alkuvuosi)

        ;; Varmista, että kustannussuunnitelmaa ei ole vielä vahvistettu
        kustannussuunnitelma (kutsu-palvelua (:http-palvelin jarjestelma) :hae-kustannussuunnitelman-tiedot
                               +kayttaja-jvh+
                               {:urakka-id urakka-id :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi})
        _ (is (false? (get-in kustannussuunnitelma [:kustannussuunnitelma :vahvistettu?])) "Kustannussuunnitelman pitäisi olla vahvistamaton ennen vahvistusta")

        ;; Rahavaraukset vaativat tarjouksen täyttämisen.
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
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
                    {:error (.getMessage e)}))]

    (is (nil? (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])) "Vahvistuksessa ei pitäisi olla virhettä")
    (is (not (nil? (get-in vastaus [:tarjous]))) "Vastauksessa pitäisi olla tarjous")
    (is (not (nil? (get-in vastaus [:kustannussuunnitelma]))) "Vastauksessa pitäisi olla kustannussuunnitelma")
    (is (true? (get-in vastaus [:kustannussuunnitelma :vahvistettu?])) "Vahvistettu pitäisi olla true")
    (is (= 3 (count (get-in vastaus [:kustannussuunnitelma :rahavaraukset]))) "Rahavarauksia pitäisi olla 3")
    (is (= 7 (count (get-in vastaus [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet]))) "Kilpailutettavia hankintoja pitäisi olla 7")
    (is (= 12 (count (get-in vastaus [:kustannussuunnitelma :erillishankinnat]))) "Erillishankintoja pitäisi olla 12 kuukautta")
    (is (= 10 (count (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))) "Johto- ja hallintokorvauksia pitäisi olla 9 toimenkuvaa")
    (is (= 12 (count (:kuukaudet (first (get-in vastaus [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))))) "Ensimmäisellä johto- ja hallintokorvauksella / toimenkuvalla pitäisi olla 12 kuukautta")
    (is (= 12 (count (get-in vastaus [:kustannussuunnitelma :hoidonjohtopalkkiot]))) "Hoidonjohtopalkkioissa pitäisi olla 12 kuukautta")

    ;; Kumotaan vahvistus
    (let [kumous-vastaus (try
                           (kutsu-palvelua (:http-palvelin jarjestelma)
                             :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ (merge tiedot {:vahvista? false}))
                           (catch Exception e
                             (println "Tapahtui virhe:" (.getMessage e))
                             {:error (.getMessage e)}))]
      (is (false? (get-in kumous-vastaus [:kustannussuunnitelma :vahvistettu?])) "Vahvistettu pitäisi olla false"))))


(deftest vahvista-kattohinta-toimii-tarkennettuna-2021
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        sopimus-id (hae-sopimus-id-urakka-idlla urakka-id)
        hoitovuoden-alkuvuosi 2024
        alkupvm (pvm/->pvm (str "01.10." hoitovuoden-alkuvuosi))
        loppupvm (pvm/->pvm (str "30.09." (inc hoitovuoden-alkuvuosi)))

        ;; Lisätään ensin kilpailutettavat hankinnat - Poista yhteenvetorivi ennen tallennusta
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        _ (uusi-kust-kyselyt/tallenna-kilpailutettavat-hankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi (:toimenpiteet h-tietomalli))
        ;; Lisätään erillishankinnat
        _ (uusi-kust-kyselyt/tallenna-erillishankinnat (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:erillishankinnat apurit/erillishankinnat-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään hoidonjohtopalkkiot
        _ (uusi-kust-kyselyt/tallenna-hoidonjohtopalkkiot (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli) hoitovuoden-alkuvuosi)
        ;; Lisätään johto- ja hallintokorvaukset
        _ (uusi-kust-kyselyt/tallenna-johto-ja-hallintokorvaukset (:db jarjestelma) +kayttaja-jvh+ urakka-id
            (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019) hoitovuoden-alkuvuosi)

        ;; Rahavaraukset vaativat tarjouksen täyttämisen.
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Käytetään kattohintana 1.1 x tavoitehintaa
        kattohintakerroin 1.1

        ;; Haetaan urakan rahavaraukset
        rahavaraukset (rahavaraus-kyselyt/hae-urakan-rahavaraukset (:db jarjestelma) {:urakka_id urakka-id})
        ;; Vuodet tietomallista
        vuodet (tarjous-kyselyt/vuodet-tietomallista apurit/tarjous-tietomalli-2019)
        tarjous (apurit/muodosta-tarjous-rahavarauksista rahavaraukset vuodet)
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

        _ (is (true? (get-in vastaus [:kustannussuunnitelma :vahvistettu?])) "Vahvistettu pitäisi olla true")

        jalkeen-kiinteat-rivit (q-map (format "SELECT summa, summa_indeksikorjattu, vahvistaja, indeksikorjaus_vahvistettu, vuosi, kuukausi, tpi.nimi
                                               FROM kiinteahintainen_tyo kt
                                                    join toimenpideinstanssi tpi ON kt.toimenpideinstanssi = tpi.id AND tpi.urakka = %s
                                                    JOIN toimenpide t ON tpi.toimenpide = t.id
                                              WHERE kt.sopimus = %s
                                                AND (CONCAT(kt.vuosi, '-', kt.kuukausi, '-01')::DATE BETWEEN '%s'::DATE AND '%s'::DATE)
                                                AND true = onko_mhu_hankintatoimenpide(t.koodi)
                                              ORDER BY kt.vuosi, kt.kuukausi"
                                        urakka-id sopimus-id alkupvm loppupvm))

        ;; Varmistetaan rivi riviltä, että kaikki kiinteät on vahvistettu.
        ;; Kiinteiden töiden vahvistamisessa on otettava huomioon se, että ihan kaikkia sieltä löytyviä riviä ei vahvisteta. Ainoastaan
        ;; Hankintatoimenpiteet (onko_mhu_hankintatoimenpide(t.koodi) = true).
        _ (is (every? #(not (nil? (:indeksikorjaus_vahvistettu %))) jalkeen-kiinteat-rivit) "Kaikilla kiinteähintaisilla riveillä pitäisi olla indeksi vahvistettu")]))
