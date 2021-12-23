(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.ui.viesti :as viesti]
            [reagent.core :as r]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]))

(defrecord ValitseTaso [arvo taso])
(defrecord HaeTehtavat [parametrit])
(defrecord TehtavaHakuOnnistui [tehtavat parametrit])
(defrecord HakuEpaonnistui [])
(defrecord MaaraHakuOnnistui [maarat prosessoi-tulokset])
(defrecord TehtavaTallennusOnnistui [vastaus])
(defrecord TehtavaTallennusEpaonnistui [vastaus])
(defrecord TallennaTehtavamaara [tehtava])
(defrecord HaeMaarat [parametrit])
(defrecord SamatTulevilleMoodi [samat?])

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(defn maarille-tehtavien-tiedot
  [maarat-map {:keys [hoitokauden-alkuvuosi tehtava-id maara]}]
  (if-not (nil? hoitokauden-alkuvuosi)
    (assoc-in maarat-map [tehtava-id hoitokauden-alkuvuosi] maara)
    maarat-map))

(defn- map->id-map-maaralla
  [maarat hoitokausi rivi]
  [(:id rivi) (assoc rivi 
                :hoitokausi hoitokausi
                :maara (get-in maarat [(:id rivi) hoitokausi]))])

(defn muodosta-atomit 
  [tehtavat-ja-toimenpiteet valinnat maarat-tehtavilla]
  (into [] (comp 
             (filter (fn [{:keys [id]}] 
                       (let [valittu-toimenpide (-> valinnat :toimenpide :id)] 
                         (or 
                           (= :kaikki valittu-toimenpide) 
                           (= id valittu-toimenpide))))) 
             (map (fn [{:keys [nimi tehtavat]}]
                    {:nimi nimi 
                     :atomi (r/atom 
                              (into 
                                {} 
                                (map (r/partial map->id-map-maaralla maarat-tehtavilla (:hoitokausi valinnat))) 
                                tehtavat))})))
    tehtavat-ja-toimenpiteet))

(def valitason-toimenpiteet
  (filter
    (fn [data]
      (= 3 (:taso data)))))

