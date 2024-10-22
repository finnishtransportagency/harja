(ns harja.palvelin.palvelut.hallinta.kojelauta-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [harja.palvelin.palvelut.hallinta.kojelauta :as kojelauta]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja
             [testi :refer :all]]))


(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :kojelauta-hallinta (component/using
                                (kojelauta/->KojelautaHallinta)
                                [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest kaikki-mhut-kojelautaan-hk-alkuvuosi-2024
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2024
                                                          :urakka-idt nil
                                                          :ely-id nil})]
    (is (every? #(integer? (:id %)) vastaus))
    (is (every? #(string? (:nimi %)) vastaus))
    (is (every? #(integer? (:hoitokauden_alkuvuosi %)) vastaus))
    (is (every? #(integer? (:ely_id %)) vastaus))
    (is (every? #(map? (:ks_tila %)) vastaus))
    (is (= 10 (count vastaus)) "Urakoiden lukumäärä")))

(deftest kaikki-mhut-kojelautaan-hk-alkuvuosi-2024-vajaa-kayttooikeus-throwaa
  ;; Kojelauta tässä vaiheessa vain pääkäyttäjälle, urakanvalvojalle ei näytetä
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                           :hae-urakat-kojelautaan
                           +kayttaja-tero+
                           {:hoitokauden-alkuvuosi 2024
                            :urakka-idt nil
                            :ely-id nil})) "Ei oikeutta poikkeus heitetään")

  ;; Kojelauta tässä vaiheessa vain pääkäyttäjälle, urakoitsijalle ei näytetä
  (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                           :hae-urakat-kojelautaan
                           +kayttaja-urakan-vastuuhenkilo+
                           {:hoitokauden-alkuvuosi 2015
                            :urakka-idt nil
                            :ely-id nil})) "Ei oikeutta poikkeus heitetään")
  )

(deftest kaikki-mhut-kojelautaan-hk-alkuvuosi-2005-ei-palauta-yhtaan
  (let [vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2005
                                                          :urakka-idt nil
                                                          :ely-id nil})]

    (is (= 0 (count vastaus)) "Urakoiden lukumäärä")))

(deftest kaikki-pop-elyn-mhut-kojelautaan-hk-alkuvuosi-2024
  (let [pop-ely-id @pohjois-pohjanmaan-hallintayksikon-id
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2024
                                                          :urakka-idt nil
                                                          :ely-id pop-ely-id})]
    (is (str/includes? vastaus "Iin MHU") "Iin MHU")
    (is (str/includes? vastaus "Raahen MHU") "Iin MHU")
    (is (str/includes? vastaus "MHU Suomussalmi") "Iin MHU")
    (is (str/includes? vastaus "MHU Kajaani") "Kajaanin MHU")

    (is (= 4 (count vastaus)) "Urakoiden lukumäärä")))

(deftest vain-iin-mhu-kojelautaan-hk-alkuvuosi-2024
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2024
                                                          :urakka-idt [iin-mhu-urakka-id]
                                                          :ely-id nil})]
    (is (str/includes? vastaus "Iin MHU") "Iin MHU")
    (is (= 1 (count vastaus)) "Urakoiden lukumäärä")))

(deftest oulun-mhu-kojelautaan-aloittamatta
  (let [urakka-id (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2022
                                                          :urakka-idt [urakka-id]
                                                          :ely-id nil})
        rivi (first (filter #(= 2022 (:hoitokauden_alkuvuosi %))
                                       vastaus))]
    (is (str/includes? vastaus "Oulun MHU") "Oulun MHU")
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
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2024
                                                          :urakka-idt [urakka-id]
                                                          :ely-id nil})
        rivi (first (filter #(= 2024 (:hoitokauden_alkuvuosi %))
                      vastaus))]
    (is (str/includes? vastaus "Raahen MHU") "Raahen MHU")
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
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2024
                                                          :urakka-idt [iin-mhu-urakka-id]
                                                          :ely-id nil})
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
                  :hae-urakat-kojelautaan +kayttaja-jvh+ {:hoitokauden-alkuvuosi 2025
                                                          :urakka-idt [iin-mhu-urakka-id]
                                                          :ely-id nil})
        vahvistettu-2024-rivi (first (filter #(= 2025 (:hoitokauden_alkuvuosi %))
                                       vastaus))]
    (is (str/includes? vastaus "Iin MHU") "Iin MHU")
    (is (= 0 (get-in vahvistettu-2024-rivi [:ks_tila :vahvistettuja])) "0 vahvistettu")
    (is (= 4 (get-in vahvistettu-2024-rivi [:ks_tila :aloittamattomia])) "4 aloittamatta")
    (is (= 2 (get-in vahvistettu-2024-rivi [:ks_tila :vahvistamattomia])) "kaksi vahvistamatta")
    (is (= "aloitettu" (get-in vahvistettu-2024-rivi [:ks_tila :suunnitelman_tila])) "tila")
    (is (= 1 (count vastaus)) "Urakoiden lukumäärä")))

