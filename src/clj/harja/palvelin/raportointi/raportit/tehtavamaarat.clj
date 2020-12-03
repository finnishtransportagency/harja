(ns harja.palvelin.raportointi.raportit.tehtavamaarat
  "Tehtävämääräraportti"
  (:require [harja.kyselyt
             [urakat :as urakat-q]
             [tehtavamaarat :as tm-q]
             [toteumat :as toteuma-q]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log])
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
  [db kysely-fn {:keys [urakka-id hallintayksikko-id alkupvm loppupvm]}]
  (log/debug "hae-tehtavamaarat: saatiin alku/loppupvm:t" alkupvm loppupvm)
  (let [hoitokaudet (laske-hoitokaudet alkupvm loppupvm)]
    (kysely-fn
      db
      {:alkupvm         alkupvm
       :loppupvm        loppupvm
       :hoitokausi      hoitokaudet
       :urakka          urakka-id
       :hallintayksikko hallintayksikko-id})))

(defn- laske-toteuma-%                                      ;:TODO voisko olla sql:ssä?
  [rivi]
  (let [[_ _ suunniteltu toteuma] rivi
        valiotsikko? (-> rivi
                         count
                         (> 1))
        toteuma-% (when valiotsikko?
                    (cond
                      (zero? toteuma) ""
                      (zero? suunniteltu) "!"
                      :default (* (.divide toteuma suunniteltu 4 RoundingMode/HALF_UP) 100)))]
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
  (if-let [valiotsikkorivi? (= 1 (count (keys rivi)))]
    rivi
    (let [rivi (select-keys rivi [:nimi :toteuma :yksikko])]
      (if (= yksikko suunnitteluyksikko)
        (assoc rivi :suunniteltu suunniteltu)
        (assoc rivi :suunniteltu 0)))))

(defn- ota-tarvittavat-arvot
  [m]
  (vals
    (select-keys m
                 [:nimi :yksikko :suunniteltu :toteuma])))

(defn- muodosta-otsikot
  [m]
  (if (= 1 (count (keys m)))
    {:rivi (conj (vec m) "" "" "" "") :korosta-hennosti? true :lihavoi? true}
    (vec m)))

(defn muodosta-taulukko
  [db user kysely-fn {:keys [alkupvm loppupvm] :as parametrit}]
  (log/debug "muodosta-taulukko: parametrit" parametrit)
  (let [hoitokaudet (laske-hoitokaudet alkupvm loppupvm)
        suunnitellut-ryhmissa (->> parametrit
                                   (hae-tehtavamaarat db kysely-fn))
        suunnitellut-valiotsikoineen (keep identity
                                           (loop [rivit suunnitellut-ryhmissa
                                                  toimenpide nil
                                                  kaikki []]
                                             (if (empty? rivit)
                                               kaikki
                                               (let [rivi (first rivit)
                                                     uusi-toimenpide? (not= toimenpide (:toimenpide rivi))
                                                     toimenpide (if uusi-toimenpide?
                                                                  (:toimenpide rivi)
                                                                  toimenpide)]
                                                 (recur (rest rivit)
                                                        toimenpide
                                                        (conj kaikki
                                                              (when uusi-toimenpide?
                                                                {:nimi toimenpide})
                                                              rivi))))))
        muodosta-rivi (comp
                        (map nayta-vain-toteuma-suunnitteluyksikko-!=-yksikko)
                        (map null-arvot-nollaksi-rivilla)
                        (map ota-tarvittavat-arvot)
                        (map laske-toteuma-%)
                        (map muodosta-otsikot))
        rivit (into [] muodosta-rivi suunnitellut-valiotsikoineen)]
    {:rivit   rivit
     :otsikot [{:otsikko "Tehtävä" :leveys 6}
               {:otsikko "Yksikkö" :leveys 1}
               {:otsikko (str "Suunniteltu määrä "
                              (if (> (count hoitokaudet) 1)
                                (str "hoitokausilla 1.10." (-> hoitokaudet first) "-30.9." (-> hoitokaudet last inc))
                                (str "hoitokaudella 1.10." (-> hoitokaudet first) "-30.9." (-> hoitokaudet first inc))))
                :leveys  2 :fmt :numero}
               {:otsikko "Toteuma" :leveys 2 :fmt :numero}
               {:otsikko "Toteuma-%" :leveys 2}]}))

(defn suorita
  [db user {:keys [alkupvm loppupvm testiversio?] :as params}]
  (let [{:keys [otsikot rivit debug]} (muodosta-taulukko db user tm-q/hae-tehtavamaarat-ja-toteumat-aikavalilla params)]
    [:raportti
     {:nimi (str "Tehtävämäärät" (when testiversio? " - TESTIVERSIO"))}
     [:taulukko
      {:otsikko    (str "Tehtävämäärät ajalta " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm) (when testiversio? " - TESTIVERSIO"))
       :sheet-nimi (str "Tehtävämäärät " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))}
      otsikot
      rivit]
     [:teksti (str "Mikäli suunnitellun määrän yksikkö on eri kuin saman tehtävän toteutuneen määrän yksikkö, näytetään tällä raportilla toteutunut määrä, mutta ei suunniteltua määrää. Yksikkö-sarakkeessa näkyy tällöin toteutuneen määrän yksikkö. Tällaisia tehtäviä ovat esimerkiksi monet liukkaudentorjuntaan liittyvät työt, joihin Tehtävä- ja määräluettelossa suunnitellaan materiaalimääriä.")]
     [:teksti (str "Toteutuneita materiaalimääriä voi tarkastella materiaali- ja ympäristöraportilla.")]]))
