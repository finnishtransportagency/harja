(ns harja.palvelin.raportointi.raportit.tiestotarkastus
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.yksikkohintaiset-tyot :as q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]))

(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (roolit/vaadi-rooli user "tilaajan kayttaja") ; FIXME Selvitä oikeudet
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        naytettavat-rivit [1 2 3]
        raportin-nimi "Tiestötarkastusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja   (if (empty? naytettavat-rivit) "Ei raportoitavia tarkastuksia.")}
      (flatten (keep identity [{:leveys "10%" :otsikko "Päivämäärä"}
                               {:leveys "5%" :otsikko "Klo"}
                               {:leveys "5%" :otsikko "Tie"}
                               {:leveys "5%" :otsikko "Aosa"}
                               {:leveys "5%" :otsikko "Aet"}
                               {:leveys "5%" :otsikko "Losa"}
                               {:leveys "10%" :otsikko "Tarkastaja"}
                               {:leveys "20%" :otsikko "Havainnot"}
                               {:leveys "10%" :otsikko "Kuvanumerot"}]))
      (mapv (fn [rivi]
              [1 2 3 4 5 6 7 8 9])
            naytettavat-rivit)]]))
