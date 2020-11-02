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
                      (zero? toteuma) "?"
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

(defn- valitse-materiaali-vai-ei
  [{:keys [toteuma materiaali yksikko materiaali-yksikko] :as rivi}]
  (if (not= 1 (count (keys rivi)))
    (let [rivi (select-keys rivi [:nimi :suunniteltu])]
      (assoc rivi
        :yksikko (if materiaali
                   materiaali-yksikko
                   yksikko)
        :toteuma (if materiaali
                   materiaali
                   toteuma)))
    rivi))

(defn- string->valiotsikkorivi
  [asia]
  (if (string? asia) [{:nimi asia}] asia))

(defn- muodosta-taulukko
  [db user parametrit]
  (let [suunnitellut (->> parametrit
                          (hae-tehtavamaarat db)
                          (sort-by :jarjestys)
                          (group-by :tehtavaryhma))
        muodosta-rivi (comp
                        cat
                        (map string->valiotsikkorivi)
                        cat
                        (map valitse-materiaali-vai-ei)
                        (map null-arvot-nollaksi-rivilla)
                        (map #(select-keys % [:nimi :yksikko :suunniteltu :toteuma]))
                        (map vals)
                        (map laske-toteuma-%))]
    {:rivit   (into [] muodosta-rivi suunnitellut)
     :otsikot [{:otsikko "Tehtävä" :leveys 6}
               {:otsikko "Yksikkö" :leveys 1}
               {:otsikko "Suunniteltu määrä" :leveys 2}
               {:otsikko "Toteuma" :leveys 2}
               {:otsikko "Toteuma-%" :leveys 2}]}))

(defn suorita
  [db user params]
  (println params)
  (let [{:keys [otsikot rivit]} (muodosta-taulukko db user params)]
    [:raportti
     {:nimi "Tehtävämäärät"}
     [:teksti (str "helou" (pr-str rivit))]
     [:teksti (str "helou" (pr-str otsikot))]
     [:taulukko
      {:otsikko "Tehtävämäärät ajalta "}
      otsikot
      rivit]]))