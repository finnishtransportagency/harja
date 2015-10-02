(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot)

;; oulu au 2014 - 2019:
;; 1.10.2014-30.9.2015 elokuu 2015 kaikki
;;
;; Päivämäärä	Tehtävä	Yksikkö	Yksikköhinta	Suunniteltu määrä hoitokaudella	Toteutunut määrä	Suunnitellut kustannukset hoitokaudella	Toteutuneet kustannukset
;; 01.08.2015	Vesakonraivaus	ha	100,00 €	240	10	24 000,00 €	1 000,00 €
;; 19.08.2015	Vesakonraivaus	ha	100,00 €	240	10	24 000,00 €	1 000,00 €
;; 20.08.2015	Vesakonraivaus	ha	100,00 €	240	10	24 000,00 €	1 000,00 €
;; Yhteensä					72 000,00 €	3 000,00 €


(defn suorita [db parametrit]
  ;; Placeholder tällä hetkellä
  (let [naytettavat-rivit []]
    [:raportti {:nimi "Yksikköhintaisten töiden raportti"}
     [:taulukko {:otsikko "Yksikköhintaisten töiden raportti"
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")}
      [{:leveys "20%" :otsikko "Päivämäärä"}
       {:leveys "30%" :otsikko "Tehtävä"}
       {:leveys "10%" :otsikko "Yksikkö"}
       {:leveys "20%" :otsikko "Yksikköhinta"}
       {:leveys "20%" :otsikko "Suunniteltu määrä hoitokaudella"}
       {:leveys "20%" :otsikko "Toteutunut määrä"}
       {:leveys "20%" :otsikko "Suunnitellut kustannukset hoitokaudella"}
       {:leveys "20%" :otsikko "Toteutuneet kustannukset"}]

      naytettavat-rivit]]))

