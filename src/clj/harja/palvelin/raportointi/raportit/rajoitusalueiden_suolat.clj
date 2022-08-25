(ns harja.palvelin.raportointi.raportit.rajoitusalueiden-suolat
  (:require [clojure.string :as str]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen
             :refer [raportin-otsikko]]))

(defqueries "harja/palvelin/raportointi/raportit/rajoitusalueiden_suolat.sql")

(defn loppusumma [tulos]
  (do
    (log/debug "tulos: " tulos)
    (vec (concat tulos [{:tie "Yhteensä" :yhteensa (reduce + (map :talvisuola_yht tulos))}]))))

(defn rivi-xf [rivi]
  [(:tie rivi)
   (:osoitevali rivi)
   (:pohjavesialue rivi)
   (:pituus rivi)
   (:ajoratojen_pituus rivi)
   (:formiaattitoteumat rivi)
   (:formiaatit_t_per_ajoratakm rivi)
   (:suolatoteumat rivi)
   (:talvisuola_t_per_ajoratakm rivi)
   (:suolarajoitus rivi)
   (:formiaatti-teksti rivi)])

(defn sarakkeet []
  [{:leveys 1 :fmt :kokonaisluku :otsikko "Tie"}
   {:leveys 3 :fmt :teksti :otsikko "Osoiteväli"}
   {:leveys 2 :fmt :teksti :otsikko "Pohjavesialue (tunnus)"}
   {:leveys 2 :fmt :numero :otsikko "Pituus (m)"}
   {:leveys 2 :fmt :numero :otsikko "Pituus ajoradat (m)"}
   {:leveys 2 :fmt :numero :otsikko "Formiaatit yhteensä (t)"}
   {:leveys 2 :fmt :numero :otsikko "Formiaatit (t/ajoratakm)"}
   {:leveys 2 :fmt :numero :otsikko "Talvisuola yhteensä (t)"}
   {:leveys 2 :fmt :numero :otsikko "Talvisuola (t/ajoratakm)"}
   {:leveys 2 :fmt :numero :otsikko "Suolan käyttö\u00ADraja (t/km)"}
   {:leveys 2 :fmt :teksti :otsikko ""}])

(defn rajoitusalueet-taulukko [otsikko rivit]
  [:taulukko {:otsikko otsikko
              ;:viimeinen-rivi-yhteenveto? true
              :tyhja (if (empty? rivit) "Ei raportoitavia suolatoteumia.")}
   (sarakkeet)
   (into [] (map rivi-xf rivit #_(loppusumma rivit)))])

(defn serialize [m sep] (str (clojure.string/join sep (map (fn [[_ v]] v) m)) "\n"))

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
                                                                             (str (:nimi p) " (" (:tunnus p) ")"))
                                                                       (:pohjavesialueet rivi))))
                                 ;; Rakenna osoiteväli
                                 (assoc :osoitevali (str
                                                      (str (:aosa rivi) " / " (:aet rivi))
                                                      " – "
                                                      (str (:losa rivi) " / " (:let rivi)))))

                               ) rajoitusalueet)
        _ (println "rajoitusalueet: " rajoitusalueet)]
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
