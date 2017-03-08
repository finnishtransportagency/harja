(ns harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]))

(defn- raportin-sarakkeet []
  [{:leveys 2 :otsikko "Kustannuslaji"}
   {:leveys 1 :otsikko "Hinta"}
   {:leveys 1 :otsikko "Indeksi"}
   {:leveys 1 :otsikko "Yhteensä"}])

(defn- raportin-rivit []
  [["Kokonaishintainen osa" 0 0 0]
   ["Yksikköhintainen osa" 0 0 0]
   ["Määrämuutokset" 0 0 0]
   ["Arvonvähennykset" 0 0 0]
   ["Bonukset" 0 0 0]
   ["Sakot" 0 0 0]])

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm tienumero urakkatyyppi] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-rivit []
        raportin-nimi "Laaduntarkastusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      (raportin-sarakkeet)
      (raportin-rivit)]]))
