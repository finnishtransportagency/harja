(ns harja.palvelin.raportointi.raportit.vemtr
  "Valtakunnallinen ja ELY-kohtainen määrätoteumaraportti"
  (:require [harja.kyselyt
             [vemtr :as vemtr-q]
             [tehtavamaarat :as tm-q]]
            [harja.palvelin.raportointi.raportit.tehtavamaarat :as tm-r]))

(defn laske-yhteen [groupattu]
  (let [tulos (reduce (fn [rivi r]
                        (assoc rivi :suunniteltu (+ (or (:suunniteltu r) 0) (or (:suunniteltu rivi) 0))
                                    :toteuma (+ (or (:toteuma r) 0) (or (:toteuma rivi) 0))
                                    :toteutunut-materiaalimaara (+ (or (:toteutunut-materiaalimaara r) 0) (or (:toteutunut-materiaalimaara rivi) 0))))
                      (first groupattu) (rest groupattu))]
    tulos))

(defn yhdistele-toimenpiteet-ja-tehtavat [e t]
  (let [groupit (group-by (juxt :hallintayksikko :toimenpide :nimi) (concat e t))
        yhteenlasketut (mapv
                         (fn [g]
                           (merge (laske-yhteen (second g))
                                  {:nimi (nth (first g) 2)
                                   :toimenipde (nth (first g) 1)
                                   :hallintayksikko (nth (first g) 0)}))
                         groupit) ]
    yhteenlasketut))

(defn hae-tm-combo [db params]
  (let [mhut (tm-q/hae-tehtavamaarat-ja-toteumat-aikavalilla db params)
        yht (vemtr-q/hae-yh-suunnitellut-ja-toteutuneet-aikavalilla db params)
        paluuarvo (sort-by (juxt :elynumero :toimenpide-jarjestys :jarjestys)
                           (yhdistele-toimenpiteet-ja-tehtavat mhut yht))]
    paluuarvo))

(defn suorita
  [db user params]
  (let [{:keys [otsikot rivit debug]} (tm-r/muodosta-taulukko db user hae-tm-combo params)]
    [:raportti
     {:nimi "Valtakunnallinen määrätoteumaraportti"}
     [:taulukko
      {:otsikko "Määrätoteumat ajalta "
       :sheet-nimi "Määrätoteumat"}
      otsikot
      rivit]]))
