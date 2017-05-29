(ns harja.palvelin.raportointi.raportit.vesivaylien-laskutusyhteenveto
  (:require [harja.kyselyt.urakat :as urakat-q]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(defn- raportin-sarakkeet []
  [{:leveys 2 :otsikko "Toimenpide / maksuera"}
   {:leveys 1 :otsikko "Maksuerät"}
   {:leveys 1 :otsikko "Tunnus"}])

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
     ["Indeksitarkistukset" indeksit indeksit]
     ["Sakot" sakot sakot]
     ["Bonukset" bonukset bonukset]
     ["Yhteensä" toteumat-yhteensa toteumat-yhteensa]]))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        raportin-tiedot []
        raportin-rivit []
        raportin-nimi "Laskutusyhteenveto"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  "laskutusyhteenveto" alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (if (empty? raportin-rivit) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi
                 :viimeinen-rivi-yhteenveto? true}
      (raportin-sarakkeet)
      raportin-rivit]]))
