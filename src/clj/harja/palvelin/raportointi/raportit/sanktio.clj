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
  kantarivit ;; TODO
  )

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
        data-raportille (sanktiot-raportille kantarivit)
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
      [{:otsikko "Laji" :leveys "50%"}
       {:otsikko "Arvo" :leveys "50%"}]
      [["Muistutus" 2]
       ["Sakko A (€)" 1566]]]]))
