(ns harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-vahvistus-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja.testi :refer :all]
            [harja.tyokalut.yleiset :refer :all]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.palvelin.palvelut.suunnittelu.apurit :as apurit]
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
        h-tietomalli (apurit/poista-yhteenvetorivi-toimenpiteilta apurit/hankinnat-tietomalli)
        toimenpiteet (uusi-kust-kyselyt/hae-urakan-toimenpiteet (:db jarjestelma) {:urakkaid urakka-id})
        h-tietomalli (apurit/paivita-hankintojen-toimenpideinstanssi-id h-tietomalli toimenpiteet)

        erillishankinnat-yht (apply +
                               (map :summa (:erillishankinnat apurit/erillishankinnat-tietomalli)))
        hoidonjohto-yht (apply +
                          (map :summa (:hoidonjohtopalkkiot apurit/hoidonjohtopalkkiot-tietomalli)))
        jjh-yht (apply +
                  (map :summa (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025)))

        vahvista-kustus-fn (fn [vahvista?]
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
            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
                (:db jarjestelma) urakka-id (:id +kayttaja-jvh+)
                kattohintakerroin tarjous vahvistetut-vuodet)

            ;; Nyt on osiot ja tarjous, kutsu vahvistusta
            vastaus (vahvista-kustus-fn true)
            virhe (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])]
        (is (some? vastaus) "Vastaus pitäisi olla olemassa")
        (is (not (nil? virhe)) "Vahvistusvirheen pitäisi olla olemassa")
        (is (= (set virhe) #{"Hoidonjohtopalkkiot"}) "Hoidonjohtopalkkiot ovat puutteellisena")))


    (testing "Vahvistus ei onnistu, suunnitelma ei täsmää (kaikki)"
      (let [tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia
                      urakka-id
                      (+ erillishankinnat-yht 200)
                      (+ hoidonjohto-yht 200)
                      (+ jjh-yht 200))

            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
                (:db jarjestelma) urakka-id (:id +kayttaja-jvh+)
                kattohintakerroin tarjous vahvistetut-vuodet)

            vastaus (vahvista-kustus-fn true)
            virhe (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])]
        (is (some? vastaus) "Vastaus pitäisi olla olemassa")
        (is (not (nil? virhe)) "Vahvistusvirheen pitäisi olla olemassa")
        (is (= (set virhe) #{"Erillishankinnat"
                             "Hoidonjohtopalkkiot"
                             "Johto-ja-hallintokorvaukset"}) "Kaikki kentät ovat puutteellisena")))


    (testing "Vahvistus onnistuu, suunnitelma täsmää tarjousta"
      (let [tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia
                      urakka-id
                      erillishankinnat-yht
                      hoidonjohto-yht
                      jjh-yht)

            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
                (:db jarjestelma) urakka-id (:id +kayttaja-jvh+)
                kattohintakerroin tarjous vahvistetut-vuodet)

            vastaus (vahvista-kustus-fn true)
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
                                             {:tehtava 6953, :uusi_maara 1100, :maaramuutos 111, :edellinen_maara 1000, :hoitokauden_alkuvuosi 2025}
                                             {:tehtava 6953, :uusi_maara 1100, :maaramuutos 222, :edellinen_maara 1000, :hoitokauden_alkuvuosi 2026}],
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

        hae-tavoitehinta-fn (fn [hoitokausi]
                              (:tavoitehinta
                                (first (q-map
                                         (format "SELECT tavoitehinta
                                                    FROM urakka_tavoite
                                                    WHERE urakka = %s AND hoitokausi = %s" urakka-id hoitokausi)))))]

    (testing "Tavoitehinta päivittyy edellisen vuoden muutosvaikutuksilla"
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
      (let [erillishankinnat 500.00M
            hoidonjohto 300.00M
            jjh 260.00M ;; satunnaisia arvoja 
            tarjous (apurit/generoi-tarjous-tasmaa-kustannuksia
                      urakka-id
                      erillishankinnat
                      hoidonjohto
                      jjh)

            _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan
                (:db jarjestelma) urakka-id (:id +kayttaja-jvh+)
                kattohintakerroin tarjous vahvistetut-vuodet)

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
          "Tavoitehinnan pitäisi olla edellisen vuoden pysyvät muutokset + tarjous")))))
