(ns harja.palvelin.raportointi.raportit.vemtr
  "Valtakunnallinen ja ELY-kohtainen määrätoteumaraportti"
  (:require [harja.kyselyt
             [vemtr :as vemtr-q]
             [tehtavamaarat :as tm-q]]
            [harja.palvelin.raportointi.raportit.tehtavamaarat :as tm-r]
            [harja.pvm :as pvm])
  (:import (java.math RoundingMode)))


(defn- sama-tehtava-ja-ely?
  [e t]
  (and (= (:nimi e) (:nimi t))
       (= (:hallintayksikko e) (:hallintayksikko t))))

(defn laske-yhteen
  [e t]  
  (assoc e :suunniteltu (+ (or (:suunniteltu e) 0) (or (:suunniteltu t) 0))
           :toteuma (+ (or (:toteuma e) 0) (or (:toteuma t) 0))
           :toteutunut-materiaalimaara (+ (or (:toteutunut-materiaalimaara e) 0) (or (:toteutunut-materiaalimaara t) 0))))

(defn kombota-samat-tehtavat
  ([ekat tokat]
    (kombota-samat-tehtavat ekat tokat {}))
  ([ekat tokat {:keys [tarkistus-fn]}]
   (loop [ekat ekat
          tokat tokat
          kombottu []]
     (let [e (first ekat)
           t (first tokat)
           tarkista (or tarkistus-fn
                        sama-tehtava-ja-ely?)]
       (if (not (or e t))
         kombottu
         (let [e (or (some #(when (tarkista % e)
                              (laske-yhteen % e))
                           kombottu)
                     (when (tarkista e t)
                       (laske-yhteen e t))
                     e)
               t (or (some #(when (tarkista % t)
                              (laske-yhteen % t))
                           kombottu)
                     (when-not (tarkista e t)
                       t))
               kombottu (filter #(not (or (tarkista % e)
                                          (tarkista % t)))
                                kombottu)
               kombottu (apply conj kombottu (keep identity [e t]))]
           (recur (rest ekat) (rest tokat) kombottu)))))))

(defn hae-tm-combo [db params]
  (let [combo-fn
        (juxt tm-q/hae-tehtavamaarat-ja-toteumat-aikavalilla
              vemtr-q/hae-yh-suunnitellut-ja-toteutuneet-aikavalilla)
        [mhut yht :as kaksi-tulosjoukkoa] (combo-fn db params)        
        paluuarvo (sort-by (juxt :hallintayksikko :toimenpide-jarjestys :jarjestys) (kombota-samat-tehtavat mhut yht))]
    paluuarvo))

(defn suorita
  [db user params]
  (let [{:keys [otsikot rivit debug]} (tm-r/muodosta-taulukko db user hae-tm-combo params)]
    [:raportti
     {:nimi "Valtakunnallinen määrätoteumaraportti"}
     [:taulukko
      {:otsikko    "Määrätoteumat ajalta "
       :sheet-nimi "Määrätoteumat"}
      otsikot
      rivit]]))
