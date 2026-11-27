(ns harja.views.urakka.kulut-test
  "Kulut näkymän testi"
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils.shared-testutils :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.domain.kulut :as kulut-domain]
            [harja.tiedot.urakka.kulut.toteutuneet-toimenpiteet-tiedot :as toteutuneet-toimenpiteet]
            [harja.views.urakka.kulut.toteutuneet-toimenpiteet-nakyma :as toteutuneet-toimenpiteet-nakyma]
            [harja.ui.grid :as grid]
            [harja.ui.pvm :as pvm-valinta]
            [harja.pvm :as pvm]))

(t/use-fixtures :each u/komponentti-fixture)

(deftest paivamaaran-valinta-erapaiva-validointi
  (with-redefs [tila/yleiset (atom {:urakka {:alkupvm (pvm/->pvm "1.10.2014") :loppupvm (pvm/->pvm "30.09.2019")}})]
    (let [koontilaskun-kuukausi "huhtikuu/1-hoitovuosi"
          parametrit {:paivitys-fn (fn [] nil)
                      :paivamaara (kulut-domain/koontilaskun-kuukausi->pvm
                                    koontilaskun-kuukausi
                                    (-> @tila/yleiset :urakka :alkupvm)
                                    (-> @tila/yleiset :urakka :loppupvm))
                      :luokat #{}
                      :valittava?-fn (kulut-domain/koontilaskun-kuukauden-sisalla?-fn
                                       koontilaskun-kuukausi
                                       (-> @tila/yleiset :urakka :alkupvm)
                                       (-> @tila/yleiset :urakka :loppupvm))
                      :disabled false}
          tulos [pvm-valinta/pvm-valintakalenteri-inputilla parametrit]
          valittava?-fn (:valittava?-fn (second tulos))]
      (is (valittava?-fn (pvm/->pvm "04.04.2015")))
      (is (valittava?-fn (pvm/->pvm "01.04.2015")))
      (is (valittava?-fn (pvm/->pvm "30.04.2015")))
      (is (not (valittava?-fn (pvm/->pvm "31.03.2015"))))
      (is (not (valittava?-fn (pvm/->pvm "01.05.2015"))))
      (is (not (valittava?-fn (pvm/->pvm "04.04.1985")))))))

(deftest toteutuneiden-toimenpiteiden-maaralaskenta
  (let [rivi (toteutuneet-toimenpiteet/laske-rivi
               {:tavoitemaara 10
                :toteutunut_maara nil
                :toteutunut_kustannus 20
                :tavoitehintavaikutus_aina true}
               true)]
    (is (nil? (:toteutunut_maara rivi)))
    (is (nil? (:maaramuutos rivi)))
    (is (nil? (:maaramuutos_prosentteina rivi)))
    (is (nil? (:yksikkohinnan_keskiarvo rivi)))
    (is (nil? (:vaikutus_tavoitehintaan rivi)))))

(deftest toteutuneet-toimenpiteet-valiotsikoidaan-tehtavaryhmittain
  (let [rivit [{:tehtava_id 1
                :tehtavaryhmaotsikko_id 10
                :tehtavaryhmaotsikko "VUOSITARKASTUKSET"
                :tehtavaryhma_id 20
                :tehtavaryhma_nimi "Kelluvien turvalaitteiden vuositarkastustyöt ajomatkoineen"}
               {:tehtava_id 2
                :tehtavaryhmaotsikko_id 10
                :tehtavaryhmaotsikko "VUOSITARKASTUKSET"
                :tehtavaryhma_id 20
                :tehtavaryhma_nimi "Kelluvien turvalaitteiden vuositarkastustyöt ajomatkoineen"}
               {:tehtava_id 3
                :tehtavaryhmaotsikko_id 11
                :tehtavaryhmaotsikko "MUUT TARKASTUKSET"
                :tehtavaryhma_id 21
                :tehtavaryhma_nimi "Väylämerkkien tarkastukset"}]
        tulos (toteutuneet-toimenpiteet-nakyma/toimenpiteet-valiotsikoilla rivit)]
    (is (grid/otsikko? (first tulos)))
    (is (= "VUOSITARKASTUKSET / Kelluvien turvalaitteiden vuositarkastustyöt ajomatkoineen"
           (:teksti (first tulos))))
    (is (= [1 2]
           (mapv :tehtava_id (remove grid/otsikko? (take 3 tulos)))))
    (is (grid/otsikko? (nth tulos 3)))
    (is (= "MUUT TARKASTUKSET / Väylämerkkien tarkastukset"
           (:teksti (nth tulos 3))))
    (is (= 3 (:tehtava_id (nth tulos 4))))))

(deftest tavoitehintavaikutus-lasketaan-prosenttirajasta
  (is (= 20
        (toteutuneet-toimenpiteet/tavoitehintavaikutus
          {:tavoitemaara 10
           :toteutunut_maara 12
           :toteutunut_kustannus 120
           :tavoitehintavaikutus_ehdollinen 10}
          true)))
  (is (= -20
        (toteutuneet-toimenpiteet/tavoitehintavaikutus
          {:tavoitemaara 10
           :toteutunut_maara 8
           :toteutunut_kustannus 80
           :tavoitehintavaikutus_ehdollinen 10}
          true)))
  (is (nil? (toteutuneet-toimenpiteet/tavoitehintavaikutus
              {:tavoitemaara 0
               :toteutunut_maara 0
               :toteutunut_kustannus 100
               :tavoitehintavaikutus_aina true}
              true))))
