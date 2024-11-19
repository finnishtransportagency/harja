(ns harja.palvelin.raportointi.raportit.paikkausten-yhteenveto
  (:require [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]))

(defqueries "harja/palvelin/raportointi/raportit/paikkausten_yhteenveto.sql")
(declare mhu-paikkausten-kustannukset-tehtavaryhmittain mhu-maarat-tehtavittain)

(defn tehtavaryhma-rivi-xf [rivi]
  {:lihavoi? nil
   :korosta-hennosti? (:yhteenveto rivi)
   :rivi
   (into []
     (concat
       [(:tehtavaryhma rivi)]
       [[:arvo-ja-yksikko-korostettu {:arvo (:summa rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? true
                                      :ryhmitelty? true}]]))})

(defn tehtava-rivi-xf [rivi]
  {:lihavoi? nil
   :rivi
   (into []
     (concat
       [(:tehtava rivi)]
       [(:yksikko rivi)]
       [[:arvo-ja-yksikko-korostettu {:arvo (:suunniteltu rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? true
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:toteutunut rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? true
                                      :ryhmitelty? true}]]))})

(defn tehtavaryhmien-kustannukset-yhteensa [db urakka-id alkupvm loppupvm]
  ;; Muutetaan lista vectoriksi, jotta conj lisää yhteenvetorivin viimeiseksi eikä ensimmäiseksi.
  ;; List tyyppiselle collectionille on halvinta lisätä elementti ensimmäiseksi. Vectorissa viimeiseksi.
  (into [] (mhu-paikkausten-kustannukset-tehtavaryhmittain db {:urakkaid urakka-id
                                                               :alkupvm alkupvm
                                                               :loppupvm loppupvm})))

(defn tehtavien-maarat-yhteensa [db urakka-id alkupvm loppupvm hoitokauden-alkuvuosi]
  (mhu-maarat-tehtavittain db {:urakkaid urakka-id
                               :alkupvm alkupvm
                               :loppupvm loppupvm
                               :hoitokauden_alkuvuosi hoitokauden-alkuvuosi}))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm hallintayksikko-id] :as parametrit}]
  (log/debug "Paikkausten yhteenvetoraportti :: suorita urakka_id=" urakka-id " alkupvm=" alkupvm " loppupvm=" loppupvm)
  (let [urakka (first (urakat-q/hae-urakka db urakka-id))
        hoitokauden-alkuvuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi alkupvm)))
        raportin-nimi "Paikkausten yhteeveto"

        ;; Haetaan MHU urakan tehtäväryhmien kustannukset. Tehtäväryhmät, joille kustannukset haetaan on määritelty
        tehtavaryhmarivit (tehtavaryhmien-kustannukset-yhteensa db (:id urakka) alkupvm loppupvm)
        ;; Yhteensä rivi
        tehtavaryhmarivit (conj tehtavaryhmarivit
                            {:tehtavaryhma "Yhteensä"
                             :summa (reduce + 0 (map :summa tehtavaryhmarivit))
                             :yhteenveto true})
        tehtavarivit (tehtavien-maarat-yhteensa db (:id urakka) alkupvm loppupvm hoitokauden-alkuvuosi)
        raportti
        [:raportti {:nimi raportin-nimi}

         [:teksti "Jotain yhteenvetosettiä."]

         [:taulukko {:otsikko "Kustannukset tehtäväryhmittäin"
                     :tyhja "Ei tehtäväryhmiä."
                     :oikealle-tasattavat-kentat #{1}
                     :sheet-nimi raportin-nimi}
          [{:leveys 8 :otsikko "Tehtäväryhmä"}
           {:leveys 1 :otsikko "Toteutunut (EUR)" :fmt :raha}]
          (map tehtavaryhma-rivi-xf tehtavaryhmarivit)]
         [:taulukko {:otsikko "Määrät tehtävittäin"
                     :tyhja "Ei tehtäviä."
                     :sheet-nimi raportin-nimi
                     :oikealle-tasattavat-kentat #{2 3}}
          [{:leveys 8 :otsikko "Tehtävät"}
           {:leveys 1 :otsikko "Yksikkö"}
           {:leveys 2 :otsikko "Suunniteltu määrä" :fmt :raha}
           {:leveys 2 :otsikko "Toteutunut määrä" :fmt :raha}]
          (map tehtava-rivi-xf tehtavarivit)]]]
    raportti))
