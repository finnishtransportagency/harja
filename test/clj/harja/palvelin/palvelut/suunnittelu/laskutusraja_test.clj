(ns harja.palvelin.palvelut.suunnittelu.laskutusraja-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]

            [harja.testi :refer :all]
            [harja.tyokalut.yleiset :refer :all]
            [harja.palvelin.palvelut.suunnittelu.apurit :as apurit]
            [harja.palvelin.palvelut.suunnittelu.tarjous-palvelu :as tarjous-palvelu]
            [harja.palvelin.palvelut.suunnittelu.uusi-kustannussuunnitelma-palvelu :as kust-palvelu]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :uusi-kustannussuunnitelma (component/using
                                       (kust-palvelu/->UusiKustannussuunnitelmaPalvelu)
                                       [:http-palvelin :db])
          :tarjous (component/using
                     (tarjous-palvelu/->Tarjous)
                     [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))


(defn- vahvista-tai-kumoa-tavoite-ja-kattohinta!
  "Vahvistaa tai kumoaa tavoitteen ja kattohinnan"
  [urakka-id hoitovuoden-alkuvuosi vahvista?]
  (kutsu-palvelua (:http-palvelin jarjestelma)
    :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+
    {:urakka-id urakka-id
     :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
     :vahvista? vahvista?}))


(deftest laskutusraja-paivittyy-tavoite-ja-kattohinnan-vahvistuksessa
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Aseta laskutusraja käyttöön
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)

    ;; Tallenna kustannussuunnitelma ja tarjous
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))

    ;; Varmista että laskutusraja on NULL ennen vahvistusta
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL ennen vahvistusta")

    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)

    ;; Tarkista että laskutusraja on asetettu
    (let [laskutusraja (hae-urakan-laskutusraja urakka-id)
          tavoitehinta_indeksikorotettu (:tavoitehinta_indeksikorjattu
                                          (first (q-map (format "SELECT tavoitehinta_indeksikorjattu
                                                FROM urakka_tavoite
                                                WHERE urakka = %s AND hoitokausi = 1" urakka-id))))]
      (is (not (nil? laskutusraja)) "Laskutusrajan pitäisi olla asetettu")
      (is (= laskutusraja tavoitehinta_indeksikorotettu) "Laskutusrajan pitäisi olla sama kuin tavoitehinta_indeksikorjattu"))))


(deftest laskutusraja-ei-paivity-kun-laskutusraja_kaytossa-false
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026") ;; -21 alkanut urakka
        hoitovuoden-alkuvuosi 2024]
    ;; Varmista että laskutusraja_kaytossa = FALSE
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = FALSE WHERE urakkaid = " urakka-id)

    ;; Tallenna kustannussuunnitelma ja tarjous
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2019 apurit/johto-ja-hallinto-tietomalli-2019))

    ;; Varmista että laskutusraja on NULL ennen vahvistusta
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL ennen vahvistusta")

    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL kun laskutusraja_kaytossa = FALSE")))


(deftest laskutusraja-nollataan-kun-vahvistus-kumotaan
  (let [urakka-id (hae-urakan-id-nimella "POP MHU Kajaani 2025-2030")
        hoitovuoden-alkuvuosi 2025]
    ;; Varmista että laskutusraja_kaytossa = TRUE
    (u "UPDATE urakka_parametrit SET laskutusraja_kaytossa = TRUE WHERE urakkaid = " urakka-id)

    ;; Tallenna kustannussuunnitelma ja tarjous
    (apurit/tallenna-kustannussuunnitelma-ja-tarjous!
      (:db jarjestelma) +kayttaja-jvh+ urakka-id hoitovuoden-alkuvuosi
      (:johto-ja-hallintokorvaukset-2025 apurit/johto-ja-hallinto-tietomalli-2025))

    ;; Vahvista
    (vahvista-tai-kumoa-tavoite-ja-kattohinta! urakka-id hoitovuoden-alkuvuosi true)
    (is (not (nil? (hae-urakan-laskutusraja urakka-id))) "Laskutusrajan pitäisi olla asetettu")
    (kutsu-palvelua (:http-palvelin jarjestelma)
      :vahvista-tavoite-ja-kattohinta +kayttaja-jvh+ {:urakka-id urakka-id
                                                      :hoitovuoden-alkuvuosi hoitovuoden-alkuvuosi
                                                      :vahvista? false})
    (is (nil? (hae-urakan-laskutusraja urakka-id)) "Laskutusrajan pitäisi olla NULL vahvistuksen kumouksen jälkeen")))
