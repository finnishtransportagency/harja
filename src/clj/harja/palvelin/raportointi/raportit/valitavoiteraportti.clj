(ns harja.palvelin.raportointi.raportit.valitavoiteraportti
  (:require [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.toimenpideinstanssit :refer [hae-urakan-toimenpideinstanssi]]
            [harja.fmt :as fmt]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko]]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.domain.roolit :as roolit]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]
            [clj-time.core :as t]
            [harja.pvm :as pvm]))

(defqueries "harja/palvelin/raportointi/raportit/valitavoitteet.sql")

(defn muodosta-raportin-rivit [valitavoitteet]
  (log/debug "Välitavoitteet: " (pr-str valitavoitteet))
  (mapv
    (fn [valitavoite]
      [(:nimi valitavoite)
       (pvm/pvm-opt (:takaraja valitavoite))
       (pvm/pvm-opt (:valmis-pvm valitavoite))
       (:valmis-kommentti valitavoite)])
      valitavoitteet))

(defn muodosta-otsikkorivit []
  [{:otsikko "Välitavoite"}
   {:otsikko "Takaraja"}
   {:otsikko "Valmistunut"}
   {:otsikko "Kommentti"}])

(defn suorita [db user {:keys [urakka-id] :as parametrit}]
  (let [konteksti :urakka
        valitavoitteet (hae-valitavoitteet db {:urakka urakka-id})
        otsikkorivit (muodosta-otsikkorivit)
        datarivit (muodosta-raportin-rivit valitavoitteet)
        raportin-nimi "Välitavoiteraportti"
        otsikko (str (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                     ", " raportin-nimi ", suoritettu " (fmt/pvm (pvm/nyt)))]
    [:raportti {:orientaatio :landscape
                :nimi        raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? datarivit) "Ei raportoitavia välitavoitteita.")
                 :sheet-nimi raportin-nimi}
      otsikkorivit
      datarivit]]))