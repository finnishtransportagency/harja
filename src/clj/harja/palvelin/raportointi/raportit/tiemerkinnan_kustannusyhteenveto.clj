(ns harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto
  (:require [harja.kyselyt.urakat :as urakat-q]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(defqueries "harja/palvelin/raportointi/raportit/tiemerkinnan_kustannusyhteenveto.sql")

(defn- raportin-sarakkeet []
  [{:leveys 2 :otsikko "Kustannuslaji" :fmt :raha}
   {:leveys 1 :otsikko "Hinta" :fmt :raha}
   {:leveys 1 :otsikko "Yhteensä" :fmt :raha}])

(defn hae-raportin-tiedot [{:keys [db urakka-id alkupvm loppupvm]}]
  (let [alku-paiva (t/day (pvm/suomen-aikavyohykkeeseen (c/from-date alkupvm)))
        alku-kuukausi (t/month (pvm/suomen-aikavyohykkeeseen (c/from-date alkupvm)))
        alku-vuosi (t/year (pvm/suomen-aikavyohykkeeseen (c/from-date alkupvm)))
        loppu-paiva (t/day (pvm/suomen-aikavyohykkeeseen (c/from-date loppupvm)))
        loppu-kuukausi (t/month (pvm/suomen-aikavyohykkeeseen (c/from-date loppupvm)))
        loppu-vuosi (t/year (pvm/suomen-aikavyohykkeeseen (c/from-date loppupvm)))
        alkukuukauden-eka-paiva (t/day (t/first-day-of-the-month alku-vuosi alku-kuukausi))
        loppukuukauden-vika-paiva (t/day (t/last-day-of-the-month loppu-vuosi loppu-kuukausi))
        kokonaisia-kuukausia-aikavalina? (and (= alku-paiva alkukuukauden-eka-paiva)
                      (= loppu-paiva loppukuukauden-vika-paiva))
        {:keys [kokonaishintaiset-tyot yksikkohintaiset-toteumat yksikkohintaiset-suunnitellut-tyot
                muut-tyot arvonmuutokset indeksit sakot bonukset]}
        (first (hae-tiemerkinnan-kustannusyhteenveto db {:urakkaid urakka-id
                                                         :alkupvm alkupvm
                                                         :loppupvm loppupvm}))
        toteumat-yhteensa (+ (if kokonaisia-kuukausia-aikavalina? kokonaishintaiset-tyot 0)
                             yksikkohintaiset-toteumat
                             muut-tyot
                             arvonmuutokset
                             indeksit
                             sakot
                             bonukset)]

    {:kokonaishintaiset-tyot kokonaishintaiset-tyot
     :yksikkohintaiset-toteumat yksikkohintaiset-toteumat
     :yksikkohintaiset-suunnitellut-tyot yksikkohintaiset-suunnitellut-tyot
     :muut-tyot muut-tyot
     :arvonmuutokset arvonmuutokset
     :indeksit indeksit
     :sakot sakot
     :bonukset bonukset
     :toteumat-yhteensa toteumat-yhteensa
     :kokonaisia-kuukausia-aikavalina? kokonaisia-kuukausia-aikavalina?}))

(defn- muodosta-raportin-rivit [raportin-tiedot]
  (let [{:keys [kokonaishintaiset-tyot yksikkohintaiset-toteumat
                muut-tyot arvonmuutokset indeksit sakot bonukset
                toteumat-yhteensa kokonaisia-kuukausia-aikavalina?]} raportin-tiedot
        ei-kk-vali-viesti "Kokonaishintainen osa voidaan näyttää vain kokonaisille kuukausille"]

    [["Kokonaishintaiset työt"
      (if kokonaisia-kuukausia-aikavalina?
        kokonaishintaiset-tyot
        [:varillinen-teksti {:arvo ei-kk-vali-viesti :tyyli :virhe}])
      (if kokonaisia-kuukausia-aikavalina?
        kokonaishintaiset-tyot
        [:varillinen-teksti {:arvo "-" :tyyli :virhe}])]
     ["Yksikköhintaiset työt" yksikkohintaiset-toteumat yksikkohintaiset-toteumat]
     ["Muut työt" muut-tyot muut-tyot]
     ["Arvonmuutokset" arvonmuutokset arvonmuutokset]
     ["Indeksit" indeksit indeksit]
     ["Sakot" sakot sakot]
     ["Bonukset" bonukset bonukset]
     ["Yhteensä" toteumat-yhteensa toteumat-yhteensa]]))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        raportin-tiedot (hae-raportin-tiedot {:db db
                                              :urakka-id urakka-id
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm})
        raportin-rivit (muodosta-raportin-rivit raportin-tiedot)
        raportin-nimi "Kustannusyhteenveto"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  "toteutuneet kustannukset" alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (if (empty? raportin-rivit) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi
                 :viimeinen-rivi-yhteenveto? true}
      (raportin-sarakkeet)
      raportin-rivit]
     (let [yksikkohintaiset-suunnitellut-tyot (:yksikkohintaiset-suunnitellut-tyot raportin-tiedot)
           toteumat-yhteensa (:toteumat-yhteensa raportin-tiedot)]
       [:yhteenveto
        [["Suunnitellut yksikköhintaiset työt aikavälillä:" (fmt/euro-opt yksikkohintaiset-suunnitellut-tyot)]
         ["Toteumat aikavälillä:" (fmt/euro-opt toteumat-yhteensa)]
         ["Kaikki aikavälillä:" (fmt/euro-opt (+ toteumat-yhteensa
                                              yksikkohintaiset-suunnitellut-tyot))]]])]))
