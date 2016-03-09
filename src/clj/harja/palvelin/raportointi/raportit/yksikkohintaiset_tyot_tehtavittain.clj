(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.yksikkohintaiset-tyot :as q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot :as yks-hint-tyot]))

(defn hae-summatut-tehtavat-urakalle [db {:keys [urakka-id alkupvm loppupvm toimenpide-id]}]
  (let [suunnittelutiedot (yks-hint-tyot/hae-urakan-hoitokaudet db urakka-id)
        toteumat (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-urakalle db
                                                                     urakka-id alkupvm loppupvm
                                                                     (not (nil? toimenpide-id)) toimenpide-id)
        toteumat (yks-hint-tyot/liita-toteumiin-suunnittelutiedot alkupvm loppupvm toteumat suunnittelutiedot)]
    toteumat))

(defn hae-summatut-tehtavat-hallintayksikolle [db {:keys [hallintayksikko-id alkupvm loppupvm toimenpide-id urakoittain?]}]
  (if urakoittain?
    (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle-urakoittain db
                                                                                       hallintayksikko-id alkupvm loppupvm
                                                                                       (not (nil? toimenpide-id)) toimenpide-id)
    (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle db
                                                                           hallintayksikko-id alkupvm loppupvm
                                                                           (not (nil? toimenpide-id)) toimenpide-id)))

(defn hae-summatut-tehtavat-koko-maalle [db {:keys [alkupvm loppupvm toimenpide-id urakoittain?]}]
  (if urakoittain?
    (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle-urakoittain db
                                                                                 alkupvm loppupvm
                                                                                 (not (nil? toimenpide-id)) toimenpide-id)
    (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle db
                                                                     alkupvm loppupvm
                                                                     (not (nil? toimenpide-id)) toimenpide-id)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm toimenpide-id urakoittain?] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-rivit (case konteksti
                            :urakka
                            (hae-summatut-tehtavat-urakalle db
                                                            {:urakka-id     urakka-id
                                                             :alkupvm       alkupvm
                                                             :loppupvm      loppupvm
                                                             :toimenpide-id toimenpide-id})
                            :hallintayksikko
                            (hae-summatut-tehtavat-hallintayksikolle db
                                                                     {:hallintayksikko-id hallintayksikko-id
                                                                      :alkupvm            alkupvm
                                                                      :loppupvm           loppupvm
                                                                      :toimenpide-id      toimenpide-id
                                                                      :urakoittain?       urakoittain?})
                            :koko-maa
                            (hae-summatut-tehtavat-koko-maalle db
                                                               {:alkupvm       alkupvm
                                                                :loppupvm      loppupvm
                                                                :toimenpide-id toimenpide-id
                                                                :urakoittain?  urakoittain?}))

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
                 :tyhja                      (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")
                 :oikealle-tasattavat-kentat (if (= konteksti :urakka)
                                               #{2 5 6}
                                               (if urakoittain?
                                               #{4}
                                               #{}))}
      (flatten (keep identity [(when urakoittain?
                                 {:leveys 25 :otsikko "Urakka"})
                               {:leveys 25 :otsikko "Tehtävä"}
                               {:leveys 5 :otsikko "Yks."}
                               (when (= konteksti :urakka)
                                 [{:leveys 10 :otsikko "Yksikkö\u00adhinta"}
                                  {:leveys 10 :otsikko "Suunniteltu määrä hoitokaudella"}])
                               {:leveys 10 :otsikko "Toteutunut määrä"}
                               (when (= konteksti :urakka)
                                 [{:leveys 15 :otsikko "Suunnitellut kustannukset hoitokaudella"}
                                  {:leveys 15 :otsikko "Toteutuneet kustannukset"}])]))
      (keep identity
            (conj (mapv (fn [rivi]
                          (flatten (keep identity [(when urakoittain?
                                                     (:urakka_nimi rivi))
                                                   (:nimi rivi)
                                                   (:yksikko rivi)
                                                   (when (= konteksti :urakka)
                                                     [(fmt/euro-opt (:yksikkohinta rivi))
                                                      (fmt/desimaaliluku-opt (:suunniteltu_maara rivi) 1)])
                                                   (fmt/desimaaliluku-opt (:toteutunut_maara rivi) 1)
                                                   (when (= konteksti :urakka)
                                                     [(fmt/euro-opt (:suunnitellut_kustannukset rivi))
                                                      (fmt/euro-opt (:toteutuneet_kustannukset rivi))])])))
                        naytettavat-rivit)
                  (when (not (empty? naytettavat-rivit))
                    (if (= konteksti :urakka)
                      ["Yhteensä" nil nil nil nil
                       (fmt/euro-opt (reduce + (keep :suunnitellut_kustannukset naytettavat-rivit)))
                       (fmt/euro-opt (reduce + (keep :toteutuneet_kustannukset naytettavat-rivit)))]
                      (flatten [(if urakoittain? ["Yhteensä" ""]
                                                 ["Yhteensä"])
                                nil
                                (fmt/desimaaliluku-opt (reduce + (keep :toteutunut_maara naytettavat-rivit)) 1)])))))]]))
