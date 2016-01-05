(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.yksikkohintaiset-tyot :refer [hae-yksikkohintaiset-tyot-per-kuukausi]]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm toimenpide-id] :as parametrit}]
  (let [tehtavat-kuukausittain-summattuna (hae-yksikkohintaiset-tyot-per-kuukausi db
                                                               urakka-id alkupvm loppupvm
                                                               (if toimenpide-id true false) toimenpide-id)
        naytettavat-rivit (mapv (fn [tehtava]
                                  (let [taman-tehtavan-rivit (filter #(= (:nimi %) tehtava)
                                                                      tehtavat-kuukausittain-summattuna)
                                        kuukausittaiset-summat (reduce
                                                                 (fn [eka toka]
                                                                   (assoc eka
                                                                     (str (int (:kuukausi toka)) "/" (int (:vuosi toka)))
                                                                     (:toteutunut_maara  toka)))
                                                                 (first taman-tehtavan-rivit)
                                                                 taman-tehtavan-rivit)]
                                    (-> kuukausittaiset-summat
                                        (dissoc :kuukausi)
                                        (dissoc :vuosi))))
                                (distinct (mapv :nimi tehtavat-kuukausittain-summattuna)))
        raportin-nimi "Yksikköhintaiset työt kuukausittain"
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
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")}
      [{:leveys "25%" :otsikko "Tehtävä"}
       {:leveys "10%" :otsikko "Yksikkö"}
       {:leveys "15%" :otsikko "Määrä yhteensä"}
       {:leveys "15%" :otsikko "Tot-%"}
       {:leveys "15%" :otsikko "Suunniteltu määrä hoitokaudella"}]

      (mapv (juxt :nimi
                  :yksikko
                  :toteutunut_maara
                  :toteumaprosentti
                  :suunniteltu_maara)
            naytettavat-rivit)]]))

