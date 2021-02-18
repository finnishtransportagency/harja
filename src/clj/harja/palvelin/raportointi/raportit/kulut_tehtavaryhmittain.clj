(ns harja.palvelin.raportointi.raportit.kulut-tehtavaryhmittain
  (:require
    [clojure.string :as cstr]
    [harja.kyselyt
     [urakat :as urakat-q]
     [laskut :as kulut-q]
     [budjettisuunnittelu :as budjetti-q]]
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
    (when (every? #(contains? tavoitteet %1) hoitokaudet)
      (reduce #(-> tavoitteet
                   (get %2)
                   first
                   :tavoitehinta
                   (+ %1)) 0 hoitokaudet))))

(def piste->pilkku
  (comp (map str)
        (map #(cstr/replace % #"(\d+)\.(\d+)" "$1,$2"))))

(def muuta-pilkut-ja-poista-id
  (map (fn [k]
         (rest
           (transduce
             piste->pilkku
             conj
             k)))))

(defn- kulut-urakalle
  [db {:keys [alkupvm urakka-id loppupvm]}]
  (let [kuukausi-valittu? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        hoitokausi-valittu? (pvm/kyseessa-hoitokausi-vali? alkupvm loppupvm)
        alkupvm-valittu-kuu-tai-vali (cond
                                       hoitokausi-valittu? (pvm/kuukauden-ensimmainen-paiva alkupvm)
                                       :default alkupvm)
        loppupvm-valittu-kuu-tai-vali (cond
                                        hoitokausi-valittu? (pvm/kuukauden-viimeinen-paiva loppupvm)
                                        :default loppupvm)
        alkupvm-hoitokausi
        (cond
          hoitokausi-valittu?
          alkupvm

          (pvm/ennen? alkupvm
                      (pvm/hoitokauden-alkupvm
                        (pvm/vuosi alkupvm)))
          (-> alkupvm
              pvm/vuosi
              dec
              pvm/hoitokauden-alkupvm)

          (pvm/sama-tai-jalkeen? alkupvm
                                 (pvm/hoitokauden-alkupvm
                                   (pvm/vuosi alkupvm)))
          (-> alkupvm
              pvm/vuosi
              pvm/hoitokauden-alkupvm))

        loppupvm-hoitokausi
        (cond
          (and hoitokausi-valittu?
               (pvm/sama-tai-ennen? loppupvm (pvm/nyt))) loppupvm

          (and hoitokausi-valittu?
               (pvm/jalkeen? loppupvm (pvm/nyt))) (pvm/nyt)

          (pvm/jalkeen? loppupvm (pvm/nyt))
          (pvm/nyt)

          (pvm/sama-tai-ennen? loppupvm (pvm/nyt))
          loppupvm)

        kaikki-rivit-alusta (group-by :nimi
                                      (concat
                                        (kulut-q/hae-urakan-johto-ja-hallintokorvaus-raporttiin-aikavalilla db {:alkupvm  alkupvm-hoitokausi
                                                                                                                :loppupvm loppupvm-hoitokausi
                                                                                                                :urakka   urakka-id})
                                        (kulut-q/hae-urakan-hj-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm-hoitokausi
                                                                                                :loppupvm loppupvm-hoitokausi
                                                                                                :urakka   urakka-id})
                                        (kulut-q/hae-urakan-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm-hoitokausi
                                                                                             :loppupvm loppupvm-hoitokausi
                                                                                             :urakka   urakka-id})))
        kaikki-rivit-kuussa (group-by :nimi
                                      (concat
                                        (kulut-q/hae-urakan-johto-ja-hallintokorvaus-raporttiin-aikavalilla db {:alkupvm  alkupvm-valittu-kuu-tai-vali
                                                                                                                :loppupvm loppupvm-valittu-kuu-tai-vali
                                                                                                                :urakka   urakka-id})
                                        (kulut-q/hae-urakan-hj-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm-valittu-kuu-tai-vali
                                                                                                :loppupvm loppupvm-valittu-kuu-tai-vali
                                                                                                :urakka   urakka-id})
                                        (kulut-q/hae-urakan-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm-valittu-kuu-tai-vali
                                                                                             :loppupvm loppupvm-valittu-kuu-tai-vali
                                                                                             :urakka   urakka-id})))
        rivin-muodostaja (comp
                           (map #(let [summa (reduce +
                                                     0
                                                     (keep :summa (second %)))]
                                   (assoc (first (second %)) :summa summa)))
                           (map #(->
                                   [(:jarjestys %)
                                    (:nimi %)
                                    (:summa %)])))
        rivit-hoitokauden-alusta (into [] rivin-muodostaja kaikki-rivit-alusta)
        rivit-tassa-kuussa (into [] rivin-muodostaja kaikki-rivit-kuussa)

        otsikot [{:leveys 1 :otsikko "Tehtäväryhmä"}
                 {:leveys 1 :otsikko (str "Hoitokauden alusta " (pvm/pvm alkupvm-hoitokausi) "-" (pvm/pvm loppupvm-hoitokausi)) :tasaa :oikea}
                 {:leveys 1 :otsikko (cond
                                       kuukausi-valittu? (str (pvm/kuukauden-nimi (pvm/kuukausi alkupvm-valittu-kuu-tai-vali)) " " (pvm/vuosi alkupvm-valittu-kuu-tai-vali))
                                       :else (str "Jaksolla " (pvm/pvm alkupvm-valittu-kuu-tai-vali) "-" (pvm/pvm loppupvm-valittu-kuu-tai-vali))) :tasaa :oikea}]
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
                                                                      (or (nth rivi-hoitokausi 2) 0)
                                                                      (or (nth (get ks (first rivi-hoitokausi)) 3 0) 0)])
                                   ks)
                                 (if (not (nil? rivi-kk))
                                   (assoc ks (first rivi-kk) [(first rivi-kk)
                                                              (second rivi-kk)
                                                              (or (nth (get ks (first rivi-kk)) 2 0) 0)
                                                              (or (nth rivi-kk 2) 0)])
                                   ks))))))
        rivit (sort-by first (mapv second rivit))
        yhteensa (reduce #(->
                            ["Yhteensä"
                             (+ (second %1) (nth %2 2))
                             (+ (nth %1 2) (nth %2 3))])
                         ["Yhteensä" 0 0]
                         rivit)
        rivit (into [] muuta-pilkut-ja-poista-id rivit)]
    {:otsikot  otsikot
     :yhteensa yhteensa
     :rivit    rivit}))

