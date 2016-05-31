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
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:nimi "Toimenpidekilometrit"
                :orientaatio :landscape}
     [:taulukko {:otsikko                    otsikko
                 :tyhja                      (if (empty? toimenpidekilometrit) "Ei raportoitavia tehtäviä.")
                 :sheet-nimi raportin-nimi}
     [{:otsikko "Teh\u00ADtä\u00ADvä" :leveys 10}]
     [["123"]]]]))
