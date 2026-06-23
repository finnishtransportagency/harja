(ns harja.palvelin.raportointi.raportit.lisatyo
  "Lisätyöraportti"
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/lisatyot_kyselyt.sql"
  {:positional? true})

(declare hae-urakan-lisatyot)

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm] :as parametrit}]
  (log/debug "Lisätyöraportti :: suorita urakka_id=" urakka-id " alkupvm=" alkupvm " loppupvm=" loppupvm
    " parametrit=" parametrit)
  (let [urakka (first (urakat-q/hae-urakka db urakka-id))
        otsikko "Lisätyöraportti"
        taulukon-otsikko "Lisätöiden kulukohdistukset"
        raportin-tiedot (hae-urakan-lisatyot db {:urakka urakka-id :alkupvm alkupvm :loppupvm loppupvm})]
    [:raportti {:nimi otsikko
                :otsikon-koko :keskikoko
                :raportin-yleiset-tiedot {:raportin-nimi otsikko
                                          :alkupvm alkupvm
                                          :loppupvm loppupvm}}
     [:teksti (str (:nimi urakka))]
     [:teksti (str (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]
     [:taulukko {:otsikko taulukon-otsikko
                 :sheet-nimi otsikko
                 :tyhja (when (empty? raportin-tiedot) "Ei laskuja aikavälille")
                 :viimeinen-rivi-yhteenveto? true}
      [{:otsikko "Laskun pvm" :leveys 3 :fmt :pvm}
       {:otsikko "Toimenpide" :leveys 5}
       {:otsikko "Lisätieto" :leveys 5}
       {:otsikko "Määrä (€)" :leveys 5 :fmt :raha}]
      (keep identity
        (conj (mapv #(yleinen/rivi (:erapaiva %)
                       (:toimenpide %)
                       (or (:lisatyon_lisatieto %) " ")
                       (:summa %))
                raportin-tiedot)
          (when (not (empty? raportin-tiedot))
            ["Lisätyöt yhteensä" "" "" (reduce + (map :summa raportin-tiedot))])))]]))
