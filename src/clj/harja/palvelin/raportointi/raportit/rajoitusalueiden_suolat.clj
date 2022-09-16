(ns harja.palvelin.raportointi.raportit.rajoitusalueiden-suolat
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen]))

(defqueries "harja/palvelin/raportointi/raportit/rajoitusalueiden_suolat.sql")

(defn rivi-xf [rivi]
  {:lihavoi? nil
   :rivi
   (into []
     (concat
       [(:tie rivi)]
       [(:osoitevali rivi)]
       [[:teksti (:pohjavesialue rivi) {:rivita? true}]]
       [(:pituus rivi)]
       [(:ajoratojen_pituus rivi)]
       [[:arvo {:arvo (:formiaattitoteumat rivi)
                :jos-tyhja "-"
                :korosta-hennosti? false
                :desimaalien-maara 2
                :ryhmitelty? false}]]
       [[:arvo {:arvo (:formiaatit_t_per_ajoratakm rivi)
                :jos-tyhja "-"
                :korosta-hennosti? false
                :desimaalien-maara 2
                :ryhmitelty? false}]]
       [[:arvo {:arvo (:suolatoteumat rivi)
                :jos-tyhja "-"
                :korosta-hennosti? false
                :desimaalien-maara 2
                :ryhmitelty? false}]]
       [[:arvo {:arvo (:talvisuola_t_per_ajoratakm rivi)
                :jos-tyhja "-"
                :korosta-hennosti? true
                :desimaalien-maara 2
                :varoitus? (< (or (:suolarajoitus rivi) 9999) (or (:talvisuola_t_per_ajoratakm rivi) 0))
                :ryhmitelty? false}]]
       [[:arvo {:arvo (:suolarajoitus rivi)
                :jos-tyhja "-"
                :korosta-hennosti? false
                :desimaalien-maara 2
                :ryhmitelty? false}]]
       [(:formiaatti-teksti rivi)]))})

(defn sarakkeet []
  [{:leveys 0.8 :fmt :kokonaisluku :otsikko "Tie"}
   {:leveys 2.5 :fmt :teksti :otsikko "Osoiteväli"}
   {:leveys 2.5 :fmt :teksti :otsikko "Pohjavesialue (tunnus)" :jos-tyhja "-"}
   {:leveys 1.5 :fmt :numero :otsikko "Pituus (m)" :jos-tyhja "-"}
   {:leveys 1.5 :fmt :numero :otsikko "Pituus ajoradat (m)" :jos-tyhja "-"}
   {:leveys 1.5 :fmt :numero :otsikko "Formiaatit yhteensä (t)"}
   {:leveys 1.5 :fmt :numero :otsikko "Formiaatit (t/ajoratakm)" :jos-tyhja "-"}
   {:leveys 1.5 :fmt :numero :otsikko "Talvisuola yhteensä (t)" :jos-tyhja "-"}
   {:leveys 1.5 :fmt :numero :otsikko "Talvisuola (t/ajoratakm)" :jos-tyhja "-"}
   {:leveys 1.5 :fmt :numero :otsikko "Suolan käyttö\u00ADraja (t/km)"}
   {:leveys 3 :fmt :teksti :otsikko ""}])

(defn rajoitusalueet-taulukko [otsikko rivit]
  [:taulukko {:otsikko otsikko
              ;:viimeinen-rivi-yhteenveto? true
              :tyhja (if (empty? rivit) "Ei raportoitavia suolatoteumia.")}
   (sarakkeet)
   (map rivi-xf rivit)])

(defn hae-rajoitusalueet-suolatoteumien-kanssa [db urakka-id alkupvm loppupvm]
  ;; TODO: tänne tietokantahaku, joka palauttaa suunnilleen alla olevan tuloksen kaikille urakan rajoitusalueille
  (let [hakuparametrit {:hoitokauden-alkuvuosi (pvm/vuosi
                                                 (first
                                                   (pvm/paivamaaran-hoitokausi alkupvm)))
                        :alkupvm alkupvm
                        :loppupvm loppupvm
                        :urakka-id urakka-id}
        rajoitusalueet (suolarajoitus-kyselyt/hae-suolatoteumat-rajoitusalueittain db hakuparametrit)
        rajoitusalueet (mapv (fn [rivi]
                               (-> rivi
                                 ;; Muunna pohjavesualueet tekstiksi
                                 (assoc :pohjavesialue (str/join " " (mapv (fn [p]
                                                                             (str (:nimi p) " (" (:tunnus p) ")" "\n"))
                                                                       (:pohjavesialueet rivi))))
                                 ;; Rakenna osoiteväli
                                 (assoc :osoitevali (str
                                                      (str (:aosa rivi) " / " (:aet rivi))
                                                      " – "
                                                      (str (:losa rivi) " / " (:let rivi))))
                                 (assoc :formiaatti-teksti (if (:formiaatti? rivi)
                                                             "Käytettävä formiaattia"
                                                             ""))))
                         rajoitusalueet)
        _ (log/debug "Löydetyt rajoitusalueet: " rajoitusalueet)]
    rajoitusalueet))

(defn suorita [db user {:keys [urakka-id alkupvm loppupvm hallintayksikko-id] :as parametrit}]
  (log/debug "Rajoitusalueraportti :: suorita urakka_id=" urakka-id " alkupvm=" alkupvm " loppupvm=" loppupvm)
  (let [urakka (first (urakat-q/hae-urakka db urakka-id))
        rajoitusalueet (hae-rajoitusalueet-suolatoteumien-kanssa db urakka-id alkupvm loppupvm)
        raportin-nimi "Suolatoteumat urakkasopimuksen mukaisilla rajoitusalueilla"]
    (vec
      (concat
        [:raportti {:orientaatio :landscape
                    :nimi raportin-nimi}]
        (if (empty? rajoitusalueet)
          [:teksti yleinen/ei-tuloksia-aikavalilla-str]
          (conj [] (rajoitusalueet-taulukko (str (:nimi urakka) " " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm)) rajoitusalueet)))))))
