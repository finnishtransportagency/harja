(ns harja.palvelin.raportointi.raportit.paikkausten-yhteenveto-mhu
  (:require [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]))

(defqueries "harja/palvelin/raportointi/raportit/paikkausten_yhteenveto.sql")
(declare mhu-paikkausten-kustannukset-tehtavaryhmittain mhu-maarat-tehtavittain
  mhu-paikkausten-suunnitellut-kustannukset)

(defn tehtavaryhma-rivi-xf
  "Parsitaan tehtäväryhmädatasta raportille sopiva rivi."
  [rivi]
  {:lihavoi? (if (:yhteenveto rivi) true nil)
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

(defn tehtava-rivi-xf
  "Parsitaan tehtävädatasta raportille sopiva rivi."
  [rivi]
  {:lihavoi? nil
   :rivi
   (into []
     (concat
       [(:tehtava rivi)]
       [(:yksikko rivi)]
       [[:arvo-ja-yksikko-korostettu {:arvo (:suunniteltu rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:toteutunut rivi)
                                      :yksikko nil
                                      :desimaalien-maara 2
                                      :korosta-hennosti? true
                                      :ryhmitelty? true}]]))})

(defn yhteenveto-rivi-xf
  "Parsitaan yhteenvetodatasta raportille sopiva rivi."
  [rivi]
  {:lihavoi? true
   :rivi
   (into []
     (concat
       [[:arvo-ja-yksikko-korostettu {:arvo (:suunniteltu rivi)
                                      :yksikko "EUR"
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
                                      :ryhmitelty? true}]]
       [[:arvo-ja-yksikko-korostettu {:arvo (:toteutunut rivi)
                                      :yksikko "EUR"
                                      :desimaalien-maara 2
                                      :korosta-hennosti? false
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

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (log/debug "Paikkausten yhteenvetoraportti :: suorita urakka_id=" urakka-id " alkupvm=" alkupvm " loppupvm=" loppupvm
    " parametrit=" parametrit)
  (let [urakka (first (urakat-q/hae-urakka db urakka-id))
        hoitokauden-alkuvuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi alkupvm)))
        raportin-nimi "Paikkausten yhteenveto"

        ;; Haetaan suunnitellut kustannukset paikkauksille kustiksesta
        suunnitellut-kustannukset (:summa (first (mhu-paikkausten-suunnitellut-kustannukset db {:urakkaid urakka-id
                                                                                                :alkupvm alkupvm
                                                                                                :loppupvm loppupvm})))

        ;; Haetaan MHU urakan tehtäväryhmien kustannukset. Tehtäväryhmät, joille kustannukset haetaan on määritelty
        tehtavaryhmarivit (tehtavaryhmien-kustannukset-yhteensa db (:id urakka) alkupvm loppupvm)
        toteutuneet-kustannukset (reduce + 0 (map :summa tehtavaryhmarivit))
        ;; Lisää Yhteensä rivi
        tehtavaryhmarivit (conj tehtavaryhmarivit
                            {:tehtavaryhma "Yhteensä"
                             :summa toteutuneet-kustannukset
                             :yhteenveto true})
        ;; Haetaan määrät tehtävittäin
        tehtavarivit (tehtavien-maarat-yhteensa db (:id urakka) alkupvm loppupvm hoitokauden-alkuvuosi)

        raportti
        [:raportti {:nimi raportin-nimi
                    :otsikon-koko :keskikoko
                    :tiedot nil
                    :raportin-yleiset-tiedot {:raportin-nimi "Kustannusten seuranta"
                                              :urakka (:nimi urakka)
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm}}
         [:teksti (str (:nimi urakka) " " (pvm/vuosi (:alkupvm urakka)) "-" (pvm/vuosi (:loppupvm urakka)))]
         [:teksti (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]

         ;; Yhteenveto -taulukko suunnitellut ja toteutuneet kustannukset
         [:taulukko {:otsikko "Yhteenveto"
                     :oikealle-tasattavat-kentat #{}
                     :sheet-nimi "Yhteenveto suunnitellut ja toteutuneet"}
          [{:leveys 1 :otsikko "Suunnitellut paikkauskustannukset"}
           {:leveys 1 :otsikko "Toteutuneet paikkauskustannukset"}]
          [(yhteenveto-rivi-xf {:suunniteltu suunnitellut-kustannukset :toteutunut toteutuneet-kustannukset})]]

         ;; Kustannukset tehtäväryhmittäin
         [:taulukko {:otsikko "Kustannukset tehtäväryhmittäin"
                     :tyhja "Ei tehtäväryhmiä."
                     :oikealle-tasattavat-kentat #{1}
                     :sheet-nimi "Kustannukset tehtäväryhmittäin"}
          [{:leveys 6 :otsikko "Tehtäväryhmä"}
           {:leveys 1 :otsikko "Toteutunut (EUR)" :fmt :raha}]
          (map tehtavaryhma-rivi-xf tehtavaryhmarivit)]

         ;; Määrät tehtävittäin
         [:taulukko {:otsikko "Määrät tehtävittäin"
                     :tyhja "Ei tehtäviä."
                     :sheet-nimi "Määrät tehtävittäin"
                     :oikealle-tasattavat-kentat #{2 3}}
          [{:leveys 7 :otsikko "Tehtävät"}
           {:leveys 1 :otsikko "Yksikkö"}
           {:leveys 2 :otsikko "Suunniteltu määrä" :fmt :raha}
           {:leveys 2 :otsikko "Toteutunut määrä" :fmt :raha}]
          (map tehtava-rivi-xf tehtavarivit)]]]

    ;; Palautetaan raportti
    raportti))
