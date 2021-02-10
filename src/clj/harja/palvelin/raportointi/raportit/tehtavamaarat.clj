(ns harja.palvelin.raportointi.raportit.tehtavamaarat
  "Tehtävämääräraportti"
  (:require [harja.kyselyt
             [urakat :as urakat-q]
             [tehtavamaarat :as tm-q]
             [toteumat :as toteuma-q]
             [hallintayksikot :as hallinta-q]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log])
  (:import (java.math RoundingMode)))

(defn- sama-tehtava-ja-ely?
  [e t]
  (and (= (:nimi e) (:nimi t))
       (= (:hallintayksikko e) (:hallintayksikko t))))

(defn laske-yhteen
  [e t]
  (assoc e :suunniteltu (+ (or (:suunniteltu e) 0) (or (:suunniteltu t) 0))
           :toteuma (+ (or (:toteuma e) 0) (or (:toteuma t) 0))
           :toteutunut-materiaalimaara (+ (or (:toteutunut-materiaalimaara e 0) 0)
                                          (or (:toteutunut-materiaalimaara t 0) 0))))

(defn kombota-samat-tehtavat
  ([rivit]
   (kombota-samat-tehtavat rivit {}))
  ([rivit {:keys [tarkistus-fn]}]
   (loop [rivit rivit
          kombottu []]
     (let [r (first rivit)
           tarkista (or tarkistus-fn
                        sama-tehtava-ja-ely?)]
       (if (not r)
         kombottu
         (let [r (or (some #(when (tarkista % r)
                              (laske-yhteen % r))
                           kombottu)
                     r)
               kombottu (filter #(not (tarkista % r))
                                kombottu)
               kombottu (apply conj kombottu (keep identity [r]))]
           (recur (rest rivit) kombottu)))))))

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
  (let [[_ _ suunniteltu toteuma toteutunut-materiaalimaara] rivi
        valiotsikko? (-> rivi
                         count
                         (> 1))
        toteuma-% (when valiotsikko?
                    (cond
                      (zero? toteuma) ""
                      (zero? suunniteltu) "!"
                      :default (* (.divide toteuma suunniteltu 2 RoundingMode/HALF_UP) 100)))
        rivi-toteumaprosentilla (filter some?
                                        (conj (into [] (take 4 rivi)) toteuma-% toteutunut-materiaalimaara))]

    rivi-toteumaprosentilla))

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
    (let [rivi (select-keys rivi [:nimi :toteuma :yksikko :toteutunut-materiaalimaara])]
      (if (= yksikko suunnitteluyksikko)
        (assoc rivi :suunniteltu suunniteltu)
        (assoc rivi :suunniteltu 0)))))

(defn- ota-tarvittavat-arvot
  [m]
  (vals
    (select-keys m
                 [:nimi :yksikko :suunniteltu :toteuma :toteutunut-materiaalimaara])))

