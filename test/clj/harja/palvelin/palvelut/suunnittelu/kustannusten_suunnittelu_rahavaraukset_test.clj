(ns harja.palvelin.palvelut.suunnittelu.kustannusten-suunnittelu-rahavaraukset-test
  (:require [clojure.test :refer [deftest testing use-fixtures compose-fixtures is]]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]
            [harja.tyokalut.yleiset :refer :all]
            [taoensso.timbre :as log]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (luo-testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :budjetoidut-tyot (component/using
                              (bs/->Budjettisuunnittelu)
                              [:http-palvelin :db])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

;; Tehdään esimerkki Oulu MHU:lle, mikä täytyy löytyä
(def odotetut-kustannus-rahavaraukset
  '({:toimenpide-avain :tavoitehintaiset-rahavaraukset, :haettu-asia "Äkilliset hoitotyöt", :indeksikorjaus-vahvistettu nil, :summa 500M, :nimi "Äkilliset hoitotyöt", :vuosi 2019, :id 1, :poistettu false, :hoitokauden-numero 1, :summa-indeksikorjattu 540.500000M :jarjestys 2}
    {:toimenpide-avain :tavoitehintaiset-rahavaraukset, :haettu-asia "Vahinkojen korjaukset", :indeksikorjaus-vahvistettu nil, :summa nil, :nimi "Vahinkojen korjaukset", :vuosi 2019, :id 2, :poistettu false, :hoitokauden-numero 1, :summa-indeksikorjattu nil :jarjestys 3}
    {:toimenpide-avain :tavoitehintaiset-rahavaraukset, :haettu-asia "Tilaajan rahavaraus kannustinjärjestelmään", :indeksikorjaus-vahvistettu nil, :summa nil, :nimi "Tilaajan rahavaraus kannustinjärjestelmään", :vuosi 2019, :id 3, :poistettu false, :hoitokauden-numero 1, :summa-indeksikorjattu nil :jarjestys 11}))

;; Hae Oulu MHU:n rahavaraukset
(deftest hae-rahavaraukset-onnistuu-test
  (let [oulu-mhu (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        suunnitellut-kustannukset (bs/hae-urakan-kustannusarvoidut-tyot (:db jarjestelma) +kayttaja-jvh+ oulu-mhu)
        tavoitehintaiset-rahavaraukset-2019 (filter #(and
                                                       (= (:toimenpide-avain %) :tavoitehintaiset-rahavaraukset)
                                                       (= (:vuosi %) 2019))
                                              suunnitellut-kustannukset)]
    (is (= odotetut-kustannus-rahavaraukset tavoitehintaiset-rahavaraukset-2019))))

(deftest hae-endpointista-rahavaraukset-onnistuu-test
  (let [oulu-mhu (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :budjetoidut-tyot +kayttaja-jvh+ {:urakka-id oulu-mhu})
        suunnitellut-kustannukset (:kustannusarvioidut-tyot vastaus)
        tavoitehintaiset-rahavaraukset-2019 (filter #(and
                                                       (= (:toimenpide-avain %) :tavoitehintaiset-rahavaraukset)
                                                       (= (:vuosi %) 2019))
                                              suunnitellut-kustannukset)]
    (is (= odotetut-kustannus-rahavaraukset tavoitehintaiset-rahavaraukset-2019))))


