(ns harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto
  (:require [harja.kyselyt.urakat :as urakat-q]
            [jeesql.core :refer [defqueries]]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]))

(defqueries "harja/palvelin/raportointi/raportit/tiemerkinnan_kustannusyhteenveto.sql")

(defn- raportin-sarakkeet []
  [{:leveys 2 :otsikko "Kustannuslaji" :fmt :raha}
   {:leveys 1 :otsikko "Hinta" :fmt :raha}
   {:leveys 1 :otsikko "Indeksi" :fmt :raha}
   {:leveys 1 :otsikko "Yhteensä" :fmt :raha}])

(defn- raportin-rivit [db urakka-id]
  (let [{:keys [kokonaishintainen-osa yksikkohintainen-osa muut-tyot sakot bonukset]}
        (first (muodosta-tiemerkinnan-kustannusyhteenveto db {:urakkaid urakka-id}))
        yhteensa (+ kokonaishintainen-osa yksikkohintainen-osa muut-tyot sakot bonukset)]
    ;; TODO Mites indeksit? Aikaväli?
    [["Kokonaishintaiset työt" kokonaishintainen-osa 0 kokonaishintainen-osa]
     ["Yksikköhintaiset työt" yksikkohintainen-osa 0 yksikkohintainen-osa]
     ["Muut työt" muut-tyot 0 muut-tyot]
     ["Sakot" sakot 0 sakot]
     ["Bonukset" bonukset 0 bonukset]
     ["Yhteensä" yhteensa 0 yhteensa]]))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-rivit []
        raportin-nimi "Kustannusyhteenveto"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (if (empty? naytettavat-rivit) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi
                 :viimeinen-rivi-yhteenveto? true}
      (raportin-sarakkeet)
      (raportin-rivit db urakka-id)]]))
