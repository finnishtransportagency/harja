(ns harja.palvelin.raportointi.raportit.kulut-tehtavaryhmittain
  (:require [harja.kyselyt
             [hallintayksikot :as hallintayksikot-q]
             [tarkastukset :as tarkastukset-q]
             [urakat :as urakat-q]
             [laskut :as kulut-q]
             [aliurakoitsijat :as ali-q]
             [budjettisuunnittelu :as budjetti-q]
             [kustannusarvioidut-tyot :as kust-q]]
            [harja.pvm :as pvm]))


(defn- laske-hoitokaudet
  [alkupvm loppupvm urakka]
  (let [{urakan-alkupvm :alkupvm urakan-loppupvm :loppupvm} urakka]
    (when (and (pvm/sama-tai-ennen? loppupvm urakan-loppupvm true)
               (pvm/sama-tai-jalkeen? alkupvm urakan-alkupvm true)
               (pvm/ennen? alkupvm loppupvm))
      (loop [vuosi (pvm/vuosi urakan-alkupvm)
             monesko-hoitokausi 1
             hoitokaudet []]
        (let [hoitokaudet (if (pvm/sama-tai-ennen? alkupvm (pvm/hoitokauden-loppupvm (inc vuosi)) true)
                            (conj hoitokaudet monesko-hoitokausi)
                            hoitokaudet)]
          (if (pvm/ennen? loppupvm (pvm/hoitokauden-alkupvm (inc vuosi)))
            hoitokaudet
            (recur (inc vuosi)
                   (inc monesko-hoitokausi)
                   hoitokaudet)))))))

(defn- tavoitehinta-urakalle
  [db {:keys [urakka-id alkupvm loppupvm]} urakkatiedot]
  (let [hoitokaudet (laske-hoitokaudet alkupvm loppupvm urakkatiedot)
        tavoitteet (group-by :hoitokausi
                             (budjetti-q/hae-budjettitavoite
                               db
                               {:urakka urakka-id}))]
    (reduce #(-> tavoitteet
                 (get %2)
                 first
                 :tavoitehinta
                 (+ %1)) 0 hoitokaudet)))

(defn- kulut-urakalle
  [db {:keys [alkupvm urakka-id loppupvm]}]
  (let [kuukausi-valittu? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        hoitokausi-valittu? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
        alkupvm-valittu-kuu-tai-vali (cond
                                       hoitokausi-valittu? (pvm/kuukauden-ensimmainen-paiva (pvm/nyt))
                                       :default alkupvm)
        loppupvm-valittu-kuu-tai-vali (cond
                                        hoitokausi-valittu? (pvm/kuukauden-viimeinen-paiva (pvm/nyt))
                                        :default loppupvm)
        alkupvm-hoitokausi (pvm/hoitokauden-alkupvm (dec (pvm/vuosi loppupvm)))
        loppupvm-hoitokausi (pvm/hoitokauden-loppupvm (pvm/vuosi loppupvm))
        rivit-hoitokauden-alusta (mapv #(->
                                          [(:jarjestys %) (:nimi %) (:summa %)])
                                       (kulut-q/hae-urakan-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm-hoitokausi
                                                                                            :loppupvm loppupvm-hoitokausi
                                                                                            :urakka   urakka-id}))
        rivit-tassa-kuussa (mapv #(->
                                    [(:jarjestys %) (:nimi %) (:summa %)])
                                 (kulut-q/hae-urakan-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm-valittu-kuu-tai-vali
                                                                                      :loppupvm loppupvm-valittu-kuu-tai-vali
                                                                                      :urakka   urakka-id}))
        otsikot [{:leveys 1 :otsikko "Tehtäväryhmä"}
                 {:leveys 1 :otsikko (str "Hoitokauden alusta" (pvm/pvm alkupvm-hoitokausi) (pvm/pvm loppupvm-hoitokausi))}
                 {:leveys 1 :otsikko (cond
                                       kuukausi-valittu? (str (pvm/kuukauden-nimi (pvm/kuukausi alkupvm)) " " (pvm/vuosi alkupvm))
                                       :else (str "Jaksolla " (pvm/pvm alkupvm-valittu-kuu-tai-vali) "-" (pvm/pvm loppupvm-valittu-kuu-tai-vali)))}]
        ; [id otsikko summa]
        ; [id otsikko summa-hka summa-kk]
        ;  0  1       2         3
        rivit (loop [rivit-hoitokausi rivit-hoitokauden-alusta
                     rivit-kuukausi rivit-tassa-kuussa
                     kaikki {}]
                (let [rivi-hoitokausi (first rivit-hoitokausi)
                      rivi-kk (first rivit-kuukausi)]
                  (if (and (nil? rivi-hoitokausi)
                           (nil? rivi-kk))
                    kaikki
                    (recur (rest rivit-hoitokausi)
                           (rest rivit-kuukausi)
                           (as-> kaikki ks
                                 (if (not (nil? rivi-hoitokausi))
                                   (assoc ks (first rivi-hoitokausi) [(first rivi-hoitokausi)
                                                                      (second rivi-hoitokausi)
                                                                      (nth rivi-hoitokausi 2)
                                                                      (nth (get ks (first rivi-hoitokausi)) 3 0)])
                                   ks)
                                 (if (not (nil? rivi-kk))
                                   (assoc ks (first rivi-kk) [(first rivi-kk)
                                                              (second rivi-kk)
                                                              (nth (get ks (first rivi-kk)) 2 0)
                                                              (nth rivi-kk 2)])
                                   ks))))))
        rivit (sort-by first (mapv second rivit))
        yhteensa (reduce #(->
                            ["Yhteensä"
                             (+ (second %1) (nth %2 2))
                             (+ (nth %1 2) (nth %2 3))])
                         ["Yhteensä" 0 0]
                         rivit)
        muuta-pilkut-ja-poista-id (comp
                                    (map (fn [k]
                                           (rest
                                             (transduce
                                               (comp (map str)
                                                     (map #(clojure.string/replace % #"(\d+)\.(\d+)" "$1,$2")))
                                               conj
                                               k)))))
        rivit (into [] muuta-pilkut-ja-poista-id rivit)]
    {:otsikot  otsikot
     :yhteensa yhteensa
     :debug    ["HKA" rivit-hoitokauden-alusta "RTA" rivit-tassa-kuussa "APVMVKV" alkupvm-valittu-kuu-tai-vali "LPVKV" loppupvm-valittu-kuu-tai-vali "APVHK" alkupvm-hoitokausi "LPVHK" loppupvm-hoitokausi]
     :rivit    rivit}))