(defn- hae-tavoitehinta
  [db {:keys [urakka-id alkupvm loppupvm] :as opts}]
  (cond
    urakka-id (tavoitehinta-urakalle db opts (first
                                               (urakat-q/hae-urakka db {:id urakka-id})))
    :default 0))

(defn suorita
  [db user {:keys [alkupvm loppupvm testiversio?] :as parametrit}]
  (let [{:keys [otsikot rivit yhteensa debug]} (kulut-urakalle db parametrit)
        tavoitehinta (hae-tavoitehinta db parametrit)
        yhteensa-hoitokauden-alusta (second yhteensa)]
    [:raportti {:nimi (str "Kulut tehtäväryhmittäin" (when testiversio? " - TESTIVERSIO"))}
     [:taulukko
      {:viimeinen-rivi-yhteenveto? true
       :otsikko                    (str "Kulut tehtäväryhmittäin ajalla " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))}
      otsikot
      (conj rivit (transduce piste->pilkku
                             conj
                             yhteensa))]
     [:taulukko
      {:otsikko                    "Urakkavuoden alusta"
       :viimeinen-rivi-yhteenveto? true}
      [{:leveys 1 :otsikko ""} {:leveys 1 :otsikko ""}]
      (map #(transduce piste->pilkku
                       conj
                       %)
           [["Urakkavuoden alusta tav.hintaan kuuluvia: " (str yhteensa-hoitokauden-alusta)]
            ["Tavoitehinta: " (if (some? tavoitehinta)
                                (str tavoitehinta)
                                "Tavoitehintaa ei saatu")]
            ["Jäljellä: " (if (some? tavoitehinta)
                            (str (- tavoitehinta yhteensa-hoitokauden-alusta))
                            "Tavoitehintaa ei saatu - jäljellä olevaa ei voitu laskea")]])]]))