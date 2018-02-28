(ns harja.palvelin.raportointi.raportit.vastaanottotarkastus
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [harja.palvelin.raportointi.raportit.yllapidon-aikataulu :as yllapidon-aikataulu]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clj-time.core :as t]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.math :as math]))

(defn osat [raportti]
  ;; Pudotetaan pois :raportti keyword ja string tai map optiot.
  ;; Palautetaan vain sen jälkeen tulevat raporttielementit
  (remove nil?
          (mapcat #(if (and (seq? %) (not (vector? %)))
                     %
                     [%])
                  (drop 2 raportti))))

(defn suorita [db user {:keys [urakka-id] :as tiedot}]
  (let [konteksti :urakka
        raportin-nimi "Vastaanottotarkastus"
        otsikko (str (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                     ", " raportin-nimi ", suoritettu " (fmt/pvm (pvm/nyt)))
        otsikkorivit []
        datarivit []]
    [:raportti {:nimi raportin-nimi}
     (mapcat (fn [[aja-parametri otsikko raportti-fn]]
               (concat [[:otsikko otsikko]]
                       (osat (raportti-fn db user tiedot))))
             [[:yllapidon-aikataulu "Ylläpidon aikataulu" yllapidon-aikataulu/suorita]])]))