(defn- kulut-hallintayksikolle
  [db {:keys [hallintayksikko-id alkupvm loppupvm]}]
  (let [otsikot [{:leveys 1 :otsikko "Tehtäväryhmä"}
                 {:leveys 1 :otsikko "Hoitokauden alusta"}
                 {:leveys 1 :otsikko "Tässä kuussa"}]
        rivit (mapv
                #(->
                   [(:tehtavaryhma %) () (:summa %)])
                (kulut-q/hae-hallintayksikon-kulut-raporttiin-aikavalilla db {:alkupvm         alkupvm
                                                                              :loppupvm        loppupvm
                                                                              :hallintayksikko hallintayksikko-id}))]
    {:otsikot otsikot
     :rivit   rivit}))

(defn- kulut-koko-maalle
  [db {:keys [alkupvm loppupvm] :as opts}]
  (let [otsikot [{:leveys 1 :otsikko "Tehtäväryhmä"}
                 {:leveys 1 :otsikko "Hoitokauden alusta"}
                 {:leveys 1 :otsikko "Tässä kuussa"}]
        rivit (mapv
                #(->
                   [(:tehtavaryhma %) (:hallintayksikko %) (:summa %)])
                (let [hallintayksikoittain? (get opts "Hallintayksiköittäin eroteltuna?")]
                  (if hallintayksikoittain?
                    (kulut-q/hae-koko-maan-kulut-raporttiin-aikavalilla-hallintayksikoittain db {:alkupvm  alkupvm
                                                                                                 :loppupvm loppupvm})
                    (kulut-q/hae-koko-maan-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm
                                                                            :loppupvm loppupvm}))))]
    {:otsikot otsikot
     :rivit   rivit}))

(defn- kulut-tehtavaryhmittain
  [db {:keys [urakka-id hallintayksikko-id] :as opts}]
  (cond
    urakka-id (kulut-urakalle db opts)
    hallintayksikko-id (kulut-hallintayksikolle db opts)
    :default (kulut-koko-maalle db opts)))

(defn- hae-tavoitehinta
  [db {:keys [urakka-id alkupvm loppupvm] :as opts}]
  (cond
    urakka-id (tavoitehinta-urakalle db opts (first
                                               (urakat-q/hae-urakka db {:id urakka-id})))
    :default 0))

(defn suorita
  [db user {:keys [alkupvm loppupvm] :as parametrit}]
  (let [{:keys [otsikot rivit yhteensa debug]} (kulut-tehtavaryhmittain db parametrit)
        tavoitehinta (hae-tavoitehinta db parametrit)]
    [:raportti {:nimi "Kulut tehtäväryhmittäin"}
     [:otsikko (str "Kulut tehtäväryhmittäin ajalla " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]
     [:teksti (str "Rapsapapsa! " (pr-str parametrit)
                   " rivit " (pr-str rivit)
                   " th " (pr-str tavoitehinta))]
     [:teksti (str "DEBUG" (pr-str debug))]
     [:taulukko
      {}
      otsikot
      (conj rivit yhteensa)]
     [:teksti (str "Urakkavuoden alusta tav.hintaan kuuluvia: " (pr-str yhteensa))]
     [:teksti (str "Tavoitehinta: " tavoitehinta)]
     [:teksti (str "Jäljellä: " #_(- tavoitehinta yhteensa))]]))