(defn- muodosta-otsikot
  [vemtr? hyt m]
  (cond
    (and (= 1 (count (keys m)))
         (some #(= (first m) %) hyt))
    {:rivi (concat (vec m)
                   (mapv (fn [_] "")
                         (take (if vemtr? 5 6)
                               (range)))) :korosta? true :lihavoi? true}

    (= 1 (count (keys m)))
    {:rivi (concat (vec m)
                   (mapv (fn [_] "")
                         (take (if vemtr? 5 6)
                               (range)))) :korosta-hennosti? true :lihavoi? true}

    :else (vec m)))

(defn taustatiedot                                          ; härveli :D
  [db params & haut]
  (let [haut-muunnoksilla (mapv #(fn [db params acc]
                                   (let [db-fn (first %)
                                         muunnos-fn (second %)
                                         parametrit (muunnos-fn params acc)]
                                     (if parametrit
                                       (db-fn db parametrit)
                                       (db-fn db))))
                                haut)
        haku-fn (fn [db params] (reduce (fn [acc h]
                                          (let [haku (h db params acc)]
                                            (concat acc haku)))
                                        []
                                        haut-muunnoksilla))]
    (haku-fn db params)))

(defn- vemtrille-puuttuvat-tyhjat-sarakkeet
  [vemtr? r]
  (if (and vemtr?
           (> (count r) 1))
    (concat r [0])
    r))

(declare db-haku-fn)

(defn muodosta-taulukko
  [db user kysely-fn {:keys [alkupvm loppupvm urakka-id hallintayksikko-id] :as parametrit}]
  ;; (log/debug "muodosta-taulukko: parametrit" parametrit)
  (let [hoitokaudet (laske-hoitokaudet alkupvm loppupvm)
        vemtr? (not= kysely-fn db-haku-fn)
        raportin-taustatiedot (apply taustatiedot
                                     db
                                     parametrit
                                     (keep identity (vector
                                                      (when urakka-id
                                                        [urakat-q/urakan-hallintayksikko
                                                         (fn [ps _] {:id (get ps :urakka-id)})])
                                                      (when urakka-id
                                                        [urakat-q/hae-urakka
                                                         (fn [ps _] {:id (get ps :urakka-id)})])
                                                      (when urakka-id
                                                        [hallinta-q/hae-organisaatio
                                                         (fn [_ acc] {:id (get (first acc) :hallintayksikko-id)})])
                                                      (when hallintayksikko-id
                                                        [hallinta-q/hae-organisaatio
                                                         (fn [ps _] {:id (get ps :hallintayksikko-id)})])
                                                      (when (not (or urakka-id hallintayksikko-id))
                                                        [hallinta-q/hallintayksikot-ilman-geometriaa
                                                         (fn [_ _] nil)]))))
        suunnitellut-ryhmissa (->> parametrit
                                   (hae-tehtavamaarat db kysely-fn))

        suunnitellut-valiotsikoineen (keep identity
                                           (loop [rivit suunnitellut-ryhmissa
                                                  toimenpide nil
                                                  hallintayksikko nil
                                                  kaikki []]
                                             (if (empty? rivit)
                                               kaikki
                                               (let [rivi (first rivit)
                                                     uusi-toimenpide? (not= toimenpide (:toimenpide rivi))
                                                     uusi-hallintayksikko? (not= hallintayksikko (:hallintayksikko rivi))
                                                     hallintayksikko (if uusi-hallintayksikko?
                                                                       (:hallintayksikko rivi)
                                                                       hallintayksikko)
                                                     toimenpide (if uusi-toimenpide?
                                                                  (:toimenpide rivi)
                                                                  toimenpide)]
                                                 (recur (rest rivit)
                                                        toimenpide
                                                        hallintayksikko
                                                        (conj kaikki
                                                              (when uusi-hallintayksikko?
                                                                {:nimi (some #(when (= (:id %) hallintayksikko) (:nimi %)) raportin-taustatiedot)})
                                                              (when uusi-toimenpide?
                                                                {:nimi toimenpide})
                                                              rivi))))))
        muodosta-rivi (comp
                        (map nayta-vain-toteuma-suunnitteluyksikko-!=-yksikko)
                        (map null-arvot-nollaksi-rivilla)
                        (map ota-tarvittavat-arvot)
                        (map laske-toteuma-%)
                        (map (partial vemtrille-puuttuvat-tyhjat-sarakkeet
                                      vemtr?))
                        (map (partial muodosta-otsikot
                                      vemtr?
                                      (map :nimi raportin-taustatiedot))))
        rivit (into [] muodosta-rivi suunnitellut-valiotsikoineen)]
    {:rivit   rivit
     :debug   suunnitellut-ryhmissa
     :otsikot (take (if vemtr? 6 5)
                    [{:otsikko "Tehtävä" :leveys 6}
                     {:otsikko "Yksikkö" :leveys 1}
                     {:otsikko (str "Suunniteltu määrä "
                                    (if (> (count hoitokaudet) 1)
                                      (str "hoitokausilla 1.10." (-> hoitokaudet first) "-30.9." (-> hoitokaudet last inc))
                                      (str "hoitokaudella 1.10." (-> hoitokaudet first) "-30.9." (-> hoitokaudet first inc))))
                      :leveys  1 :fmt :numero}
                     {:otsikko "Toteuma" :leveys 1 :fmt :numero}
                     {:otsikko "Toteuma-%" :leveys 1}
                     {:otsikko "Toteutunut materiaalimäärä" :leveys 1}])}))

(defn db-haku-fn
  [db params]
  (kombota-samat-tehtavat
    (tm-q/hae-tehtavamaarat-ja-toteumat-aikavalilla db params)))

(defn suorita
  [db user {:keys [alkupvm loppupvm testiversio?] :as params}]
  (let [{:keys [otsikot rivit debug]} (muodosta-taulukko db user db-haku-fn params)]
    [:raportti
     {:nimi (str "Tehtävämäärät" (when testiversio? " - TESTIVERSIO"))}
     [:taulukko
      {:otsikko    (str "Tehtävämäärät ajalta " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm) (when testiversio? " - TESTIVERSIO"))
       :sheet-nimi (str "Tehtävämäärät " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))}
      otsikot
      rivit]
     [:teksti (str "Mikäli suunnitellun määrän yksikkö on eri kuin saman tehtävän toteutuneen määrän yksikkö, näytetään tällä raportilla toteutunut määrä, mutta ei suunniteltua määrää. Yksikkö-sarakkeessa näkyy tällöin toteutuneen määrän yksikkö. Tällaisia tehtäviä ovat esimerkiksi monet liukkaudentorjuntaan liittyvät työt, joihin Tehtävä- ja määräluettelossa suunnitellaan materiaalimääriä.")]
     [:teksti (str "Toteutuneita materiaalimääriä voi tarkastella materiaali- ja ympäristöraportilla.")]]))
