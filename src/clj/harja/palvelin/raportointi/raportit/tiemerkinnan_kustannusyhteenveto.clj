(ns harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]))

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
      [{:leveys 4 :otsikko "Päivämäärä"}
       {:leveys 2 :otsikko "Klo"}
       {:leveys 2 :otsikko "Tie"}
       {:leveys 2 :otsikko "Aosa"}
       {:leveys 2 :otsikko "Aet"}
       {:leveys 2 :otsikko "Losa"}
       {:leveys 2 :otsikko "Let"}
       {:leveys 3 :otsikko "Tar\u00ADkas\u00ADtaja"}
       {:leveys 8 :otsikko "Mittaus"}
       {:leveys 10 :otsikko "Ha\u00ADvain\u00ADnot"}
       {:leveys 2 :otsikko "Laadun alitus"}
       {:leveys 3 :otsikko "Liit\u00ADteet" :tyyppi :liite}]
      []]]))
