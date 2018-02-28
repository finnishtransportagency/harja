(ns harja.palvelin.raportointi.raportit.vastaanottotarkastus
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.math :as math]))

(defn suorita [db user {:keys [urakka-id] :as parametrit}]
  (let [konteksti :urakka
        raportin-nimi "Vastaanottotarkastus"
        otsikko (str (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                     ", " raportin-nimi ", suoritettu " (fmt/pvm (pvm/nyt)))
        otsikkorivit []
        datarivit []]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? datarivit) "Ei raportoitavaa.")
                 :sheet-nimi raportin-nimi}
      otsikkorivit
      datarivit]]))