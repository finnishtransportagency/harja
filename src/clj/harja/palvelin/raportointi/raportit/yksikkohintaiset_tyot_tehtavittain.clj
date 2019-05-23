(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-tehtavittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.yksikkohintaiset-tyot :as q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.domain.raportointi :refer [info-solu]]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot :as yks-hint-tyot]
            [clojure.string :as str]))

(defn hae-summatut-tehtavat-urakalle [db {:keys [urakka-id alkupvm loppupvm toimenpide-id suunnittelutiedot]}]
  (let [toteumat (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-urakalle db
                                                                               urakka-id alkupvm loppupvm
                                                                               (not (nil? toimenpide-id)) toimenpide-id)
        toteumat-suunnittelutiedoilla (yks-hint-tyot/liita-toteumiin-suunnittelutiedot alkupvm loppupvm toteumat suunnittelutiedot)]
    toteumat-suunnittelutiedoilla))

(defn hae-summatut-tehtavat-hallintayksikolle [db {:keys [hallintayksikko-id alkupvm loppupvm toimenpide-id urakoittain? urakkatyyppi]}]
  (if urakoittain?
    (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle-urakoittain db
                                                                                       hallintayksikko-id
                                                                                       (when urakkatyyppi (name urakkatyyppi))
                                                                                       alkupvm loppupvm
                                                                                       (not (nil? toimenpide-id)) toimenpide-id)
    (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-hallintayksikolle db
                                                                           hallintayksikko-id
                                                                           (when urakkatyyppi (name urakkatyyppi))
                                                                           alkupvm loppupvm
                                                                           (not (nil? toimenpide-id)) toimenpide-id)))

(defn hae-summatut-tehtavat-koko-maalle [db {:keys [alkupvm loppupvm toimenpide-id urakoittain? urakkatyyppi]}]
  (if urakoittain?
    (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle-urakoittain db
                                                                                 (when urakkatyyppi (name urakkatyyppi))
                                                                                 alkupvm loppupvm
                                                                                 (not (nil? toimenpide-id)) toimenpide-id)
    (q/hae-yksikkohintaiset-tyot-tehtavittain-summattuna-koko-maalle db
                                                                     (when urakkatyyppi (name urakkatyyppi))
                                                                     alkupvm loppupvm
                                                                     (not (nil? toimenpide-id)) toimenpide-id)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm toimenpide-id urakoittain? urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        suunnittelutiedot (when (= :urakka konteksti)
                            (yks-hint-tyot/suunnitellut-tehtavat db urakka-id))
        naytettavat-rivit (case konteksti
                            :urakka
                            (hae-summatut-tehtavat-urakalle db
                                                            {:urakka-id urakka-id
                                                             :alkupvm alkupvm
                                                             :loppupvm loppupvm
                                                             :toimenpide-id toimenpide-id
                                                             :suunnittelutiedot suunnittelutiedot})
                            :hallintayksikko
                            (hae-summatut-tehtavat-hallintayksikolle db
                                                                     {:hallintayksikko-id hallintayksikko-id
                                                                      :alkupvm alkupvm
                                                                      :loppupvm loppupvm
                                                                      :toimenpide-id toimenpide-id
                                                                      :urakoittain? urakoittain?
                                                                      :urakkatyyppi urakkatyyppi})
                            :koko-maa
                            (hae-summatut-tehtavat-koko-maalle db
                                                               {:alkupvm alkupvm
                                                                :loppupvm loppupvm
                                                                :toimenpide-id toimenpide-id
                                                                :urakoittain? urakoittain?
                                                                :urakkatyyppi urakkatyyppi}))
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
                                                 #{}))
                 :sheet-nimi raportin-nimi}
      (flatten (keep identity [(when urakoittain?
                                 {:leveys 25 :otsikko "Urakka"})
                               {:leveys 25 :otsikko "Tehtävä"}
                               {:leveys 5 :otsikko "Yks."}
                               (when (= konteksti :urakka)
                                 [{:leveys 10 :otsikko "Yksikkö\u00adhinta €" :fmt :raha}
                                  {:leveys 10 :otsikko "Suunniteltu määrä hoitokaudella" :fmt :numero}])
                               {:leveys 10 :otsikko "Toteutunut määrä" :fmt :numero}
                               (when (= konteksti :urakka)
                                 [{:leveys 15 :otsikko "Suunnitellut kustannukset hoitokaudella €" :fmt :raha}
                                  {:leveys 15 :otsikko "Toteutuneet kustannukset €" :fmt :raha}])]))
      (keep identity
            (conj (mapv (fn [rivi]
                          (keep identity [(when urakoittain?
                                            (or (:urakka_nimi rivi) (info-solu "")))
                                          (or (:nimi rivi) (info-solu ""))
                                          (or (:yksikko rivi) (info-solu ""))
                                          (when (= konteksti :urakka)
                                            (or (:yksikkohinta rivi) (info-solu "")))
                                          (when (= konteksti :urakka)
                                            (or (:suunniteltu_maara rivi) (info-solu "Ei suunnitelmaa")))
                                          (or (:toteutunut_maara rivi) 0)
                                          (when (= konteksti :urakka)
                                            (or (:suunnitellut_kustannukset rivi) (info-solu "")))
                                          (when (= konteksti :urakka)
                                            (or (:toteutuneet_kustannukset rivi) (info-solu "")))]))
                        (sort-by (juxt :urakka_nimi :nimi :yksikko) naytettavat-rivit))
                  (when (not (empty? naytettavat-rivit))
                    (if (= konteksti :urakka)
                      ["Yhteensä" nil nil nil nil
                       (reduce + (keep :suunnitellut_kustannukset naytettavat-rivit))
                       (reduce + (keep :toteutuneet_kustannukset naytettavat-rivit))]
                      (flatten [(if urakoittain? ["Yhteensä" ""]
                                                 ["Yhteensä"])
                                nil
                                (reduce + (keep :toteutunut_maara naytettavat-rivit))])))))]
     (yks-hint-tyot/suunnitelutietojen-nayttamisilmoitus konteksti alkupvm loppupvm suunnittelutiedot)]))
