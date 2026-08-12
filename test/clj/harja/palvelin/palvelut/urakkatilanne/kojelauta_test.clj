(ns harja.palvelin.palvelut.urakkatilanne.kojelauta-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.pvm :as pvm]
            [harja [testi :refer :all]]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.palvelut.urakkatilanne.kojelauta :as kojelauta]
            [harja.palvelin.palvelut.valikatselmus.paatos-apurit :as paatos-apurit]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :urakkatilanne (component/using
                           (kojelauta/->KojelautaHallinta)
                           [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

;; Helpperit
(defn tallenna-lupauspaatos [urakka-id tyyppi luvatut-pisteet toteutuneet-pisteet]
  (let [urakan-tiedot (first (urakka-kyselyt/hae-urakka (:db jarjestelma) {:id urakka-id}))
        urakan-parametrit (first (urakka-kyselyt/hae-urakan-parametrit (:db jarjestelma) {:urakkaid urakka-id}))
        indeksi (:indeksi urakan-tiedot)
        kayttajaid (:id +kayttaja-jvh+)
        hoitokauden-alkuvuosi 2024
        tavoitehinta 5M
        tarjous-tavoitehinta 5M
        lupausbonus (if (> toteutuneet-pisteet luvatut-pisteet) 100M nil)
        lupaussanktio (if (> toteutuneet-pisteet luvatut-pisteet) nil 100M)
        paatos-pvm (pvm/->pvm "12.05.2024")
        indeksikorotus (paatos-apurit/laske-indeksikorotus-lupaukselle (:db jarjestelma) urakka-id
                         paatos-pvm indeksi (if lupausbonus lupausbonus lupaussanktio) false)
        lupaussanktio nil
        bonusprosentti (:lupauspaatoksen_bonusprosentti urakan-parametrit)
        sanktioprosentti (:lupauspaatoksen_sanktioprosentti urakan-parametrit)
        erilliskustannus-id 1
        sanktio-id 1
        lupauspaatos (paatos-apurit/lupauspaatos urakka-id hoitokauden-alkuvuosi tyyppi tavoitehinta tarjous-tavoitehinta luvatut-pisteet toteutuneet-pisteet
                       lupausbonus lupaussanktio bonusprosentti sanktioprosentti indeksi indeksikorotus erilliskustannus-id sanktio-id kayttajaid)

        _ (paatos-kyselyt/tee-lupauspaatos (:db jarjestelma) lupauspaatos)]))

(deftest kaikki-mhut-kojelautaan-hk-alkuvuosi-2024
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                          :hoitokauden-alkuvuosi 2024
                                                          :urakka-idt nil
                                                          :evk-idt #{}})]
    (is (every? #(integer? (:id %)) vastaus))
    (is (every? #(string? (:nimi %)) vastaus))
    (is (every? #(integer? (:hoitokauden_alkuvuosi %)) vastaus))
    (is (every? #(integer? (:evk_id %)) vastaus))
    (is (every? #(map? (:ks_tila %)) vastaus))
    (is (= 10 (count vastaus)) "Urakoiden lukumäärä")))

(deftest kaikki-mhut-kojelautaan-hk-alkuvuosi-2024-vajaa-kayttooikeus-throwaa
  ;; Urakanvalvojan pitää nähdä
  (let [vastaus-urakanvalvojalle (kutsu-palvelua (:http-palvelin jarjestelma)
                                   :hae-urakat-kojelautaan
                                   +kayttaja-tero+
                                   {:urakkatyyppi :hoito
                                    :hoitokauden-alkuvuosi 2024
                                    :urakka-idt nil
                                    :evk-idt #{}})
        vastaus-ely-paakayttajalle (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-urakat-kojelautaan
                                     (ely-paakayttaja)
                                     {:urakkatyyppi :hoito
                                      :hoitokauden-alkuvuosi 2024
                                      :urakka-idt nil
                                      :evk-idt #{}})]
    (is (= 10 (count vastaus-urakanvalvojalle)) "Urakanvalvoja näkee")
    (is (= 10 (count vastaus-ely-paakayttajalle)) "ELY:n Pääkäyttäjä näkee"))

  ;; Urakoitsijalle ei tässä vaiheessa näytetä (myöh. suunnitelma avata oman urakan osalta)
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                           :hae-urakat-kojelautaan
                           +kayttaja-urakan-vastuuhenkilo+
                           {:urakkatyyppi :hoito
                            :hoitokauden-alkuvuosi 2015
                            :urakka-idt nil
                            :evk-idt #{}})) "Ei oikeutta, poikkeus heitetään")

  ;; Urakoitsijan Laadunvalvojakaan ei ainakaan vielä saa nähdä asioita
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                           :hae-urakat-kojelautaan
                           kemin-alueurakan-2019-2023-laadunvalvoja
                           {:urakkatyyppi :hoito
                            :hoitokauden-alkuvuosi 2020
                            :urakka-idt #{@kemin-alueurakan-2019-2023-id}
                            :evk-idt #{}})) "Ei oikeutta, poikkeus heitetään")

  ;; myöskään urakoitsijan pääkäyttäjälle ei palauteta tietoa
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                           :hae-urakat-kojelautaan
                           kemin-alueurakan-2019-2023-paakayttaja
                           {:urakkatyyppi :hoito
                            :hoitokauden-alkuvuosi 2020
                            :urakka-idt #{@kemin-alueurakan-2019-2023-id}
                            :evk-idt #{}})) "Ei oikeutta, poikkeus heitetään"))

(deftest kaikki-mhut-kojelautaan-hk-alkuvuosi-2005-ei-palauta-yhtaan
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                          :hoitokauden-alkuvuosi 2005
                                                          :urakka-idt nil
                                                          :evk-idt #{}})]

    (is (= 0 (count vastaus)) "Urakoiden lukumäärä")))

(deftest kaikki-pop-elyn-mhut-kojelautaan-hk-alkuvuosi-2024
  (let [psu-evk-id @pohjois-suomen-evk-id
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                          :hoitokauden-alkuvuosi 2024
                                                          :urakka-idt nil
                                                          :evk-idt #{psu-evk-id}})]
    (is (str/includes? vastaus "Iin MHU") "Iin MHU")
    (is (str/includes? vastaus "Raahen MHU") "Iin MHU")
    (is (str/includes? vastaus "MHU Suomussalmi") "Iin MHU")

    (is (= 4 (count vastaus)) "Urakoiden lukumäärä")))

(deftest kojelautaan-usealla-evkn-suodatuksella
  (let [psu-evk-id @pohjois-suomen-evk-id
        lapin-evk-id (ffirst (q "SELECT id FROM organisaatio WHERE nimi = 'Lappi' AND tyyppi = 'elinvoimakeskus'"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                          :hoitokauden-alkuvuosi 2024
                                                          :urakka-idt nil
                                                          :evk-idt #{psu-evk-id lapin-evk-id}})
        psu-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                      :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                              :hoitokauden-alkuvuosi 2024
                                                              :urakka-idt nil
                                                              :evk-idt #{psu-evk-id}})
        lappi-vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                        :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                                :hoitokauden-alkuvuosi 2024
                                                                :urakka-idt nil
                                                                :evk-idt #{lapin-evk-id}})]

    (testing "Lapin EVK löytyy"
      (is (some? lapin-evk-id) "Lapin elinvoimakeskus löytyy kannasta"))

    (testing "Kaikki palautetut urakat kuuluvat valittuihin EVK:iin"
      (is (every? #(contains? #{psu-evk-id lapin-evk-id} (:evk_id %)) vastaus)
        "Jokaisen urakan evk_id on joko PSU tai Lappi"))

    (testing "Useamman EVK:n haku palauttaa enemmän kuin yksittäinen"
      (is (>= (count vastaus) (count psu-vastaus))
        "Kahden EVK:n haku palauttaa vähintään yhtä monta kuin PSU yksinään"))

    (testing "Kahden EVK:n erillisten hakujen summa vastaa yhdistettyä hakua"
      (is (= (count vastaus)
            (+ (count psu-vastaus) (count lappi-vastaus)))
        "Yhdistetyn haun lukumäärä = PSU + Lappi"))))

(deftest kojelautaan-tyhja-evk-suodatus-palauttaa-kaikki
  (let [vastaus-tyhjalla (kutsu-palvelua (:http-palvelin jarjestelma)
                           :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                                   :hoitokauden-alkuvuosi 2024
                                                                   :urakka-idt nil
                                                                   :evk-idt #{}})
        vastaus-nililla (kutsu-palvelua (:http-palvelin jarjestelma)
                          :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                                  :hoitokauden-alkuvuosi 2024
                                                                  :urakka-idt nil
                                                                  :evk-idt nil})]
    (testing "Tyhjä EVK-set palauttaa kaikki urakat"
      (is (= (count vastaus-tyhjalla) (count vastaus-nililla))
        "Tyhjä set ja nil tuottavat saman tuloksen")
      (is (pos? (count vastaus-tyhjalla))
        "Tyhjä EVK-suodatus palauttaa urakoita"))))

(deftest vain-iin-mhu-kojelautaan-hk-alkuvuosi-2024
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                          :hoitokauden-alkuvuosi 2024
                                                          :urakka-idt [iin-mhu-urakka-id]
                                                          :evk-idt #{}})]
    (is (= (:indeksikerroin (first vastaus)) 1.298) "Indeksikerroin palautuu oikein")
    (is (str/includes? vastaus "Iin MHU") "Iin MHU")
    (is (= 1 (count vastaus)) "Urakoiden lukumäärä")))

(deftest oulun-mhu-kojelautaan-aloittamatta
  (let [urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                          :hoitokauden-alkuvuosi 2022
                                                          :urakka-idt [urakka-id]
                                                          :evk-idt #{}})
        rivi (first (filter #(= 2022 (:hoitokauden_alkuvuosi %))
                      vastaus))]
    (is (str/includes? vastaus "Oulun MHU") "Oulun MHU")
    (is (= (:indeksikerroin rivi) 1.352) "Indeksikerroin palautuu oikein")
    (is (= "aloittamatta" (get-in rivi [:ks_tila :suunnitelman_tila])) "tila")
    (is (= 1 (count vastaus)) "Urakoiden lukumäärä")))

(deftest raahen-mhu-kojelautaan-kaikki-osiot-vahvistettu
  (let [urakka-id (hae-urakan-id-nimella "Raahen MHU 2023-2028")
        kayttaja (:id +kayttaja-jvh+)
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'hankintakustannukset', 2, true, %s);" urakka-id kayttaja))
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'erillishankinnat', 2, true, %s);" urakka-id kayttaja))
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'johto-ja-hallintokorvaus', 2, true, %s);" urakka-id kayttaja))
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'hoidonjohtopalkkio', 2, true, %s);" urakka-id kayttaja))
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'tavoite-ja-kattohinta', 2, true, %s);" urakka-id kayttaja))
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'tavoitehintaiset-rahavaraukset', 2, true, %s);" urakka-id kayttaja))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                          :hoitokauden-alkuvuosi 2024
                                                          :urakka-idt [urakka-id]
                                                          :evk-idt #{}})
        rivi (first (filter #(= 2024 (:hoitokauden_alkuvuosi %))
                      vastaus))]
    (is (str/includes? vastaus "Raahen MHU") "Raahen MHU")
    (is (= (:indeksikerroin rivi) 1.118) "Indeksikerroin palautuu oikein")
    (is (= 6 (get-in rivi [:ks_tila :vahvistettuja])) "6 vahvistettua")
    (is (= 0 (get-in rivi [:ks_tila :aloittamattomia])) "0 aloittamatta")
    (is (= 0 (get-in rivi [:ks_tila :vahvistamattomia])) "0 vahvistamatta")
    (is (= "vahvistettu" (get-in rivi [:ks_tila :suunnitelman_tila])) "tila")
    (is (= 1 (count vastaus)) "Urakoiden lukumäärä")))


(deftest iin-mhu-kojelautaan-yksi-osio-vahvistettu
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja (:id +kayttaja-jvh+)
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'hankintakustannukset', 1, false, %s);" iin-mhu-urakka-id kayttaja))
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'hankintakustannukset', 2, false, %s);" iin-mhu-urakka-id kayttaja))
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'hankintakustannukset', 3, false, %s);" iin-mhu-urakka-id kayttaja))
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'hankintakustannukset', 4, true, %s);" iin-mhu-urakka-id kayttaja))
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'hankintakustannukset', 5, false, %s);" iin-mhu-urakka-id kayttaja))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                          :hoitokauden-alkuvuosi 2024
                                                          :urakka-idt [iin-mhu-urakka-id]
                                                          :evk-idt #{}})
        vahvistettu-2024-rivi (first (filter #(= 2024 (:hoitokauden_alkuvuosi %))
                                       vastaus))]
    (is (str/includes? vastaus "Iin MHU") "Iin MHU")
    (is (= 1 (get-in vahvistettu-2024-rivi [:ks_tila :vahvistettuja])) "yksi vahvistettu")
    (is (= 4 (get-in vahvistettu-2024-rivi [:ks_tila :aloittamattomia])) "4 aloittamatta")
    (is (= 1 (get-in vahvistettu-2024-rivi [:ks_tila :vahvistamattomia])) "1 kesken") ;; tavoitehinta on kesken, jos jotain on kirjattu
    (is (= "aloitettu" (get-in vahvistettu-2024-rivi [:ks_tila :suunnitelman_tila])) "tila")
    (is (= 1 (count vastaus)) "Urakoiden lukumäärä")))

(deftest iin-mhu-kojelautaan-yksikin-osio-aloitettu-niin-myos-tavoite-ja-kattohinta-aloitettu
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja (:id +kayttaja-jvh+)
        _ (i (format "INSERT INTO suunnittelu_kustannussuunnitelman_tila (urakka, osio, hoitovuosi, vahvistettu, luoja)
        VALUES (%s, 'hankintakustannukset', 5, false, %s);" iin-mhu-urakka-id kayttaja))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                          :hoitokauden-alkuvuosi 2025
                                                          :urakka-idt [iin-mhu-urakka-id]
                                                          :evk-idt #{}})
        vahvistettu-2024-rivi (first (filter #(= 2025 (:hoitokauden_alkuvuosi %))
                                       vastaus))]
    (is (str/includes? vastaus "Iin MHU") "Iin MHU")
    (is (= 0 (get-in vahvistettu-2024-rivi [:ks_tila :vahvistettuja])) "0 vahvistettu")
    (is (= 4 (get-in vahvistettu-2024-rivi [:ks_tila :aloittamattomia])) "4 aloittamatta")
    (is (= 2 (get-in vahvistettu-2024-rivi [:ks_tila :vahvistamattomia])) "kaksi vahvistamatta")
    (is (= "aloitettu" (get-in vahvistettu-2024-rivi [:ks_tila :suunnitelman_tila])) "tila")
    (is (= 1 (count vastaus)) "Urakoiden lukumäärä")))


(deftest lupausbonus-nousee-oikein-kojelautaan-iin-urakassa
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        psu-evk-id (hae-pohjois-suomen-evk-id)
        vastaus-ennen-paatoksia (first
                                  (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                                            :hoitokauden-alkuvuosi 2024
                                                                            :urakka-idt [urakka-id]
                                                                            :evk-idt #{}}))
        tehdyt-paatokset-ennen (:tehdyt-paatokset-count vastaus-ennen-paatoksia)
        _ (tallenna-lupauspaatos urakka-id "bonus" 76 92)
        vastaus (first
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                            :hoitokauden-alkuvuosi 2024
                                                            :urakka-idt [urakka-id]
                                                            :evk-idt #{}}))
        tehdyt-paatokset-jalkeen (:tehdyt-paatokset-count vastaus)]

    (is (= urakka-id (get-in vastaus-ennen-paatoksia [:id])) "Urakka")
    (is (= psu-evk-id (get-in vastaus-ennen-paatoksia [:evk_id])) "PSU EVK")
    (is (nil? (get-in vastaus-ennen-paatoksia [:tavoitehintapaatos])) "Tavoitehinta")
    (is (nil? (get-in vastaus-ennen-paatoksia [:kattohintapaatos])) "Kattohinta")
    (is (nil? (get-in vastaus-ennen-paatoksia [:lupauspaatokset])) "Lupauspäätökset")

    (is (= urakka-id (get-in vastaus [:id])) "Urakka")
    (is (= psu-evk-id (get-in vastaus [:evk_id])) "PSU EVK")
    (is (= 1 tehdyt-paatokset-jalkeen))
    (is (= 0 tehdyt-paatokset-ennen))))

(deftest lupauspisteet-nousee-oikein-kojelautaan-iin-urakassa
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        psu-evk-id (hae-pohjois-suomen-evk-id)
        lapin-evk-id (hae-elinvoimakeskus-id-nimella "Lappi")

        ;; Tallenna lupauspäätös kantaan
        _ (tallenna-lupauspaatos urakka-id "bonus" 76 92)

        vastaus (first
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                            :hoitokauden-alkuvuosi 2024
                                                            :urakka-idt [urakka-id]
                                                            :evk-idt #{}}))

        urakka-jossa-ei-tavoitepisteita (hae-urakan-id-nimella "Ivalon MHU testiurakka (uusi)")
        vastaus-jossa-tei-avoitepisteita
        (first
          (kutsu-palvelua (:http-palvelin jarjestelma)
            :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                    :hoitokauden-alkuvuosi 2025
                                                    :urakka-idt [urakka-jossa-ei-tavoitepisteita]
                                                    :evk-idt #{}}))]
    (is (= urakka-id (get-in vastaus [:id])) "Urakka")
    (is (= psu-evk-id (get-in vastaus [:evk_id])) "PSU EVK")
    (is (= 76 (get-in vastaus [:luvatut_pisteet])) "luvatut_pisteet")

    (is (= urakka-jossa-ei-tavoitepisteita (get-in vastaus-jossa-tei-avoitepisteita [:id])) "Urakka")
    (is (= lapin-evk-id (get-in vastaus-jossa-tei-avoitepisteita [:evk_id])) "Lapin EVK")
    (is (nil? (get-in vastaus-jossa-tei-avoitepisteita [:luvatut_pisteet])) "luvatut_pisteet")))

(deftest taytetty-nousee-oikein-kojelautaan-iin-urakassa
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        psu-evk-id (hae-pohjois-suomen-evk-id)
        hae-kojelauta-fn (fn []
                           (first
                             (kutsu-palvelua (:http-palvelin jarjestelma)
                               :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                                       :hoitokauden-alkuvuosi 2024
                                                                       :urakka-idt [urakka-id]
                                                                       :evk-idt #{}})))
        tehdyt-paatokset-ennen (:tehdyt-paatokset-count (hae-kojelauta-fn))
        ;; Tallenna lupauspäätös kantaan
        _ (tallenna-lupauspaatos urakka-id "taytetty" 76 76)
        ;; Hae uusi vastaus
        vastaus (hae-kojelauta-fn)]
    (is (= urakka-id (get-in vastaus [:id])) "Urakka")
    (is (= psu-evk-id (get-in vastaus [:evk_id])) "PSU EVK")
    (is (= 76 (get-in vastaus [:luvatut_pisteet])) "luvatut_pisteet")
    (is (= 76 (get-in vastaus [:toteutuneet_pisteet])) "luvatut_pisteet")
    (is (= 0 tehdyt-paatokset-ennen) "Päätöksiä ei ole ennenkun tehtiin lupauspäätös")
    (is (= 1 (:tehdyt-paatokset-count vastaus)) "Lupauksen pitäisi nostaa tehtyjen päätösten määrää")))


(deftest haku-ei-loyda-kun-evk-ja-urakka-ristiriidassa
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        psu-evk-id (hae-pohjois-suomen-evk-id)
        ;; Tallenna lupauspäätös kantaan
        _ (tallenna-lupauspaatos urakka-id "taytetty" 76 76)

        vastaus (first
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                            :hoitokauden-alkuvuosi 2024
                                                            :urakka-idt [urakka-id]
                                                            :evk-idt #{123}}))]
    (is (nil? (get-in vastaus [:id])) "Ei löydy urakkaa, koska 123 elinvoimakeskusta ei ole.")
    (is (nil? (get-in vastaus [:evk_id])) "Ei löydy elinvoimakeskusta.")
    (is (nil? (get-in vastaus [:luvatut_pisteet])) "Ei löydy pisteitä")
    (is (nil? (get-in vastaus [:toteutuneet_pisteet])) "Ei löydy pisteitä")
    (is (nil? (get-in vastaus [:lupauspaatos])) "Ei löydy lupauspäätöstä")))

(deftest poikkeamat-nousee-oikein-kojelautaan-iin-urakassa
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        psu-evk-id (hae-pohjois-suomen-evk-id)
        kayttaja-id (:id +kayttaja-jvh+)

        ;; Tallenna lupauspäätös kantaan
        _ (tallenna-lupauspaatos urakka-id "bonus" 76 92)

        ;; lisää laatupoikkeama hoitokauden alkuun
        _ (i (format "INSERT INTO laatupoikkeama (kohde, tekija, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys,
          ulkoinen_id, lahde, yllapitokohde, \"sisaltaa-poikkeamaraportin?\")
          VALUES ('Minimi', 'tilaaja', %s, '2024-11-14 16:00:49.791984', '2024-10-01 16:00:31.000000', null, false, false, %s, 'Poikkeama',
           1, 2, 4, 5, 3, null, 'harja-ui', null, null);\n;" kayttaja-id urakka-id))
        ;; lisää laatupoikkeama hoitokauden loppuun
        _ (i (format "INSERT INTO laatupoikkeama (kohde, tekija, luoja, luotu, aika, kasittelyaika, selvitys_pyydetty, selvitys_annettu, urakka, kuvaus, tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys,
          ulkoinen_id, lahde, yllapitokohde, \"sisaltaa-poikkeamaraportin?\")
          VALUES ('Minimi2', 'tilaaja', %s, '2024-11-14 16:00:49.791984', '2025-09-30 16:00:31.000000', null, false, false, %s, 'Poikkeama',
           1, 2, 4, 5, 3, null, 'harja-ui', null, null);\n;" kayttaja-id urakka-id))

        ;; lisää turvallisuuspoikkeama hoitokauden alkuun
        _ (i (format "INSERT INTO turvallisuuspoikkeama (urakka, tapahtunut, kasitelty, kuvaus, sairauspoissaolopaivat, sairaalavuorokaudet, luotu, luoja,
        tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys, ulkoinen_id, vahinkoluokittelu, vakavuusaste, tyyppi, tyontekijanammatti_muu, tyontekijanammatti, lahde, laatija, tapahtuman_otsikko, paikan_kuvaus, vaarallisten_aineiden_kuljetus, vaarallisten_aineiden_vuoto, tila, turi_id, juurisyy1, juurisyy1_selite, juurisyy2, juurisyy2_selite, juurisyy3, juurisyy3_selite)
        VALUES (%s, '2024-10-01 14:00:00.000000', null, 'abc', null, null, '2024-11-14 16:06:42.425530', %s,
         1, 2, 4, 5, 3, null, '{henkilovahinko}', 'lieva', '{tyotapaturma}', null, 'asentaja','harja-ui', 3, 'Minimi', null, false, false, 'avoin', null, 'puutteelliset_henkilonsuojaimet', null, null, null, null, null);\n" urakka-id kayttaja-id))
        ;; lisää turvallisuuspoikkeama hoitokauden loppuun
        _ (i (format "INSERT INTO turvallisuuspoikkeama (urakka, tapahtunut, kasitelty, kuvaus, sairauspoissaolopaivat, sairaalavuorokaudet, luotu, luoja,
        tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys, tr_alkuetaisyys, ulkoinen_id, vahinkoluokittelu, vakavuusaste, tyyppi, tyontekijanammatti_muu, tyontekijanammatti, lahde, laatija, tapahtuman_otsikko, paikan_kuvaus, vaarallisten_aineiden_kuljetus, vaarallisten_aineiden_vuoto, tila, turi_id, juurisyy1, juurisyy1_selite, juurisyy2, juurisyy2_selite, juurisyy3, juurisyy3_selite)
        VALUES (%s, '2025-09-30 14:00:00.000000', null, 'abc', null, null, '2024-1-14 16:06:42.425530', %s,
         1, 2, 4, 5, 3, null, '{henkilovahinko}', 'lieva', '{tyotapaturma}', null, 'asentaja','harja-ui', 3, 'Minimi', null, false, false, 'avoin', null, 'puutteelliset_henkilonsuojaimet', null, null, null, null, null);\n" urakka-id kayttaja-id))
        vastaus (first
                  (kutsu-palvelua (:http-palvelin jarjestelma)
                    :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :hoito
                                                            :hoitokauden-alkuvuosi 2024
                                                            :urakka-idt [urakka-id]
                                                            :evk-idt #{}}))]
    (is (= urakka-id (get-in vastaus [:id])) "Urakka")
    (is (= psu-evk-id (get-in vastaus [:evk_id])) "PSU EVK")
    (is (= 76 (get-in vastaus [:luvatut_pisteet])) "luvatut_pisteet")
    (is (= 2 (get-in vastaus [:avoimet_laatupoikkeamat])) "avoimet_laatupoikkeamat")
    (is (= 2 (get-in vastaus [:avoimet_turvallisuuspoikkeamat])) "avoimet_turvallisuuspoikkeamat")))

(deftest paallystys-tietojen-yhteenveto
  (let [urakka-id (hae-urakan-id-nimella "Utajärven päällystysurakka")
        kohteen-nimi "Tärkeä kohde mt20 2022"
        kohde-id (hae-yllapitokohteen-id-nimella kohteen-nimi)
        vastaus-aloittamatta (first
                               (kutsu-palvelua (:http-palvelin jarjestelma)
                                 :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :paallystys
                                                                         :hoitokauden-alkuvuosi 2022
                                                                         :urakka-idt [urakka-id]
                                                                         :elyt-id #{}}))

        _ (i (format "INSERT INTO public.paallystysilmoitus (paallystyskohde, ilmoitustiedot, luotu, muokattu, luoja, muokkaaja, poistettu, takuupvm, paatos_tekninen_osa, kasittelyaika_tekninen_osa, tila, perustelu_tekninen_osa, asiatarkastus_pvm, asiatarkastus_tarkastaja, asiatarkastus_hyvaksytty, asiatarkastus_lisatiedot, versio, lisatiedot, virhe, virhe_aikaleima)
        VALUES (%s, null, '2024-12-03 12:16:49.479553', '2024-12-03 12:17:24.558653', 3, 3, false, '2027-12-31', 'hyvaksytty', '2024-12-03 00:00:00.000000', 'lukittu', 'aasdasd', null, null, null, null, 2, null, null, null);\n" kohde-id))
        vastaus-aloitettu (first
                            (kutsu-palvelua (:http-palvelin jarjestelma)
                              :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :paallystys
                                                                      :hoitokauden-alkuvuosi 2022
                                                                      :urakka-idt [urakka-id]
                                                                      :evk-idt #{}}))

        _ (u (format "UPDATE paallystysilmoitus SET tila = 'valmis' WHERE paallystyskohde = %s;" kohde-id))
        vastaus-valmis-ei-lahetetty (first
                                      (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :paallystys
                                                                                :hoitokauden-alkuvuosi 2022
                                                                                :urakka-idt [urakka-id]
                                                                                :evk-idt #{}}))

        _ (u (format "UPDATE yllapitokohde SET lahetetty = NOW(), lahetysvirhe = 'paha virhe', lahetys_onnistunut = FALSE WHERE id = %s;" kohde-id))
        vastaus-lahetys-epaonnistuu (first
                                      (kutsu-palvelua (:http-palvelin jarjestelma)
                                        :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :paallystys
                                                                                :hoitokauden-alkuvuosi 2022
                                                                                :urakka-idt [urakka-id]
                                                                                :evk-idt #{}}))
        kohteen-odotettu-virhe {:kohdenimi "Tärkeä kohde mt20 2022"
                                :kohdenumero "L42"
                                :lahetysvirhe "paha virhe"
                                :tunnus "B"}
        ;; merkataan vielä lähetys onnistuneeksi ja assertataan niiden määrä
        _ (u (format "UPDATE yllapitokohde SET lahetetty = NOW(), lahetysvirhe = null, lahetys_onnistunut = TRUE WHERE id = %s;" kohde-id))
        vastaus-lahetys-onnistuu (first
                                   (kutsu-palvelua (:http-palvelin jarjestelma)
                                     :hae-urakat-kojelautaan +kayttaja-jvh+ {:urakkatyyppi :paallystys
                                                                             :hoitokauden-alkuvuosi 2022
                                                                             :urakka-idt [urakka-id]
                                                                             :evk-idt #{}}))]
    (is (= urakka-id (get-in vastaus-aloitettu [:id])) "Urakka")
    (is (= 1 (get-in vastaus-aloittamatta [:aloittamatta])) "Kohteita aloittamatta")
    (is (= 1 (get-in vastaus-aloitettu [:yllapitokohteiden_lkm])) "ylläpitokohteiden lkm")
    (is (= 1 (get-in vastaus-lahetys-epaonnistuu [:epaonnistuneet_lahetetyt])) "epäonnistuneet lähetykset")
    (is (= 1 (get-in vastaus-lahetys-onnistuu [:lahetetty_onnistuneesti])) "lahetetty_onnistuneesti")
    (is (= 1 (get-in vastaus-valmis-ei-lahetetty [:valmiit_ei_lahetetty])) "valmis ei lähetetty")
    (is (= 1 (get-in vastaus-aloitettu [:valmis_hyvaksytty])) "valmis hyväksytty")
    (is (= kohteen-odotettu-virhe
          ;; kohteen id voi muuttua kun testidata elää, dissocataan id sen vuoksi niin testi on robustimpi
          (dissoc (first (get-in vastaus-lahetys-epaonnistuu [:virheelliset_kohteet])) :id)) "Kohteen virhetiedot")))


