(ns harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-paivittain
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.yksikkohintaiset-tyot :refer [hae-yksikkohintaiset-tyot-per-paiva]]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [harja.domain.raportointi :refer [info-solu]]
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
  (let [suunnittelutiedot (yks-hint-tyot/suunnitellut-tehtavat db urakka-id)
        toteumat (hae-yksikkohintaiset-tyot-per-paiva db
                                                      {:urakka urakka-id
                                                       :alkupvm alkupvm
                                                       :loppupvm loppupvm
                                                       :rajaa_tpi (not (nil? toimenpide-id))
                                                       :tpi toimenpide-id})
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
                 :oikealle-tasattavat-kentat #{3 6 7}
                 :sheet-nimi raportin-nimi}
      [{:leveys 10 :otsikko "Päivämäärä"}
       {:leveys 25 :otsikko "Tehtävä"}
       {:leveys 5 :otsikko "Yks."}
       {:leveys 10 :otsikko "Yksikkö\u00adhinta €" :fmt :raha}
       {:leveys 10 :tasaa :oikea :otsikko "Suunniteltu määrä hoitokaudella" :fmt :numero}
       {:leveys 10 :tasaa :oikea :otsikko "Toteutunut määrä" :fmt :numero}
       {:leveys 15 :otsikko "Suunnitellut kustannukset hoitokaudella €" :fmt :raha}
       {:leveys 15 :otsikko "Toteutuneet kustannukset €" :fmt :raha}]

      (keep identity
            (conj (yleinen/ryhmittele-tulokset-raportin-taulukolle
                    naytettavat-rivit :toimenpide
                    (juxt (comp pvm/pvm :pvm)
                          #(or (:nimi %) (info-solu ""))
                          #(or (:yksikko %) (info-solu ""))
                          (comp #(or % (info-solu "")) :yksikkohinta)
                          (comp #(or % (info-solu "Ei suunnitelmaa")) :suunniteltu_maara)
                          (comp #(or % 0) :toteutunut_maara)
                          (comp #(or % (info-solu "")) :suunnitellut_kustannukset)
                          (comp #(or % (info-solu "")) :toteutuneet_kustannukset)))
                  (when (not (empty? naytettavat-rivit))
                    ["Yhteensä" nil nil nil nil nil nil
                     (reduce + (keep :toteutuneet_kustannukset naytettavat-rivit))])))]
     (yks-hint-tyot/suunnitelutietojen-nayttamisilmoitus konteksti alkupvm loppupvm suunnittelutiedot)]))
