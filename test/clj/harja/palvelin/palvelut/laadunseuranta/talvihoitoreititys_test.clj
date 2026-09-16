(ns harja.palvelin.palvelut.laadunseuranta.talvihoitoreititys-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [dk.ative.docjure.spreadsheet :as xls]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit-palvelu :as talvihoitoreitit-palvelu]
            [harja.kyselyt.kalustoresurssit :as kalustoresurssit-q]
            [harja.testi :refer :all]
            [com.stuartsierra.component :as component]))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :excel-vienti (component/using (excel-vienti/luo-excel-vienti)
                          [:http-palvelin])
          :talvihoitoreitit (component/using
                                   (talvihoitoreitit-palvelu/->Talvihoitoreitit)
                              [:http-palvelin :db :excel-vienti])))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures
                      jarjestelma-fixture
                      urakkatieto-fixture))

(deftest testaa-talvihoitoreititys-excel-tuonti
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        workbook (xls/load-workbook-from-file "test/resurssit/excel/talvihoitoreitit_tuonti_ok.xlsx")
        ;; Tallenna Excelistä luetut talvihoitoreitit kantaan
        _ (talvihoitoreitit-palvelu/kasittele-excel (:db jarjestelma) urakka-id +kayttaja-jvh+ nil workbook)

        ;; Hae juuri tallennetut talvihoitoreitit
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma) :hae-urakan-talvihoitoreitit +kayttaja-jvh+ {:urakka-id urakka-id})]

    ;; Varmistetaan data
    (is (= (count vastaus) 6) "Urakalla on kuusi reittiä.")
    (is (= (:nimi (nth vastaus 5)) "Pysäköinti") "Kohteen nimi on Pysäköinti.")
    (is (= (:laskettu_pituus (nth vastaus 5)) 0.012) "Pysäköintikohteen pituus on 0.012.")
    (is (= (:tr_maara (nth vastaus 5)) 2) "TR täsmää")
    (is (= (nil? (:ka_maara (nth vastaus 5)))) "KA täsmää")
    (is (= (nil? (:kup_maara (nth vastaus 5)))) "KA täsmää")
    (is (= (:hoitoluokat (nth vastaus 5))
          {:huoltoaukot [{:ryhma :huoltoaukot, :hoitoluokka "Talvihoito", :pituus 0.012}]}) "Hoitoluokat täsmää")))

(deftest testaa-talvihoitoreititys-excel-tuonti-virheellisella-excelilla
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        workbook (xls/load-workbook-from-file "test/resurssit/excel/talvihoitoreitit_tuonti_fail.xlsx")
        ;; Tallenna Excelistä luetut talvihoitoreitit kantaan
        excel-vastaus (talvihoitoreitit-palvelu/kasittele-excel (:db jarjestelma) urakka-id +kayttaja-jvh+ nil workbook)]
    (is (= (str/includes? (str excel-vastaus) "Reittien ja kaluston määrä ei täsmää.")) "Excel lataus ei onnistu.")))

(deftest testaa-talvihoito-kalustoyhteenveto
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        kayttaja-id (:id +kayttaja-jvh+)
        ;; Kirjataan urakalle luvattu kalusto kahdelle hoitoluokkaryhmälle
        _ (kalustoresurssit-q/tallenna-kalustoresurssi<! (:db jarjestelma)
            {:urakka-id urakka-id :hoitoluokkaryhma "ise-ib" :maara 5 :kayttaja-id kayttaja-id})
        _ (kalustoresurssit-q/tallenna-kalustoresurssi<! (:db jarjestelma)
            {:urakka-id urakka-id :hoitoluokkaryhma "ic-iii" :maara 3 :kayttaja-id kayttaja-id})
        ;; Tuodaan reitit Excelistä
        workbook (xls/load-workbook-from-file "test/resurssit/excel/talvihoitoreitit_tuonti_ok.xlsx")
        _ (talvihoitoreitit-palvelu/kasittele-excel (:db jarjestelma) urakka-id +kayttaja-jvh+ nil workbook)

        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                  :hae-urakan-talvihoito-kalustoyhteenveto +kayttaja-jvh+ {:urakka-id urakka-id})]

    (is (= 2 (count vastaus)) "Yhteenvedossa on rivi molemmille käytössä oleville hoitoluokkaryhmälle.")
    (is (= ["ise-ib" "ic-iii"] (mapv :hoitoluokkaryhma vastaus)) "Rivit ovat hoitoluokkaryhmien järjestyksessä.")
    (is (= 5 (:luvattu (first vastaus))) "Ise–Ib luvattu kalusto täsmää.")
    (is (= 3 (:luvattu (second vastaus))) "Ic–III luvattu kalusto täsmää.")
    (is (every? integer? (map :suunniteltu vastaus)) "Reiteille suunniteltu kalusto on kokonaisluku jokaiselle riville.")))


(deftest testaa-talvihoitoreititys-excel-vienti
  (let [urakka-id (hae-urakan-id-nimella "Iin MHU 2021-2026")
        vastaus (:raportti (kutsu-excel-palvelua (:http-palvelin jarjestelma)
                             :lataa-talvihoitoreitit-exceliin +kayttaja-jvh+ {:urakka-id urakka-id}))]
    (is (= (:nimi (second vastaus)) "Iin MHU 2021-2026 - Talvihoitoreitit"))
    (is (= (nth vastaus 2) [:otsikko-heading "Reitti tunnistetaan nimen perusteella."]))
    (is (= (count vastaus) 5) "Excelistä löytyy viisi elementtiä.")))
