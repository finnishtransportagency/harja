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
        ;; FIXME Jostain syystä kannasta nostettu kuukausi / vuosi tulee desimaaliarvona, täytyy selvittää
        ;; mistä johtuu mutta toistaiseksi korjataan varmistamalla että ovat integerejä
        tehtavat-kuukausittain-summattuna (mapv (fn [tehtava]
                                                  (-> tehtava
                                                      (assoc :kuukausi (int (:kuukausi tehtava)))
                                                      (assoc :vuosi (int (:vuosi tehtava)))))
                                                tehtavat-kuukausittain-summattuna)
        ;; Muutetaan tehtävät muotoon, jossa jokainen tehtävä ensiintyy kerran ja kuukausittaiset
        ;; summat esitetään avaimina
        naytettavat-rivit (mapv (fn [tehtava]
                                  (let [taman-tehtavan-rivit (filter #(= (:nimi %) tehtava)
                                                                      tehtavat-kuukausittain-summattuna)
                                        kuukausittaiset-summat (reduce
                                                                 (fn [eka toka]
                                                                   (assoc eka
                                                                     (str (:vuosi toka) "/" (:kuukausi toka))
                                                                     (:toteutunut_maara  toka)))
                                                                 (first taman-tehtavan-rivit)
                                                                 taman-tehtavan-rivit)]
                                    (-> kuukausittaiset-summat
                                        (dissoc :kuukausi)
                                        (dissoc :vuosi))))
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
      (flatten [{:leveys "25%" :otsikko "Tehtävä"}
                {:leveys "10%" :otsikko "Yksikkö"}
                (mapv (fn [rivi]
                        {:otsikko (str (:vuosi rivi) "/" (:kuukausi rivi))})
                      listattavat-pvmt)
                {:leveys "15%" :otsikko "Määrä yhteensä"}
                {:leveys "15%" :otsikko "Tot-%"}
                {:leveys "15%" :otsikko "Suunniteltu määrä hoitokaudella"}])
      (mapv (fn [rivi]
              (flatten [(:nimi rivi)
                        (:yksikko rivi)
                        (mapv (fn [pvm]
                                (get rivi (str (:vuosi pvm) "/" (:kuukausi pvm))))
                              listattavat-pvmt)
                        ; (:toteutunut_maara rivi) TODO SUMMAA NÄMÄ OIKEIN
                        ; (:toteumaprosentti rivi)
                        (:suunniteltu_maara rivi)]))
            naytettavat-rivit)]]))

