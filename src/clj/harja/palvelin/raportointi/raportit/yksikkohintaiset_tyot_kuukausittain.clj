(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.yksikkohintaiset-tyot :as q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.domain.roolit :as roolit]))

(defn hae-tehtavat-urakalle [db {:keys [urakka-id alkupvm loppupvm toimenpide-id]}]
  (q/hae-yksikkohintaiset-tyot-kuukausittain-urakalle db
                                                      urakka-id alkupvm loppupvm
                                                      (if toimenpide-id true false) toimenpide-id))

(defn hae-tehtavat-hallintayksikolle [db {:keys [hallintayksikko-id alkupvm loppupvm toimenpide-id urakoittain?]}]
  (if urakoittain?
    (q/hae-yksikkohintaiset-tyot-kuukausittain-hallintayksikolle-urakoittain db
                                                                             hallintayksikko-id alkupvm loppupvm
                                                                             (if toimenpide-id true false) toimenpide-id)
    (q/hae-yksikkohintaiset-tyot-kuukausittain-hallintayksikolle db
                                                                 hallintayksikko-id alkupvm loppupvm
                                                                 (if toimenpide-id true false) toimenpide-id)))

(defn hae-tehtavat-koko-maalle [db {:keys [alkupvm loppupvm toimenpide-id urakoittain?]}]
  (if urakoittain?
    (q/hae-yksikkohintaiset-tyot-kuukausittain-koko-maalle-urakoittain db
                                                                       alkupvm loppupvm
                                                                       (if toimenpide-id true false) toimenpide-id)
    (q/hae-yksikkohintaiset-tyot-kuukausittain-koko-maalle db
                                                           alkupvm loppupvm
                                                           (if toimenpide-id true false) toimenpide-id)))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm toimenpide-id urakoittain?] :as parametrit}]
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        kuukausittaiset-summat (case konteksti
                                 :urakka
                                 (hae-tehtavat-urakalle db
                                                        {:urakka-id     urakka-id
                                                         :alkupvm       alkupvm
                                                         :loppupvm      loppupvm
                                                         :toimenpide-id toimenpide-id})
                                 :hallintayksikko
                                 (hae-tehtavat-hallintayksikolle db
                                                                 {:hallintayksikko-id hallintayksikko-id
                                                                  :alkupvm            alkupvm
                                                                  :loppupvm           loppupvm
                                                                  :toimenpide-id      toimenpide-id
                                                                  :urakoittain?       urakoittain?})
                                 :koko-maa
                                 (hae-tehtavat-koko-maalle db
                                                           {:alkupvm       alkupvm
                                                            :loppupvm      loppupvm
                                                            :toimenpide-id toimenpide-id
                                                            :urakoittain?  urakoittain?}))
        urakat-set (into #{} (map :urakka_nimi kuukausittaiset-summat))
        tehtavat-set (into #{} (map :nimi kuukausittaiset-summat))
        naytettavat-rivit (let [muodosta-tehtava-rivi (fn [tehtava-nimi urakka-nimi]
                                                        (let [taman-tehtavan-rivit (if urakka-nimi
                                                                                     (filter #(and (= (:nimi %) tehtava-nimi)
                                                                                                   (= (:urakka_nimi %) urakka-nimi))
                                                                                             kuukausittaiset-summat)
                                                                                     (filter #(= (:nimi %) tehtava-nimi)
                                                                                             kuukausittaiset-summat))
                                                              suunniteltu-maara (:suunniteltu_maara (first taman-tehtavan-rivit))
                                                              maara-yhteensa (reduce + (mapv :toteutunut_maara taman-tehtavan-rivit))
                                                              toteumaprosentti (if suunniteltu-maara
                                                                                 (fmt/desimaaliluku (float (with-precision 10 (* (/ maara-yhteensa suunniteltu-maara) 100))) 1)
                                                                                 "-")
                                                              kuukausittaiset-summat (reduce
                                                                                       (fn [map tehtava]
                                                                                         (assoc map
                                                                                           (pvm/kuukausi-ja-vuosi (c/to-date (t/local-date (:vuosi tehtava) (:kuukausi tehtava) 1)))
                                                                                           (or (:toteutunut_maara tehtava) 0)))
                                                                                       {}
                                                                                       taman-tehtavan-rivit)]
                                                          ;; Kasataan tehtävästä näytettävä rivi
                                                          (-> kuukausittaiset-summat
                                                              (assoc :urakka_nimi urakka-nimi)
                                                              (assoc :nimi tehtava-nimi)
                                                              (assoc :yksikko (:yksikko (first taman-tehtavan-rivit)))
                                                              (assoc :suunniteltu_maara suunniteltu-maara)
                                                              (assoc :toteutunut_maara maara-yhteensa)
                                                              (assoc :toteumaprosentti toteumaprosentti))))]
                            (if urakoittain?
                              ;; Käydään jokainen urakka läpi ja muutetaan sen tehtävät vectoriksi mappeja,
                              ;; jossa jokainen tehtävä ensiintyy kerran mapissa ja kuukausittaiset
                              ;; summat esitetään avaimina ("kuukausi/vuosi" on avain ja sen arvo on summa).
                              (flatten (mapv (fn [urakka-nimi]
                                               (mapv
                                                 (fn [tehtava] (muodosta-tehtava-rivi tehtava urakka-nimi))
                                                 tehtavat-set))
                                             urakat-set))
                              ;; Sama homma, mutta tarkastellaankin suoraan tehtäviä
                              (mapv
                                (fn [tehtava] (muodosta-tehtava-rivi tehtava nil))
                                tehtavat-set)))
        listattavat-pvmt (take-while (fn [pvm]
                                       ;; Nykyisen iteraation kk ei ole myöhempi kuin loppupvm:n kk
                                       (not (t/after?
                                              (t/local-date (t/year pvm)
                                                            (t/month pvm)
                                                            1)
                                              (t/local-date (t/year (c/from-date loppupvm))
                                                            (t/month (c/from-date loppupvm))
                                                            1))))
                                       (iterate (fn [pvm]
                                                  (t/plus pvm (t/months 1)))
                                                (c/from-date alkupvm)))
        raportin-nimi "Yksikköhintaiset työt kuukausittain"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")}
      (flatten (keep identity [(when urakoittain?
                                 {:leveys "20%" :otsikko "Urakka"})
                               {:leveys "20%" :otsikko "Tehtävä"}
                               {:leveys "5%" :otsikko "Yk\u00ADsik\u00ADkö"}
                               (mapv (fn [rivi]
                                       {:otsikko (pvm/kuukausi-ja-vuosi (c/to-date rivi))})
                                     listattavat-pvmt)
                               {:leveys "10%" :otsikko "Määrä yhteensä"}
                               (when (= konteksti :urakka)
                                 [{:leveys "5%" :otsikko "Tot-%"}
                                 {:leveys "10%" :otsikko "Suun\u00ADni\u00ADtel\u00ADtu määrä hoi\u00ADto\u00ADkau\u00ADdella"}])]))
      (mapv (fn [rivi]
              (flatten (keep identity [(when urakoittain?
                                         (:urakka_nimi rivi))
                                       (:nimi rivi)
                                       (:yksikko rivi)
                                       (mapv (fn [pvm]
                                               (or
                                                 (get rivi (pvm/kuukausi-ja-vuosi (c/to-date pvm)))
                                                 0))
                                             listattavat-pvmt)
                                       (:toteutunut_maara rivi)
                                       (when (= konteksti :urakka)
                                         [(:toteumaprosentti rivi)
                                         (:suunniteltu_maara rivi)])])))
            naytettavat-rivit)]]))

