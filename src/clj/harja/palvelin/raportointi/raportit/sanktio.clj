(ns harja.palvelin.raportointi.raportit.sanktio
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.pvm :as pvm]
            [harja.domain.materiaali :as materiaalidomain]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.kyselyt.konversio :as konv]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]))

(defqueries "harja/palvelin/raportointi/raportit/sanktiot.sql")

(defn sanktiot-raportille [kantarivit]
  [{:otsikko "Talvihoito"}
   ["Muistutukset" "kpl" 0]
   ["Sakko A" "€" 0]
   ["- Päätiet" "€" 0]
   ["- Muut tiet" "€" 0]
   ["Sakko B" "€" 0]
   ["- Päätiet" "€" 0]
   ["- Muut tiet" "€" 0]
   ["- Talvihoito, sakot yht." "€" 0]
   ["- Talvihoito, indeksit yht." "€" 0]
   {:otsikko "Muut tuotteet"}
   ["Muistutukset" "kpl" 0]
   ["Sakko A" "€" 0]
   ["- Liikenneymp. hoito" "€" 0]
   ["- Sorateiden hoito" "€" 0]
   ["Sakko B" "€" 0]
   ["- Liikenneymp. hoito" "€" 0]
   ["- Sorateiden hoito" "€" 0]
   ["- Muut tuotteet, sakot yht." "€" 0]
   ["- Muut tuotteet, indeksit yht." "€" 0]
   {:otsikko "Ryhmä C"}
   ["Ryhmä C, sakot yht." "€" 0]
   ["Ryhmä C, indeksit yht." "€" 0]
   {:otsikko "Yhteensä"}
   ["Muistutukset yht." "kpl" 0]
   ["Indeksit yht." "€" 0]
   ["Kaikki sakot yht." "€" 0]
   ["Kaikki yht" "€" 0]])

(defn suorita [db user {:keys [alkupvm loppupvm
                               urakka-id hallintayksikko-id
                               urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        kantarivit (hae-sanktiot db
                                 {:urakka urakka-id
                                  :hallintayksikko hallintayksikko-id
                                  :urakkatyyppi (when urakkatyyppi (name urakkatyyppi))
                                  :alku alkupvm
                                  :loppu loppupvm})
        raporttidata (sanktiot-raportille kantarivit)
        raportin-nimi "Sanktioraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio
                                                    db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]

    [:raportti {:nimi raportin-nimi
                :orientaatio :landscape}
     [:taulukko {:otsikko otsikko}
      [{:otsikko "Laji"    :leveys "55%"}
       {:otsikko "Yksikkö" :leveys "10%"}
       {:otsikko "Arvo"    :leveys "55%"}]
      raporttidata]]))
