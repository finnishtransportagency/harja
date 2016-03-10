(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.yksikkohintaiset-tyot :refer [hae-yksikkohintaiset-tyot-per-paiva]]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clojure.string :as str]))

;; oulu au 2014 - 2019:
;; 1.10.2014-30.9.2015 elokuu 2015 kaikki
;;
;; Päivämäärä	Tehtävä	Yksikkö	Yksikköhinta	Suunniteltu määrä hoitokaudella	Toteutunut määrä	Suunnitellut kustannukset hoitokaudella	Toteutuneet kustannukset
;; 01.08.2015	Vesakonraivaus	ha	100,00 €	240	10	24 000,00 €	1 000,00 €
;; 19.08.2015	Vesakonraivaus	ha	100,00 €	240	10	24 000,00 €	1 000,00 €
;; 20.08.2015	Vesakonraivaus	ha	100,00 €	240	10	24 000,00 €	1 000,00 €
;; Yhteensä					72 000,00 €	3 000,00 €

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm toimenpide-id] :as parametrit}]
  (let [suunnittelutiedot (yks-hint-tyot/hae-urakan-hoitokaudet db urakka-id)
        toteumat (hae-yksikkohintaiset-tyot-per-paiva db
                                                      urakka-id alkupvm loppupvm
                                                      (not (nil? toimenpide-id)) toimenpide-id)
        naytettavat-rivit (yks-hint-tyot/liita-toteumiin-suunnittelutiedot
                                                   alkupvm
                                                   loppupvm
                                                   toteumat
                                                   suunnittelutiedot)
        raportin-nimi "Yksikköhintaiset työt päivittäin"
        konteksti :urakka
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka  (:nimi (first (urakat-q/hae-urakka db urakka-id))))
                  raportin-nimi alkupvm loppupvm)]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko                    otsikko
                 :viimeinen-rivi-yhteenveto? true
                 :tyhja                      (if (empty? naytettavat-rivit) "Ei raportoitavia tehtäviä.")
                 :oikealle-tasattavat-kentat #{3 6 7}}
      [{:leveys 10 :otsikko "Päivämäärä"}
       {:leveys 25 :otsikko "Tehtävä"}
       {:leveys 5 :otsikko "Yks."}
       {:leveys 10 :otsikko "Yksikkö\u00adhinta €"}
       {:leveys 10 :otsikko "Suunniteltu määrä hoitokaudella"}
       {:leveys 10 :otsikko "Toteutunut määrä"}
       {:leveys 15 :otsikko "Suunnitellut kustannukset hoitokaudella €"}
       {:leveys 15 :otsikko "Toteutuneet kustannukset €"}]

      (keep identity
            (conj (yleinen/ryhmittele-tulokset-raportin-taulukolle
                    naytettavat-rivit :toimenpide
                    (juxt (comp pvm/pvm :pvm)
                          (or :nimi "-")
                          (or :yksikko "-")
                          (comp #(let [formatoitu (fmt/euro-opt %)]
                                  (if-not (str/blank? formatoitu) formatoitu "-"))
                                :yksikkohinta)
                          (comp #(let [formatoitu (fmt/desimaaliluku-opt % 1)]
                                  (if-not (str/blank? formatoitu) formatoitu "Ei suunnitelmaa"))
                                  :suunniteltu_maara)
                          (comp #(let [formatoitu (fmt/desimaaliluku-opt % 1)]
                                  (if-not (str/blank? formatoitu) formatoitu 0))
                                :toteutunut_maara)
                          (comp #(let [formatoitu (fmt/euro-opt %)]
                                  (if-not (str/blank? formatoitu) formatoitu "-"))
                                :suunnitellut_kustannukset)
                          (comp #(let [formatoitu (fmt/euro-opt %)]
                                  (if-not (str/blank? formatoitu) formatoitu "-"))
                                :toteutuneet_kustannukset)))
                  (when (not (empty? naytettavat-rivit))
                    ["Yhteensä" nil nil nil nil nil
                     (fmt/euro-opt false (reduce + (keep :suunnitellut_kustannukset naytettavat-rivit)))
                     (fmt/euro-opt false (reduce + (keep :toteutuneet_kustannukset naytettavat-rivit)))])))]
     (yks-hint-tyot/suunnitelutietojen-nayttamisilmoitus konteksti alkupvm loppupvm suunnittelutiedot)]))

