(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.yksikkohintaiset-tyot :as q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]))

(defn muodosta-raportti-urakalle [db {:keys [urakka-id alkupvm loppupvm toimenpide-id]}]
  (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-urakalle db
                                                           urakka-id alkupvm loppupvm
                                                           (if toimenpide-id true false) toimenpide-id))

(defn muodosta-raportti-hallintayksikolle [db {:keys [hallintayksikko-id alkupvm loppupvm toimenpide-id]}]
  (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle db
                                                                    hallintayksikko-id alkupvm loppupvm
                                                                    (if toimenpide-id true false) toimenpide-id))

(defn muodosta-raportti-koko-maalle [db {:keys [alkupvm loppupvm toimenpide-id]}]
  (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle db
                                                              alkupvm loppupvm
                                                              (if toimenpide-id true false) toimenpide-id))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm toimenpide-id] :as parametrit}]
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-rivit (case konteksti
                            :urakka
                            (muodosta-raportti-urakalle db
                                                        {:urakka-id     urakka-id
                                                         :alkupvm       alkupvm
                                                         :loppupvm      loppupvm
                                                         :toimenpide-id toimenpide-id})
                            :hallintayksikko
                            (muodosta-raportti-hallintayksikolle db
                                                                 {:hallintayksikko-id hallintayksikko-id
                                                                  :alkupvm            alkupvm
                                                                  :loppupvm           loppupvm
                                                                  :toimenpide-id      toimenpide-id})
                            :koko-maa
                            (muodosta-raportti-koko-maalle db
                                                           {:alkupvm       alkupvm
                                                            :loppupvm      loppupvm
                                                            :toimenpide-id toimenpide-id}))

        raportin-nimi "Yksikköhintaiset työt tehtävittäin"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko                    otsikko
                 :viimeinen-rivi-yhteenveto? true
                 :tyhja                      (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")}
      [{:leveys "25%" :otsikko "Tehtävä"}
       {:leveys "5%"  :otsikko "Yks."}
       {:leveys "10%" :otsikko "Yksikkö\u00adhinta"}
       {:leveys "10%" :otsikko "Suunniteltu määrä hoitokaudella"}
       {:leveys "10%" :otsikko "Toteutunut määrä"}
       {:leveys "15%" :otsikko "Suunnitellut kustannukset hoitokaudella"}
       {:leveys "15%" :otsikko "Toteutuneet kustannukset"}]

      (conj (mapv (juxt :nimi
                        :yksikko
                        (comp fmt/euro-opt :yksikkohinta)
                        :suunniteltu_maara
                        :toteutunut_maara
                        (comp fmt/euro-opt :suunnitellut_kustannukset)
                        (comp fmt/euro-opt :toteutuneet_kustannukset))
                  naytettavat-rivit)
            ["Yhteensä" nil nil nil nil
             (fmt/euro-opt (reduce + (keep :suunnitellut_kustannukset naytettavat-rivit)))
             (fmt/euro-opt (reduce + (keep :toteutuneet_kustannukset naytettavat-rivit)))])]]))

