(ns harja.palvelin.raportointi.raportit.kulut-tehtavaryhmittain
  (:require [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.kyselyt.tarkastukset :as tarkastukset-q]
            [harja.kyselyt
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
    (println ">" urakkatiedot hoitokaudet tavoitteet)
    (reduce #(-> tavoitteet
                 (get %2)
                 first
                 :tavoitehinta
                 (+ %1)) 0 hoitokaudet)))

(defn- kulut-urakalle
  [db user {:keys [alkupvm urakka-id loppupvm]}]
  (let [otsikot [{:leveys 1 :otsikko "Tehtäväryhmä"}
                 {:leveys 1 :otsikko "Hoitokauden alusta"}
                 {:leveys 1 :otsikko "Tässä kuussa"}]
        alkupvm-tama-kuu (pvm/kuukauden-ensimmainen-paiva (pvm/nyt))
        loppupvm-tama-kuu (pvm/kuukauden-viimeinen-paiva (pvm/nyt))
        rivit-hoitokauden-alusta (mapv #(->
                                          [(:tehtavaryhma %) (:summa %)])
                                       (kulut-q/hae-urakan-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm
                                                                                            :loppupvm loppupvm
                                                                                            :urakka   urakka-id}))
        rivit-tassa-kuussa (mapv #(->
                                    [(:tehtavaryhma %) (:summa %)])
                                 (kulut-q/hae-urakan-kulut-raporttiin-aikavalilla db {:alkupvm  alkupvm-tama-kuu
                                                                                      :loppupvm loppupvm-tama-kuu
                                                                                      :urakka   urakka-id}))
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
                                                                (nth (get ks (first rivi-hoitokausi)) 2 0)])
                                   ks)
                                 (if (not (nil? rivi-kk))
                                   (assoc ks (first rivi-kk) [(first rivi-kk)
                                                                 (nth (get ks (first rivi-kk)) 1 0)
                                                                 (second rivi-kk)])
                                   ks))))))
        rivit (mapv second rivit)
        yhteensa 0 #_(reduce #(+ %1 (second %2)) 0 rivit)]
    {:otsikot  otsikot
     :yhteensa yhteensa
     :rivit    rivit}))

(defn- kulut-hallintayksikolle
  [db user {:keys [hallintayksikko-id alkupvm loppupvm]}]
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
  [db user {:keys [alkupvm loppupvm] :as opts}]
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
  [db user {:keys [urakka-id hallintayksikko-id] :as opts}]
  (cond
    urakka-id (kulut-urakalle db user opts)
    hallintayksikko-id (kulut-hallintayksikolle db user opts)
    :default (kulut-koko-maalle db user opts)))

(defn- hae-tavoitehinta
  [db user {:keys [urakka-id alkupvm loppupvm] :as opts}]
  (cond
    urakka-id (tavoitehinta-urakalle db opts (first
                                               (urakat-q/hae-urakka db {:id urakka-id})))
    :default 0))

(defn suorita
  [db user {:keys [alkupvm loppupvm] :as parametrit}]
  (let [{:keys [otsikot rivit yhteensa]} (kulut-tehtavaryhmittain db user parametrit)
        tavoitehinta (hae-tavoitehinta db user parametrit)]
    [:raportti {:nimi "Kulut tehtäväryhmittäin"}
     [:otsikko (str "Kulut tehtäväryhmittäin ajalla " (pvm/pvm alkupvm) " - " (pvm/pvm loppupvm))]
     [:teksti (str "Rapsapapsa! " (pr-str parametrit)
                   " rivit " (pr-str rivit)
                   " th " (pr-str tavoitehinta))]
     [:taulukko
      {}
      otsikot
      rivit]
     [:teksti (str "Yhteensä: " yhteensa)]
     [:teksti (str "Urakkavuoden alusta tav.hintaan kuuluvia: ")]
     [:teksti (str "Tavoitehinta: " tavoitehinta)]
     [:teksti (str "Jäljellä: " (- tavoitehinta yhteensa))]]))