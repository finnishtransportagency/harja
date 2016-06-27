(ns harja.palvelin.raportointi.raportit.valitavoiteraportti
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clj-time.core :as t]
            [harja.pvm :as pvm]))

(defn suorita [db user {:keys [urakka-id] :as parametrit}]
  (let [konteksti :urakka
        valitavoitteet [] ; TODO Hae
        taulukkorivit #_(muodosta-raportin-rivit valitavoitteet) [] ; TODO Käsittele
        raportin-nimi "Välitavoiteraportti"
        otsikko (str (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                     ", " raportin-nimi ", suoritettu " (fmt/pvm (pvm/nyt)))]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? taulukkorivit) "Ei raportoitavia välitavoitteita.")
                 :sheet-nimi raportin-nimi}
      [["Välitavoite"]]
      [["Sepon tien auraus"]]]]))