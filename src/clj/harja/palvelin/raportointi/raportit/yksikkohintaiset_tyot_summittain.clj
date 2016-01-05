(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-summittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.yksikkohintaiset-tyot :refer [hae-yksikkohintaiset-tyot-per-paiva]]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm toimenpide-id] :as parametrit}]
  (let [naytettavat-rivit (hae-yksikkohintaiset-tyot-per-paiva db
                                                               urakka-id alkupvm loppupvm
                                                               (if toimenpide-id true false) toimenpide-id)

        raportin-nimi "Yksikköhintaisten töiden raportti"
        konteksti :urakka ;; myöhemmin tähänkin rapsaan voi tulla muitakin kontekseja, siksi alle yleistä koodia
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    ;:hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    ;:koko-maa "KOKO MAA"
                    )
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
                        :suunniteltu_maara
                        :toteutunut_maara
                        (comp fmt/euro-opt :suunnitellut_kustannukset)
                        (comp fmt/euro-opt :toteutuneet_kustannukset))
                  naytettavat-rivit)
            [nil "Yhteensä" nil nil nil nil
             (fmt/euro-opt (reduce + (keep :suunnitellut_kustannukset naytettavat-rivit)))
             (fmt/euro-opt (reduce + (keep :toteutuneet_kustannukset naytettavat-rivit)))])]]))