;; Lisää vuodelle 2020 uusi summa rahavaraukselle
(deftest lisaa-summa-tavoitehintaiselle-rahavaraukselle-onnistuu-test
  (let [oulu-mhu (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Haetaan ensin vuosittaiset indeksiarvot
        indeksivastaus (kutsu-palvelua (:http-palvelin jarjestelma) :budjettisuunnittelun-indeksit +kayttaja-jvh+ {:urakka-id oulu-mhu})
        vuosi 2020
        vuoden-indeksikerroin (:indeksikerroin (first (filter #(= vuosi (:vuosi %)) indeksivastaus)))

        ;; Haetaan rahavarauksen id
        rahavaraus-id (:id (first (q-map (format "SELECT id FROM rahavaraus WHERE nimi = '%s'"
                                           "Tilaajan rahavaraus kannustinjärjestelmään"))))
        summa 100
        indeksisumma (* summa vuoden-indeksikerroin)
        payload {:urakka-id oulu-mhu
                 :rahavaraus-id rahavaraus-id
                 :vuosi vuosi
                 :loppuvuodet? false
                 :summa summa
                 :indeksisumma indeksisumma}

        tallennus-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tavoitehintainen-rahavaraus +kayttaja-jvh+ payload)
        budjetoidut-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :budjetoidut-tyot +kayttaja-jvh+ {:urakka-id oulu-mhu})
        suunnitellut-kustannukset (:kustannusarvioidut-tyot budjetoidut-vastaus)
        tavoitehintaiset-rahavaraukset-2020 (filter #(and
                                                       (= (:toimenpide-avain %) :tavoitehintaiset-rahavaraukset)
                                                       (= (:vuosi %) 2020))
                                              suunnitellut-kustannukset)
        muuttunut-rahavaraus (first (filter
                                      #(= (:id %) rahavaraus-id)
                                      tavoitehintaiset-rahavaraukset-2020))]
    (is (= (:summa muuttunut-rahavaraus) (bigdec summa)))
    (is (= (round2 2 (:summa-indeksikorjattu muuttunut-rahavaraus)) (round2 2 (bigdec indeksisumma))))))

;; Helpperit helpottamaan lukemista
(defn third [coll]
  (nth coll 2))
(defn fourth [coll]
  (nth coll 3))


;; Muokkaa rahavarausta
(deftest muokkaa-summa-tavoitehintaiselle-rahavaraukselle-onnistuu-test
  (let [oulu-mhu (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Haetaan ensin vuosittaiset indeksiarvot
        indeksivastaus (kutsu-palvelua (:http-palvelin jarjestelma) :budjettisuunnittelun-indeksit +kayttaja-jvh+ {:urakka-id oulu-mhu})
        vuosi 2020
        vuoden-indeksikerroin (:indeksikerroin (first (filter #(= vuosi (:vuosi %)) indeksivastaus)))

        ;; Haetaan muokattavan rahavarauksen id
        rahavaraus-id (:id (first (q-map (format "SELECT id FROM rahavaraus WHERE nimi = '%s'"
                                           "Äkilliset hoitotyöt"))))
        summa 100
        indeksisumma (* summa vuoden-indeksikerroin)
        payload {:urakka-id oulu-mhu
                 :rahavaraus-id rahavaraus-id
                 :vuosi vuosi
                 :loppuvuodet? false
                 :summa summa
                 :indeksisumma indeksisumma}

        tallennus-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tavoitehintainen-rahavaraus +kayttaja-jvh+ payload)
        budjetoidut-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :budjetoidut-tyot +kayttaja-jvh+ {:urakka-id oulu-mhu})
        suunnitellut-kustannukset (:kustannusarvioidut-tyot budjetoidut-vastaus)
        tavoitehintaiset-rahavaraukset-2020 (filter #(and
                                                       (= (:toimenpide-avain %) :tavoitehintaiset-rahavaraukset)
                                                       (= (:vuosi %) 2020))
                                              suunnitellut-kustannukset)
        muuttunut-rahavaraus (first (filter
                                      #(= (:id %) rahavaraus-id)
                                      tavoitehintaiset-rahavaraukset-2020))]
    (is (= (:summa muuttunut-rahavaraus) (bigdec summa)))
    (is (= (round2 2 (:summa-indeksikorjattu muuttunut-rahavaraus)) (round2 2 (bigdec indeksisumma))))))

;; Muokkaa rahavarausta myös tuleville vuosille
(deftest muokkaa-summa-tuleville-vuosille-tavoitehintaiselle-rahavaraukselle-onnistuu-test
  (let [oulu-mhu (hae-urakan-id-nimella "Oulun MHU 2019-2024")
        ;; Haetaan ensin vuosittaiset indeksiarvot
        indeksivastaus (kutsu-palvelua (:http-palvelin jarjestelma) :budjettisuunnittelun-indeksit +kayttaja-jvh+ {:urakka-id oulu-mhu})
        vuosi 2020
        vuoden-indeksikerroin (:indeksikerroin (first (filter #(= vuosi (:vuosi %)) indeksivastaus)))

        ;; Haetaan muokattavan rahavarauksen id
        rahavaraus-id (:id (first (q-map (format "SELECT id FROM rahavaraus WHERE nimi = '%s'"
                                           "Äkilliset hoitotyöt"))))
        summa 100
        indeksisumma (* summa vuoden-indeksikerroin)
        payload {:urakka-id oulu-mhu
                 :rahavaraus-id rahavaraus-id
                 :vuosi vuosi
                 :loppuvuodet? true
                 :summa summa
                 :indeksisumma indeksisumma}

        tallennus-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :tallenna-tavoitehintainen-rahavaraus +kayttaja-jvh+ payload)
        budjetoidut-vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :budjetoidut-tyot +kayttaja-jvh+ {:urakka-id oulu-mhu})
        suunnitellut-kustannukset (:kustannusarvioidut-tyot budjetoidut-vastaus)
        tavoitehintaiset-rahavaraukset (filter #(and
                                                  (= (:toimenpide-avain %) :tavoitehintaiset-rahavaraukset)
                                                  (>= (:vuosi %) 2020))
                                         suunnitellut-kustannukset)
        muuttuneet-rahavaraukset (filter
                                   #(= (:id %) rahavaraus-id)
                                   tavoitehintaiset-rahavaraukset)]
    ;; Vuosi 2020
    (is (= (:summa (first muuttuneet-rahavaraukset)) (bigdec summa)))
    (is (= (round2 2 (:summa-indeksikorjattu (first muuttuneet-rahavaraukset))) (round2 2 (bigdec indeksisumma))))
    ;; Vuosi 2021
    (is (= (:summa (second muuttuneet-rahavaraukset)) (bigdec summa)))
    (is (= (round2 2 (:summa-indeksikorjattu (second muuttuneet-rahavaraukset))) (round2 2 (bigdec indeksisumma))))
    ;; Vuosi 2022
    (is (= (:summa (third muuttuneet-rahavaraukset)) (bigdec summa)))
    (is (= (round2 2 (:summa-indeksikorjattu (third muuttuneet-rahavaraukset))) (round2 2 (bigdec indeksisumma))))
    ;; Vuosi 2023
    (is (= (:summa (fourth muuttuneet-rahavaraukset)) (bigdec summa)))
    (is (= (round2 2 (:summa-indeksikorjattu (fourth muuttuneet-rahavaraukset))) (round2 2 (bigdec indeksisumma))))))
