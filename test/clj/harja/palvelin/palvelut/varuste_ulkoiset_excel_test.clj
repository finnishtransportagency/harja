(ns harja.palvelin.palvelut.varuste-ulkoiset-excel-test
  (:require [clojure.test :refer [deftest is testing]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.integraatiot.velho.varusteet :as velho]
            [harja.palvelin.palvelut.varuste-ulkoiset-excel :as varuste-ulkoiset-excel]
            [harja.palvelin.raportointi.excel :as excel]))

(deftest vie-ulkoiset-varusteet-exceliin-ilman-hoitovuosirajausta-test
  (testing "Excel-vienti muodostaa ei-rajatulle haulle järkevän otsikoinnin"
    (with-redefs [oikeudet/vaadi-lukuoikeus (fn [& _])
                  urakat-q/hae-urakka (fn [_ _] [{:nimi "Testiurakka"}])
                  velho/hae-urakan-varustetoteumat (fn [_ _] {:toteumat []})
                  excel/muodosta-excel (fn [raportti _] {:raportti raportti})]
      (let [vastaus (varuste-ulkoiset-excel/vie-ulkoiset-varusteet-exceliin
                      {:db :db}
                      :workbook
                      {:id 1}
                      {:urakka-id 123
                       :hoitokauden-alkuvuosi nil
                       :hoitovuoden-kuukausi 10})
            raportti (:raportti vastaus)
            raportin-asetukset (second raportti)]
        (is (= "Varustetoimenpiteet Ei hoitovuosirajausta" (:nimi raportin-asetukset))
          "Ei-rajatun haun tiedostonimen pitää kertoa rajauksen puuttumisesta")
        (is (= [["Hoitovuosi" "Ei hoitovuosirajausta"]] (:tietoja raportin-asetukset))
          "Ei-rajatun haun Exceliin pitää lisätä tieto hoitovuosirajauksen puuttumisesta")
        (is (= {:raportin-nimi "Varustetoimenpiteet, Ei hoitovuosirajausta"
                :urakka "Testiurakka"
                :alkupvm nil
                :loppupvm nil
                :custom-ylin-rivi "Varustetoimenpiteet, Testiurakka, Ei hoitovuosirajausta"}
               (:raportin-yleiset-tiedot raportin-asetukset))
          "Ei-rajatun haun Excelin pitää välittää otsikointi raportin yleisiin tietoihin")))))
