(ns harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto
  (:require [harja.kyselyt.urakat :as urakat-q]
            [jeesql.core :refer [defqueries]]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]))

(defqueries "harja/palvelin/raportointi/raportit/tiemerkinnan_kustannusyhteenveto.sql")

(defn- raportin-sarakkeet []
  [{:leveys 2 :otsikko "Kustannuslaji" :fmt :raha}
   {:leveys 1 :otsikko "Hinta" :fmt :raha}
   ;; TODO Lisätään indeksit myöhemmin
   #_{:leveys 1 :otsikko "Indeksi" :fmt :raha}
   {:leveys 1 :otsikko "Yhteensä" :fmt :raha}])

(defn- raportin-rivit [db urakka-id alkupvm loppupvm]
  (let [alku-paiva (t/day (pvm/suomen-aikavyohykkeeseen  (c/from-date alkupvm)))
        alku-kuukausi (t/month (pvm/suomen-aikavyohykkeeseen  (c/from-date alkupvm)))
        alku-vuosi (t/year (pvm/suomen-aikavyohykkeeseen  (c/from-date alkupvm)))
        loppu-paiva (t/day (pvm/suomen-aikavyohykkeeseen  (c/from-date loppupvm)))
        loppu-kuukausi (t/month (pvm/suomen-aikavyohykkeeseen  (c/from-date loppupvm)))
        loppu-vuosi (t/year (pvm/suomen-aikavyohykkeeseen  (c/from-date loppupvm)))
        alkukuukauden-eka-paiva (t/day (t/first-day-of-the-month alku-vuosi alku-kuukausi))
        loppukuukauden-vika-paiva (t/day (t/last-day-of-the-month loppu-vuosi loppu-kuukausi))
        kk-vali? (and (= alku-paiva alkukuukauden-eka-paiva)
                      (= loppu-paiva loppukuukauden-vika-paiva))
        ei-kk-vali-viesti "Kokonaishintainen osa voidaan näyttää vain kokonaisille kuukausille"
        {:keys [kokonaishintainen-osa yksikkohintainen-osa muut-tyot sakot bonukset]}
        (first (muodosta-tiemerkinnan-kustannusyhteenveto db {:urakkaid urakka-id
                                                              :alkupvm alkupvm
                                                              :loppupvm loppupvm}))
        yhteensa (+ (if kk-vali? kokonaishintainen-osa 0)
                    yksikkohintainen-osa
                    muut-tyot
                    sakot
                    bonukset)]

    [["Kokonaishintaiset työt"
      (if kk-vali?
        kokonaishintainen-osa
        [:varillinen-teksti {:arvo ei-kk-vali-viesti :tyyli :virhe}])
      0
      (if kk-vali?
        kokonaishintainen-osa
        [:varillinen-teksti {:arvo "-" :tyyli :virhe}])]
     ["Yksikköhintaiset työt" yksikkohintainen-osa yksikkohintainen-osa]
     ["Muut työt" muut-tyot muut-tyot]
     ["Sakot" sakot sakot]
     ["Bonukset" bonukset bonukset]
     ["Yhteensä" yhteensa yhteensa]]))

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
      (raportin-rivit db urakka-id alkupvm loppupvm)]]))