(defn kustannussuunnitelman-tila [urakka osio hoitovuosi]
  (first (q-map "SELECT id, urakka, osio, hoitovuosi, vahvistettu FROM suunnittelu_kustannussuunnitelman_tila WHERE osio = %s AND urakka = %s AND hoitovuosi = %s")))

(deftest hankintakustannus-nakyy-oikein-tilataulussa
  (let [iin-mhu-urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        iin-talvihoidon-toimenpideinstanssi (hae-toimenpideinstanssi-id-nimella "Iin MHU 2021-2026 Talvihoito TP")
        iin-sopimus-id (hae-sopimus-id-nimella "MHU Ii sopimus")
        integraatio-kayttajan-id (hae-kayttajan-id-kayttajanimella "Integraatio")
        ;; luodaan vahvistamaton hankintakustannus urakkaan hoitokaudelle 2021-2022
        _ (i (format "INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus, luotu, luoja, summa_indeksikorjattu, versio)
        VALUES (2021, 10, 22, %s, %s, '2024-10-22 12:22:55.341000', %s,  23.496, 0);" iin-talvihoidon-toimenpideinstanssi iin-sopimus-id kayttaja-id))
        ;; luodaan vahvistettu hankintakustannus urakkaan hoitokaudelle 2024-2025
        _ (i (format "INSERT INTO kiinteahintainen_tyo (vuosi, kuukausi, summa, toimenpideinstanssi, sopimus, luotu, luoja, summa_indeksikorjattu, indeksikorjaus_vahvistettu, vahvistaja, versio)
        VALUES (2024, 10, 44, %s, %s, '2024-10-22 12:34:47.559000', %s, 57.112, '2024-10-22 12:34:50.641000', %s, 0);" iin-talvihoidon-toimenpideinstanssi iin-sopimus-id kayttaja-id kayttaja-id))

        ei-tilatauluja-ennen-paivitysta (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s;" iin-mhu-urakka-id))
        ;; Päivitetään kust. suunnitelman tilataulu niiltä hoitovuosilta jonne muutos tehtiin, jonka jälkeen assertoidaan että tieto on päivittynyt oikein
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2021);")
        _ (q "SELECT * FROM korjaa_kustannussuunnitelmien_puuttuvat_tilat(2024);")
        tilataulut-paivitystyksen-jalkeen (q (format "SELECT * FROM suunnittelu_kustannussuunnitelman_tila WHERE urakka = %s;" iin-mhu-urakka-id))
        tilataulu-hankintakustannus-2021 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 1)
        tilataulu-hankintakustannus-2022 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 2)
        tilataulu-hankintakustannus-2023 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 3)
        tilataulu-hankintakustannus-2024 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 4)
        tilataulu-hankintakustannus-2025 (kustannussuunnitelman-tila iin-mhu-urakka-id "hankintakustannukset" 5)]


    (is (empty? ei-tilatauluja-ennen-paivitysta) "Ennen päivitystä urakassa ei tietoa tilataulussa")
    (is (not (empty? tilataulut-paivitystyksen-jalkeen)) "Päivityksen jälkeen urakassa on tietoa tilataulussa")
    (is (false? (:vahvistettu tilataulu-hankintakustannus-2021)) "2021 tilatieto on false")
    (is (false? (:vahvistettu tilataulu-hankintakustannus-2022)) "2022 tilatietoa ei ole")
    (is (false? (:vahvistettu tilataulu-hankintakustannus-2023)) "2023 tilatietoa ei ole")
    (is (true? (:vahvistettu tilataulu-hankintakustannus-2024)) "2024 vahvistettu on true")
    (is (number? (:vahvistaja tilataulu-hankintakustannus-2024)) "2024 vahvistetaja löytyy")
    (is (false? (:vahvistettu tilataulu-hankintakustannus-2025)) "2025 tilatietoa ei ole")
    (is (= (:muokkaaja tilataulu-hankintakustannus-2021) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")
    (is (= (:muokkaaja tilataulu-hankintakustannus-2024) integraatio-kayttajan-id) "Muokkaaja on Integraatio-käyttäjä silloin kun korjausskriptillä on päivitetty tilaa.")))