(extend-protocol tuck/Event
  TehtavaTallennusEpaonnistui
  (process-event
    [_ app]
    (viesti/nayta! "Tallennus epäonnistui" :danger)
    (-> app 
      (assoc-in [:valinnat :virhe-tallennettaessa] true)
      (assoc-in [:valinnat :tallennetaan] false)))
  TehtavaTallennusOnnistui
  (process-event
    [vastaus app]
    (-> app 
      (assoc-in [:valinnat :virhe-tallennettaessa] false)
      (assoc-in [:valinnat :tallennetaan] false)))
  TallennaTehtavamaara
  (process-event
    [{tehtava :tehtava} {{samat-tuleville? :samat-tuleville :keys [hoitokausi] :as valinnat} :valinnat :as app}]
    (let [{:keys [id maara]} tehtava
          urakka-id (-> @tiedot/yleiset :urakka :id)]
      (if samat-tuleville?
        (doseq [vuosi (mapv (comp keyword str)
                        (range hoitokausi
                          (-> @tiedot/yleiset
                            :urakka
                            :loppupvm
                            pvm/vuosi)))]
          (tuck-apurit/post! :tallenna-tehtavamaarat
            {:urakka-id             urakka-id
             :hoitokauden-alkuvuosi (-> vuosi
                                      name
                                      js/parseInt)
             :tehtavamaarat         [{:tehtava-id id
                                      :maara      maara}]}
            {:onnistui           ->TehtavaTallennusOnnistui
             :epaonnistui        ->TehtavaTallennusEpaonnistui
             :paasta-virhe-lapi? true}))
        (tuck-apurit/post! :tallenna-tehtavamaarat
          {:urakka-id             urakka-id
           :hoitokauden-alkuvuosi hoitokausi
           :tehtavamaarat         [{:tehtava-id id
                                    :maara      maara}]}
          {:onnistui           ->TehtavaTallennusOnnistui
           :epaonnistui        ->TehtavaTallennusEpaonnistui
           :paasta-virhe-lapi? true}))
      (-> app 
        (assoc-in [:maarat id hoitokausi] maara)
        (update :valinnat #(assoc 
                             %
                             :virhe-tallennettaessa false
                             :tallennetaan true)))))
  HakuEpaonnistui
  (process-event
    [_ app]
    (update app :valinnat #(assoc %
                             :virhe-noudettaessa true
                             :noudetaan (dec (:noudetaan %)))))
  TehtavaHakuOnnistui
  (process-event
    [{:keys [tehtavat parametrit]} {:keys [valinnat] :as app}]
    (let [{urakka-id :id urakka-alkupvm :alkupvm} (-> @tiedot/tila :yleiset :urakka)
          alkuvuosi (pvm/vuosi urakka-alkupvm)
          toimenpide {:nimi "0 KAIKKI" :id :kaikki} #_(some
                       (fn [t]
                         (when (= 3 (:taso t))
                           t))
                       tehtavat)
          {hoitokausi         :hoitokausi} parametrit]
      (-> app
        (assoc :tehtavat-ja-toimenpiteet tehtavat)
        (update :valinnat #(assoc % :noudetaan (do
                                                 (tuck-apurit/post! :tehtavamaarat-hierarkiassa
                                                   {:urakka-id             urakka-id
                                                    :hoitokauden-alkuvuosi (or hoitokausi
                                                                             (:hoitokausi valinnat)
                                                                             alkuvuosi)}
                                                   
                                                   {:onnistui           ->MaaraHakuOnnistui
                                                    :epaonnistui        ->HakuEpaonnistui
                                                    :paasta-virhe-lapi? true})
                                                 (:noudetaan %))
                             :toimenpide-valikko-valinnat (sort-by :nimi 
                                                            (concat 
                                                              [{:nimi "0 KAIKKI" :id :kaikki}] 
                                                              (into [] 
                                                                valitason-toimenpiteet 
                                                                tehtavat)))
                             :hoitokausi (pvm/vuosi (pvm/nyt))
                             :toimenpide toimenpide)))))
  MaaraHakuOnnistui
  (process-event
    [{:keys [maarat]} {:keys [tehtavat-ja-toimenpiteet tehtavat-taulukko valinnat] :as app}]
    (let [maarat-tehtavilla (reduce 
                              maarille-tehtavien-tiedot
                              {}
                              maarat)]
      (-> app
        (assoc :taulukon-atomit (muodosta-atomit tehtavat-ja-toimenpiteet valinnat maarat-tehtavilla))
        (assoc :maarat maarat-tehtavilla)
        (update-in [:valinnat :noudetaan] dec))))
  HaeTehtavat
  (process-event
    [{parametrit :parametrit} app]
    (-> app
        (tuck-apurit/post! :tehtavat
                           {:urakka-id (:id (-> @tiedot/tila :yleiset :urakka))}
                           {:onnistui            ->TehtavaHakuOnnistui
                            :epaonnistui         ->HakuEpaonnistui
                            :onnistui-parametrit [parametrit]
                            :paasta-virhe-lapi?  true})
        (update :valinnat #(assoc %
                             :virhe-noudettaessa false
                             :noudetaan (inc (:noudetaan %))))))
  HaeMaarat
  (process-event
    [{:keys [parametrit]} app]
    (let [{:keys [hoitokausi]} parametrit
          {urakka-id :id urakka-alkupvm :alkupvm} (-> @tiedot/tila :yleiset :urakka)]
      (-> app
        (tuck-apurit/post! :tehtavamaarat-hierarkiassa
          {:urakka-id             urakka-id
           :hoitokauden-alkuvuosi (or hoitokausi
                                    (pvm/vuosi urakka-alkupvm))}
          (merge
            {:onnistui           ->MaaraHakuOnnistui
             :epaonnistui        ->HakuEpaonnistui
             :paasta-virhe-lapi? true}))
        (update :valinnat #(assoc %
                             :virhe-noudettaessa false
                             :noudetaan (inc (:noudetaan %))))
        (assoc-in [:valinnat :hoitokausi] hoitokausi))))
  ValitseTaso
  (process-event
    [{:keys [arvo taso]} {:keys [tehtavat-taulukko tehtavat-ja-toimenpiteet maarat valinnat] :as app}]
    (as-> app a      
      (assoc-in a [:valinnat taso] arvo)
      (assoc a :taulukon-atomit (muodosta-atomit tehtavat-ja-toimenpiteet (:valinnat a) maarat))))
  SamatTulevilleMoodi
  (process-event [{:keys [samat?]} app]
    (assoc-in app [:valinnat :samat-tuleville] samat?)))
