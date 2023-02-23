(ns harja.palvelin.raportointi.raportit.tehtavamaarat
  "Tehtävämääräraportti"
  (:require [harja.kyselyt
             [urakat :as urakat-q]
             [tehtavamaarat :as tm-q]
             [hallintayksikot :as hallinta-q]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log])
  (:import (java.math RoundingMode)))

(def vemtr-elementit 5)
(def tm-elementit 4)

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
  [db kysely-fn {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (log/debug "hae-tehtavamaarat: saatiin alku/loppupvm:t" alkupvm loppupvm)
  (let [hoitokaudet (laske-hoitokaudet alkupvm loppupvm)
        vain-mhut? (parametrit "Vain MHUt ja HJU:t")]
    (kysely-fn
      db
      {:alkupvm alkupvm
       :loppupvm loppupvm
       :hoitokausi hoitokaudet
       :urakka urakka-id
       :hallintayksikko hallintayksikko-id
       :vain-mhut? vain-mhut?})))

(defn pyorista-kahteen-decimaaliin [arvo]
  (when (not (nil? arvo))
    (.setScale
      (with-precision 2 (bigdec arvo)) 2 RoundingMode/HALF_UP)))

(defn- laske-toteuma-% ;:TODO voisko olla sql:ssä?
  [rivi]
  (let [[_ _ suunniteltu toteuma toteutunut-materiaalimaara] rivi
        tehtava-rivi? (-> rivi
                        count
                        (> 1))
        toteuma-% (when tehtava-rivi?
                    (cond
                      (zero? toteuma) ""
                      (zero? suunniteltu) "!"
                      :default (.setScale (* 100 (with-precision 2 (/ toteuma suunniteltu))) 0 RoundingMode/HALF_UP)))
        suunniteltu (pyorista-kahteen-decimaaliin suunniteltu)
        toteuma (pyorista-kahteen-decimaaliin toteuma)
        rivi-toteumaprosentilla (filter some?
                                  (conj (into [] (take 2 rivi)) suunniteltu toteuma toteuma-% toteutunut-materiaalimaara))]

    rivi-toteumaprosentilla))

(defn- yksikko-lukujen-peraan
  [rivi]
  (let [[tehtava yksikko suunniteltu toteuma toteuma-% toteutunut-materiaalimaara] rivi
        tehtava-rivi? (> (count rivi) 1)
        rivi-yksikoilla (if-not tehtava-rivi?
                          rivi
                          (filter some?
                            (vec
                              [tehtava
                               (when suunniteltu [:arvo-ja-yksikko
                                                  {:arvo suunniteltu
                                                   :yksikko yksikko
                                                   :fmt :numero}])
                               (when toteuma [:arvo-ja-yksikko
                                              {:arvo toteuma
                                               :yksikko yksikko
                                               :fmt :numero}])
                               toteuma-% toteutunut-materiaalimaara])))]

    rivi-yksikoilla))

(defn- null->0
  [kvp]
  (let [[avain arvo] kvp]
    [avain (if (nil? arvo) 0M arvo)]))

(defn- null-arvot-nollaksi-rivilla
  [rivi]
  (into {} (map null->0 rivi)))

(defn- nayta-suunniteltu-jos-sama-yksikko-kuin-toteumalla
  [{:keys [yksikko suunniteltu suunnitteluyksikko] :as rivi}]
  (if (= 1 (count (keys rivi))) ;; Tarkistetaan onko väliotsikkorivi
    rivi
    (let [rivi (select-keys rivi [:nimi :toteuma :yksikko :toteutunut-materiaalimaara])]
      (if (= yksikko suunnitteluyksikko)
        (assoc rivi :suunniteltu suunniteltu)
        (assoc rivi :suunniteltu 0M)))))

(defn- ota-tarvittavat-arvot
  [m]
  (let [arvot (vals
                (select-keys m
                  [:nimi :yksikko :suunniteltu :toteuma :toteutunut-materiaalimaara]))]
    arvot))

(defn- muodosta-otsikot
  [vemtr? hyt m]
  (cond
    (and (= 1 (count (keys m)))
      (some #(= (first m) %) hyt))
    {:rivi (concat (vec m)
             (mapv (fn [_] "") ; luodaan tyhjiä soluja
               (take (if vemtr? vemtr-elementit tm-elementit)
                 (range)))) :korosta? true :lihavoi? true}

    (= 1 (count (keys m)))
    {:rivi (concat (vec m)
             (mapv (fn [_] "")
               (take (if vemtr? vemtr-elementit tm-elementit)
                 (range)))) :korosta-hennosti? true :lihavoi? true}

    :else (vec m)))

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
        urakan-hallintayksikko-tiedot (when urakka-id 
                                        (urakat-q/urakan-hallintayksikko db {:id urakka-id}))
        urakan-tiedot (when urakka-id 
                        (urakat-q/hae-urakka db {:id urakka-id}))
        hallintayksikko-tiedot (cond 
                              urakka-id 
                              (hallinta-q/hae-organisaatio db {:id (-> urakan-hallintayksikko-tiedot first :hallintayksikko-id)})
                              
                              (not (or urakka-id hallintayksikko-id))
                              (hallinta-q/hallintayksikot-ilman-geometriaa db)
                                  
                              hallintayksikko-id
                              (hallinta-q/hae-organisaatio db {:id hallintayksikko-id}))
        raportin-taustatiedot (into [] (keep identity) 
                                (mapcat identity 
                                  [urakan-hallintayksikko-tiedot
                                   urakan-tiedot
                                   hallintayksikko-tiedot]))
        suunnitellut-ryhmissa (->> parametrit
                                (hae-tehtavamaarat db kysely-fn))
        ;; Varmistetaan vielä, että kaikki tehtävät ovat oikeassa järjestyksessä
        suunnitellut-ryhmissa (into [] (sort-by (juxt :elynumero :toimenpide-jarjestys :jarjestys) suunnitellut-ryhmissa))
        kaikki (if (and urakka-id (not (empty? suunnitellut-ryhmissa))) [{:nimi (-> urakan-tiedot first :nimi)}] [])
        suunnitellut-valiotsikoineen (loop [rivit suunnitellut-ryhmissa
                                            toimenpiteet #{}
                                            hallintayksikot #{}
                                            kaikki kaikki]
                                       (if (empty? rivit)
                                         kaikki
                                         (let [rivi (first rivit)
                                               uusi-toimenpide? (not (contains? toimenpiteet
                                                                       (str (:toimenpide rivi) (:hallintayksikko rivi))))
                                               uusi-hallintayksikko? (not (contains? hallintayksikot (:hallintayksikko rivi)))
                                               hallintayksikko (:hallintayksikko rivi)
                                               toimenpide (:toimenpide rivi)
                                               ; luodaan väliotsikkoelementti
                                               kaikki-rivit (if (and (not urakka-id) uusi-hallintayksikko?) ; turha tieto jos urakalle haetaan
                                                              (conj kaikki
                                                                {:nimi (some #(when (= (:id %) hallintayksikko) (:nimi %)) hallintayksikko-tiedot)})
                                                              kaikki)
                                               kaikki-rivit (if uusi-toimenpide?
                                                              (conj kaikki-rivit
                                                                {:nimi toimenpide}) 
                                                              kaikki-rivit)
                                               kaikki-rivit (conj kaikki-rivit rivi)]
                                           (recur (rest rivit) ;; loput suunnitellut-ryhmissä
                                             (conj toimenpiteet (str (:toimenpide rivi) (:hallintayksikko rivi))) ;; Toimenpide set
                                             (conj hallintayksikot hallintayksikko) ;; Hallintayksikkö set
                                             kaikki-rivit)))) ;; Pidetään kirjaa kaikista riveistä, joita raporttiin laitetaan
        muodosta-rivi (comp
                        (map nayta-suunniteltu-jos-sama-yksikko-kuin-toteumalla)
                        (map null-arvot-nollaksi-rivilla)
                        (map ota-tarvittavat-arvot)
                        (map laske-toteuma-%)
                        (map yksikko-lukujen-peraan)
                        (map (partial vemtrille-puuttuvat-tyhjat-sarakkeet
                               vemtr?))
                        (map (partial muodosta-otsikot
                               vemtr?
                               (map :nimi raportin-taustatiedot))))
        rivit (into [] muodosta-rivi suunnitellut-valiotsikoineen)]
    {:rivit rivit
     :debug suunnitellut-ryhmissa
     :urakkatiedot (when urakka-id (-> urakan-tiedot first))
     :otsikot (take (if vemtr? 
                      vemtr-elementit 
                      tm-elementit)
                [{:otsikko "Tehtävä" :leveys 6}
                 {:otsikko (str "Suunniteltu 1.10." (-> hoitokaudet first)
                             " - 30.9." (-> hoitokaudet last inc))
                  :leveys 1 :fmt :numero}
                 {:otsikko "Toteuma" :leveys 1 :fmt :numero}
                 {:otsikko "Toteuma-%" :leveys 1 :fmt :prosentti-0desim}
                 {:otsikko "Toteutunut materiaali\u00ADmäärä" :leveys 1 :fmt :numero}])}))

(defn db-haku-fn
  [db params]
  (kombota-samat-tehtavat
    (tm-q/hae-tehtavamaarat-ja-toteumat-aikavalilla db params)))

(defn suorita
  [db user {:keys [alkupvm loppupvm] :as params}]
  (let [{:keys [otsikot rivit debug urakkatiedot]} (muodosta-taulukko db user db-haku-fn params)]
    [:raportti
     {:nimi (str "Tehtävämäärät " (:nimi urakkatiedot))}
     [:taulukko
      {:otsikko (str "Tehtävämäärät " (:nimi urakkatiedot) " ajalta " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))
       :sheet-nimi (str "Tehtävämäärät " (:nimi urakkatiedot) " " (pvm/pvm alkupvm) "-" (pvm/pvm loppupvm))}
      otsikot
      rivit]
     [:teksti (str "Mikäli suunnitellun määrän yksikkö on eri kuin saman tehtävän toteutuneen määrän yksikkö, näytetään tällä raportilla toteutunut määrä, mutta ei suunniteltua määrää. Tällaisia tehtäviä ovat esimerkiksi monet liukkaudentorjuntaan liittyvät työt, joihin Tehtävä- ja määräluettelossa suunnitellaan materiaalimääriä.")]
     [:teksti (str "Toteutuneita materiaalimääriä voi tarkastella materiaali- ja ympäristöraportilla.")]]))
