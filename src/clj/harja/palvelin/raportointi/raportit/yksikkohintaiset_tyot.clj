(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.yksikkohintaiset-tyot :refer [hae-yksikkohintaiset-tyot-per-paiva]]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]))

;; oulu au 2014 - 2019:
;; 1.10.2014-30.9.2015 elokuu 2015 kaikki
;;
;; Päivämäärä	Tehtävä	Yksikkö	Yksikköhinta	Suunniteltu määrä hoitokaudella	Toteutunut määrä	Suunnitellut kustannukset hoitokaudella	Toteutuneet kustannukset
;; 01.08.2015	Vesakonraivaus	ha	100,00 €	240	10	24 000,00 €	1 000,00 €
;; 19.08.2015	Vesakonraivaus	ha	100,00 €	240	10	24 000,00 €	1 000,00 €
;; 20.08.2015	Vesakonraivaus	ha	100,00 €	240	10	24 000,00 €	1 000,00 €
;; Yhteensä					72 000,00 €	3 000,00 €

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm toimenpide-id] :as parametrit}]
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [naytettavat-rivit (hae-yksikkohintaiset-tyot-per-paiva db
                                                               urakka-id alkupvm loppupvm
                                                               (if toimenpide-id true false) toimenpide-id)

        raportin-nimi "Yksikköhintaiset työt päivittäin"
        konteksti :urakka
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id))))
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :viimeinen-rivi-yhteenveto? true
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")}
      [{:leveys "10%" :otsikko "Päivämäärä"}
       {:leveys "25%" :otsikko "Tehtävä"}
       {:leveys "5%" :otsikko "Yks."}
       {:leveys "10%" :otsikko "Yksikkö\u00adhinta"}
       {:leveys "10%" :otsikko "Suunniteltu määrä hoitokaudella"}
       {:leveys "10%" :otsikko "Toteutunut määrä"}
       {:leveys "15%" :otsikko "Suunnitellut kustannukset hoitokaudella"}
       {:leveys "15%" :otsikko "Toteutuneet kustannukset"}]

      (conj (mapv (juxt (comp pvm/pvm :pvm)
                        :nimi
                        :yksikko
                        (comp fmt/euro-opt :yksikkohinta)
                        (comp #(fmt/desimaaliluku % 1) :suunniteltu_maara)
                        (comp #(fmt/desimaaliluku % 1) :toteutunut_maara)
                        (comp fmt/euro-opt :suunnitellut_kustannukset)
                        (comp fmt/euro-opt :toteutuneet_kustannukset))
                  naytettavat-rivit)
            [nil "Yhteensä" nil nil nil nil
             (fmt/euro-opt (reduce + (keep :suunnitellut_kustannukset naytettavat-rivit)))
             (fmt/euro-opt (reduce + (keep :toteutuneet_kustannukset naytettavat-rivit)))])]]))

