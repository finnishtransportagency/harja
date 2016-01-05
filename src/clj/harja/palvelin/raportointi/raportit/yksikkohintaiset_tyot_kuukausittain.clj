(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.yksikkohintaiset-tyot :refer [hae-yksikkohintaiset-tyot-per-kuukausi]]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]))

(defn pvm->str [pvm]
  (str (subs (str (:vuosi pvm)) 2 4) "/" (:kuukausi pvm)))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm toimenpide-id] :as parametrit}]
  (let [tehtavat-kuukausittain-summattuna (hae-yksikkohintaiset-tyot-per-kuukausi db
                                                               urakka-id alkupvm loppupvm
                                                               (if toimenpide-id true false) toimenpide-id)
        ;; Muutetaan tehtävät muotoon, jossa jokainen tehtävä ensiintyy kerran ja kuukausittaiset
        ;; summat esitetään avaimina
        naytettavat-rivit (mapv (fn [tehtava-nimi]
                                  (let [taman-tehtavan-rivit (filter #(= (:nimi %) tehtava-nimi)
                                                                      tehtavat-kuukausittain-summattuna)
                                        suunniteltu-maara (:suunniteltu_maara (first taman-tehtavan-rivit))
                                        maara-yhteensa (reduce + (mapv :toteutunut_maara taman-tehtavan-rivit))
                                        toteumaprosentti (if suunniteltu-maara
                                                           (format "%.2f" (with-precision 10 (/ maara-yhteensa suunniteltu-maara)))
                                                           "-")
                                        kuukausittaiset-summat (reduce
                                                                 (fn [map tehtava]
                                                                   (assoc map
                                                                     (pvm->str tehtava)
                                                                     (or (:toteutunut_maara tehtava) 0)))
                                                                 {}
                                                                 taman-tehtavan-rivit)]
                                    ;; Kasataan näytettävä rivi
                                    (-> kuukausittaiset-summat
                                        (assoc :nimi tehtava-nimi)
                                        (assoc :yksikko (:yksikko (first taman-tehtavan-rivit)))
                                        (assoc :suunniteltu_maara suunniteltu-maara)
                                        (assoc :toteutunut_maara maara-yhteensa)
                                        (assoc :toteumaprosentti toteumaprosentti))))
                                (distinct (mapv :nimi tehtavat-kuukausittain-summattuna)))
        listattavat-pvmt (distinct (mapv (fn [rivi]
                                           {:vuosi (:vuosi rivi) :kuukausi (:kuukausi rivi)})
                                         (sort-by #(vec (map % [:vuosi :kuukausi])) tehtavat-kuukausittain-summattuna)))
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
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")}
      (flatten [{:leveys "20%" :otsikko "Tehtävä"}
                {:leveys "10%" :otsikko "Yksikkö"}
                (mapv (fn [rivi]
                        {:otsikko (pvm->str rivi)})
                      listattavat-pvmt)
                {:leveys "10%" :otsikko "Määrä yhteensä"}
                {:leveys "10%" :otsikko "Tot-%"}
                {:leveys "10%" :otsikko "Suunniteltu määrä hoitokaudella"}])
      (mapv (fn [rivi]
              (flatten [(:nimi rivi)
                        (:yksikko rivi)
                        (mapv (fn [pvm]
                                (or
                                  (get rivi (pvm->str pvm))
                                  0))
                              listattavat-pvmt)
                        (:toteutunut_maara rivi)
                        (:toteumaprosentti rivi)
                        (:suunniteltu_maara rivi)]))
            naytettavat-rivit)]]))

