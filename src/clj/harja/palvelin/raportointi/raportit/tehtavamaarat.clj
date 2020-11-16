(ns harja.palvelin.raportointi.raportit.tehtavamaarat
  "Tehtävämääräraportti"
  (:require [harja.kyselyt
             [urakat :as urakat-q]
             [tehtavamaarat :as tm-q]
             [toteumat :as toteuma-q]]
            [harja.pvm :as pvm])
  (:import (java.math RoundingMode)))

(defn- laske-hoitokaudet
  [alkupvm loppupvm]
  (let [alku-hoitokausi (if (pvm/ennen? alkupvm (pvm/hoitokauden-alkupvm (pvm/vuosi alkupvm)))
                          (dec (pvm/vuosi alkupvm))
                          (pvm/vuosi alkupvm))
        loppu-hoitokausi (if (pvm/ennen? loppupvm (pvm/hoitokauden-alkupvm (pvm/vuosi loppupvm)))
                           (dec (pvm/vuosi loppupvm))
                           (pvm/vuosi loppupvm))]
    (range alku-hoitokausi (inc loppu-hoitokausi))))

(defn- hae-tehtavamaarat
  [db {:keys [urakka-id hallintayksikko-id alkupvm loppupvm]}]
  (let [hoitokaudet (laske-hoitokaudet alkupvm loppupvm)]
    (cond
      hallintayksikko-id (tm-q/hae-hallintayksikon-tehtavamaarat-ja-toteumat-aikavalilla
                           db
                           {:alkupvm         alkupvm
                            :loppupvm        loppupvm
                            :hoitokausi      hoitokaudet
                            :hallintayksikko hallintayksikko-id})
      urakka-id (tm-q/hae-urakan-tehtavamaarat-ja-toteumat-aikavalilla
                  db
                  {:urakka     urakka-id
                   :alkupvm    alkupvm
                   :loppupvm   loppupvm
                   :hoitokausi hoitokaudet})
      :oletus (tm-q/hae-kaikki-tehtavamaarat-ja-toteumat-aikavalilla
                db
                {:alkupvm    alkupvm
                 :loppupvm   loppupvm
                 :hoitokausi hoitokaudet}))))

(defn- hae-toteumat
  []
  nil)

(defn- laske-toteuma-%
  [rivi]
  (let [[_ _ suunniteltu toteuma] rivi
        toteuma-% (when (-> rivi
                            count
                            (> 1))
                    (cond
                      (zero? toteuma) ""
                      (zero? suunniteltu) "!"
                      :oletus (* (.divide toteuma suunniteltu 4 RoundingMode/HALF_UP) 100)))]
    (keep identity
          (conj (into [] rivi) toteuma-%))))

(defn- null->0
  [kvp]
  (let [[avain arvo] kvp]
    [avain (if (nil? arvo) 0 arvo)]))

(defn- null-arvot-nollaksi-rivilla
  [rivi]
  (into {} (map null->0 rivi)))

(defn- nayta-vain-toteuma-suunnitteluyksikko-!=-yksikko
  [{:keys [yksikko suunniteltu suunnitteluyksikko] :as rivi}]
  (if (not= 1 (count (keys rivi)))
    (let [rivi (select-keys rivi [:nimi :toteuma :yksikko])]
      (if (= yksikko suunnitteluyksikko)
        (assoc rivi :suunniteltu suunniteltu)
        (assoc rivi :suunniteltu 0)))
    rivi))

(defn- string->valiotsikkorivi
  [asia]
  (if (string? asia) [{:nimi asia}] asia))

(defn- muodosta-taulukko
  [db user parametrit]
  (let [suunnitellut (->> parametrit
                          (hae-tehtavamaarat db)
                          (sort-by :jarjestys))
        suunnitellut (keep identity
                           (loop [rivit suunnitellut
                                  tehtavaryhma nil
                                  kaikki []]
                             (if (empty? rivit)
                               kaikki
                               (let [rivi (first rivit)
                                     uusi-tehtavaryhma? (not= tehtavaryhma (:tehtavaryhma rivi))
                                     tehtavaryhma (if uusi-tehtavaryhma?
                                                    (:tehtavaryhma rivi)
                                                    tehtavaryhma  )]
                                 (recur (rest rivit)
                                        tehtavaryhma
                                        (conj kaikki
                                              (when uusi-tehtavaryhma?
                                                {:nimi tehtavaryhma})
                                              rivi))))))
        muodosta-rivi (comp
                        (map nayta-vain-toteuma-suunnitteluyksikko-!=-yksikko)
                        (map null-arvot-nollaksi-rivilla)
                        (map #(vals
                                (select-keys %
                                             [:nimi :yksikko :suunniteltu :toteuma])))
                        (map laske-toteuma-%)
                        (map #(if (= 1 (count (keys %)))
                                {:rivi (conj (vec %) "" "" "" "") :korosta-hennosti? true :lihavoi? true}
                                (vec %))))
        rivit (into [] muodosta-rivi suunnitellut)]
    {:rivit   rivit
     :otsikot [{:otsikko "Tehtävä" :leveys 6}
               {:otsikko "Yksikkö" :leveys 1}
               {:otsikko "Suunniteltu määrä" :leveys 2 :fmt :numero}
               {:otsikko "Toteuma" :leveys 2 :fmt :numero}
               {:otsikko "Toteuma-%" :leveys 2}]}))

(defn suorita
  [db user params]
  (println params)
  (let [{:keys [otsikot rivit debug]} (muodosta-taulukko db user params)]
    [:raportti
     {:nimi "Tehtävämäärät"}
     #_[:teksti (str "helou" (pr-str rivit))]
     #_[:teksti (str "helou" (pr-str otsikot))]
     #_[:teksti (str "helou" (pr-str debug))]
     [:taulukko
      {:otsikko    "Tehtävämäärät ajalta "
       :sheet-nimi "löl"}
      otsikot
      rivit]]))