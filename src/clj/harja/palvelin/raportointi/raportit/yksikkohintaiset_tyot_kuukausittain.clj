(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.yksikkohintaiset-tyot :refer [hae-yksikkohintaiset-tyot-per-kuukausi]]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm toimenpide-id] :as parametrit}]
  (log/debug "Muodostetaan yks. hint. kuukausiraportti urakalle " urakka-id " ja toimenpiteelle " toimenpide-id " aikaväliltä " (pr-str alkupvm loppupvm))
  (let [kuukausittaiset-summat (hae-yksikkohintaiset-tyot-per-kuukausi db
                                                             urakka-id alkupvm loppupvm
                                                             (if toimenpide-id true false) toimenpide-id)
        ;; Muutetaan tehtävät muotoon, jossa jokainen tehtävä ensiintyy kerran ja kuukausittaiset
        ;; summat esitetään avaimina
        naytettavat-rivit (mapv (fn [tehtava-nimi]
                                  (let [taman-tehtavan-rivit (filter #(= (:nimi %) tehtava-nimi)
                                                                     kuukausittaiset-summat)
                                        suunniteltu-maara (:suunniteltu_maara (first taman-tehtavan-rivit))
                                        maara-yhteensa (reduce + (mapv :toteutunut_maara taman-tehtavan-rivit))
                                        toteumaprosentti (if suunniteltu-maara
                                                           (format "%.1f" (with-precision 10 (* (/ maara-yhteensa suunniteltu-maara) 100)))
                                                           "-")
                                        kuukausittaiset-summat (reduce
                                                                 (fn [map tehtava]
                                                                   (assoc map
                                                                     (pvm/kuukausi-ja-vuosi (c/to-date (t/local-date (:vuosi tehtava) (:kuukausi tehtava) 1)))
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
                                (distinct (mapv :nimi kuukausittaiset-summat)))
        listattavat-pvmt (take-while (fn [pvm]
                                             (or (t/equal? pvm (c/from-date loppupvm))
                                                 (t/before? pvm (c/from-date loppupvm))))
                                           (iterate (fn [pvm]
                                                      (t/plus pvm (t/days 32)))
                                                    (c/from-date alkupvm)))
        raportin-nimi "Yksikköhintaiset työt kuukausittain"
        konteksti :urakka ;; myöhemmin tähänkin rapsaan voi tulla muitakin kontekseja, siksi alle yleistä koodia
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    ;:hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    ;:koko-maa "KOKO MAA"
                    )
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")}
      (flatten [{:leveys "20%" :otsikko "Tehtävä"}
                {:leveys "5%" :otsikko "Yk\u00ADsik\u00ADkö"}
                (mapv (fn [rivi]
                        {:otsikko (pvm/kuukausi-ja-vuosi (c/to-date rivi))})
                      listattavat-pvmt)
                {:leveys "10%" :otsikko "Määrä yhteensä"}
                {:leveys "5%" :otsikko "Tot-%"}
                {:leveys "10%" :otsikko "Suun\u00ADni\u00ADtel\u00ADtu määrä hoi\u00ADto\u00ADkau\u00ADdella"}])
      (mapv (fn [rivi]
              (flatten [(:nimi rivi)
                        (:yksikko rivi)
                        (mapv (fn [pvm]
                                (or
                                  (get rivi (pvm/kuukausi-ja-vuosi (c/to-date pvm)))
                                  0))
                              listattavat-pvmt)
                        (:toteutunut_maara rivi)
                        (:toteumaprosentti rivi)
                        (:suunniteltu_maara rivi)]))
            naytettavat-rivit)]]))

