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
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]))

(defqueries "harja/palvelin/raportointi/raportit/valitavoitteet.sql")

(defn muodosta-raportin-rivit [valitavoitteet]
  (let [ajoissa (fn [valitavoitteet]
                  (filter
                    (fn [valitavoite]
                      (and (:takaraja valitavoite)
                           (:valmis-pvm valitavoite)
                           (pvm/sama-tai-ennen? (c/from-date (:valmis-pvm valitavoite))
                                                (c/from-date (:takaraja valitavoite)))))
                    valitavoitteet))
        myohassa (fn [valitavoitteet]
                   (filter
                     (fn [valitavoite]
                       (and (:takaraja valitavoite)
                            (:valmis-pvm valitavoite)
                            (pvm/jalkeen? (c/from-date (:valmis-pvm valitavoite))
                                          (c/from-date (:takaraja valitavoite)))))
                     valitavoitteet))
        kesken (fn [valitavoitteet]
                 (filter
                   (fn [valitavoite]
                     (and (:takaraja valitavoite)
                          (pvm/ennen? (t/now) (c/from-date (:takaraja valitavoite)))
                          (not (:valmis-pvm valitavoite))))
                   valitavoitteet))
        toteumatta (fn [valitavoitteet]
                     (filter
                       (fn [valitavoite]
                         (and (:takaraja valitavoite)
                              (not (:valmis-pvm valitavoite))
                              (pvm/jalkeen? (t/now)
                                            (c/from-date (:takaraja valitavoite)))))
                       valitavoitteet))
        valitavoiterivi (fn [valitavoite]
                          [(:nimi valitavoite)
                           (pvm/pvm-opt (:takaraja valitavoite))
                           (pvm/pvm-opt (:valmis-pvm valitavoite))
                           (:valmis-kommentti valitavoite)])]
    (into [] (concat
               [{:otsikko "Ajoissa toteutuneet"}]
               (mapv valitavoiterivi (ajoissa valitavoitteet))
               [{:otsikko "Myöhässä toteutuneet"}]
               (mapv valitavoiterivi (myohassa valitavoitteet))
               [{:otsikko "Kesken"}]
               (mapv valitavoiterivi (kesken valitavoitteet))
               [{:otsikko "Toteutumatta jääneet"}]
               (mapv valitavoiterivi (toteumatta valitavoitteet))))))

(defn muodosta-otsikkorivit []
  [{:otsikko "Välitavoite" :leveys 10}
   {:otsikko "Takaraja" :leveys 5}
   {:otsikko "Valmistunut" :leveys 5}
   {:otsikko "Kommentti" :leveys 10}])

(defn suorita [db user {:keys [urakka-id] :as parametrit}]
  (let [konteksti :urakka
        valitavoitteet (hae-valitavoitteet db {:urakka urakka-id})
        otsikkorivit (muodosta-otsikkorivit)
        datarivit (muodosta-raportin-rivit valitavoitteet)
        raportin-nimi "Välitavoiteraportti"
        otsikko (str (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                     ", " raportin-nimi ", suoritettu " (fmt/pvm (pvm/nyt)))]
    [:raportti {:orientaatio :landscape
                :nimi raportin-nimi}
     [:taulukko {:otsikko otsikko
                 :tyhja (when (empty? datarivit) "Ei raportoitavia välitavoitteita.")
                 :sheet-nimi raportin-nimi}
      otsikkorivit
      datarivit]]))