(ns harja.palvelin.raportointi.raportit.toimenpidekilometrit
  "Toimenpidekilometrit-raportti. Näyttää kuinka paljon kutakin kok. hint. työtä on tehty eri urakoissa."
  (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.pvm :as pvm]
            [harja.tyokalut.functor :refer [fmap]]
            [jeesql.core :refer [defqueries]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.hoitoluokat :as hoitoluokat]))

(defqueries "harja/palvelin/raportointi/raportit/toimenpidekilometrit.sql")

(defn suorita [db user {:keys [alkupvm loppupvm hoitoluokat urakka-id
                               hallintayksikko-id urakoittain?] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        hoitoluokat (or hoitoluokat
                        ;; Jos hoitoluokkia ei annettu, näytä kaikki (työmaakokous)
                        (into #{} (map :numero) hoitoluokat/talvihoitoluokat))
        talvihoitoluokat (filter #(hoitoluokat (:numero %)) hoitoluokat/talvihoitoluokat)
        parametrit {:urakka urakka-id
                    :hallintayksikko hallintayksikko-id
                    :alkupvm alkupvm
                    :loppupvm loppupvm
                    :hoitoluokat hoitoluokat}
        toimenpidekilometrit #_(hae-toimenpidekilometrit db parametrit urakoittain?) nil
        raportin-nimi "Toimenpidekilometrit"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        otsikkorivit (into [] (concat
                                [{:otsikko "Hoi\u00ADto\u00ADluok\u00ADka"}]
                                (map (fn [{:keys [nimi]}]
                                       {:otsikko nimi :tasaa :keskita})
                                     talvihoitoluokat)
                                [{:otsikko "" :sarakkeita 1}]))
        _ (log/debug "Otsikkorivit: " (pr-str otsikkorivit))
        datarivit [["123"]]]
    [:raportti {:nimi "Toimenpidekilometrit"
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko
                 :tyhja (if (empty? toimenpidekilometrit) "Ei raportoitavia tehtäviä.")
                 :rivi-ennen [{:teksti "" :sarakkeita 1}
                              {:teksti "Urakka 1" :sarakkeita (count talvihoitoluokat)}
                              {:teksti "Urakka 2" :sarakkeita (count talvihoitoluokat)}]
                 :sheet-nimi raportin-nimi}
      otsikkorivit
      datarivit]]))
