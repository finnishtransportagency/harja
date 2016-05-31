(ns harja.palvelin.raportointi.raportit.toimenpidekilometrit
  "Toimenpidekilometrit-raportti. Näyttää kuinka paljon kutakin kok. hint. työtä on tehty eri urakoissa."
  (:require [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.pvm :as pvm]
            [harja.tyokalut.functor :refer [fmap]]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.domain.hoitoluokat :as hoitoluokat]))

(defqueries "harja/palvelin/raportointi/raportit/toimenpidekilometrit.sql")

(defn suorita [db user {:keys [alkupvm loppupvm hoitoluokat urakka-id
                               hallintayksikko-id urakoittain?] :as parametrit}]
  (let [hoitoluokat (or hoitoluokat
                        ;; Jos hoitoluokkia ei annettu, näytä kaikki (työmaakokous)
                        (into #{} (map :numero) hoitoluokat/talvihoitoluokat))
        parametrit {:urakka urakka-id
                    :hallintayksikko hallintayksikko-id
                    :alkupvm alkupvm
                    :loppupvm loppupvm
                    :hoitoluokat hoitoluokat}
        toimenpidekilometrit #_(hae-toimenpidekilometrit db parametrit urakoittain?) nil]
    [:raportti {:nimi "Toimenpidekilometrit"
                :orientaatio :landscape}
     [{:otsikko "Teh\u00ADtä\u00ADvä" :leveys 10}]
     ["123"]]))
