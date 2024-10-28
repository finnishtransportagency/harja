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
