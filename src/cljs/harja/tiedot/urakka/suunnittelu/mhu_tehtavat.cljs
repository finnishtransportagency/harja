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
(defrecord HaeSopimuksenTiedot [])
(defrecord SopimuksenHakuOnnistui [tulos])

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

(defn sopimuksen-maarille-tehtavien-tiedot
  [maarat-map {:keys [tehtava-id maara] :as rivi}]
  (assoc-in maarat-map [tehtava-id] rivi))

(defn- map->id-map-maaralla
  [maarat hoitokausi rivi]
  (let [maarat-tahan-asti (get maarat (:id rivi))
        hoitokaudet (range (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi) (inc hoitokausi))
        maarat-tahan-asti (reduce (fn [acc vuosi]
                                    (println vuosi acc maarat-tahan-asti (get maarat-tahan-asti vuosi))
                                    (+ acc (get maarat-tahan-asti vuosi))) 0 hoitokaudet)]
    [(:id rivi) (assoc rivi 
                  :hoitokausi hoitokausi
                  :maarat-tahan-asti maarat-tahan-asti
                  :maara (get-in maarat [(:id rivi) hoitokausi]))]))

(defn liita-sopimusten-tiedot 
  [sopimusten-maarat rivi]
  (let [{:keys [maara]} (get sopimusten-maarat (:id rivi))]
       (assoc rivi :sopimuksen-maara maara)))

(defn muodosta-atomit 
  [tehtavat-ja-toimenpiteet valinnat maarat-tehtavilla sopimusten-maarat]
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
                                (comp 
                                  (map (r/partial liita-sopimusten-tiedot sopimusten-maarat))
                                  (map (r/partial map->id-map-maaralla maarat-tehtavilla (:hoitokausi valinnat)))) 
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
    [{:keys [maarat]} {:keys [tehtavat-ja-toimenpiteet tehtavat-taulukko valinnat sopimuksen-maarat] :as app}]
    (let [maarat-tehtavilla (reduce 
                              maarille-tehtavien-tiedot
                              {}
                              maarat)]
      (-> app
        (assoc :taulukon-atomit (muodosta-atomit tehtavat-ja-toimenpiteet valinnat maarat-tehtavilla sopimuksen-maarat))
        (assoc :maarat maarat-tehtavilla)
        (update-in [:valinnat :noudetaan] dec))))
  SopimuksenHakuOnnistui
  (process-event
    [{:keys [tulos]} app]
    (println "sopparihaku" tulos)
    (let [sopimuksen-maarat (reduce sopimuksen-maarille-tehtavien-tiedot {} tulos)] 
      (assoc app :sopimuksen-maarat sopimuksen-maarat :urakan-alku? false)))
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
           :hoitokauden-alkuvuosi :kaikki #_(or hoitokausi
                                    (pvm/vuosi urakka-alkupvm))}
          {:onnistui           ->MaaraHakuOnnistui
           :epaonnistui        ->HakuEpaonnistui
           :paasta-virhe-lapi? true})
        (update :valinnat #(assoc %
                             :virhe-noudettaessa false
                             :noudetaan (inc (:noudetaan %))))
        (assoc-in [:valinnat :hoitokausi] hoitokausi))))
  HaeSopimuksenTiedot
  (process-event 
    [_ {:keys [tehtavat-ja-toimenpiteet] :as app}]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)
          ;; jos alla olevat unohtuu poistaa, niin huomauta
          ;; devausta varten ilman bäkkipalveluita
          feikki-payload (fn [tehtavat]
                           (let [flatattu 
                                 (reduce (fn [acc r]
                                           (concat acc (:tehtavat r)))
                                   []
                                   tehtavat)]
                             (into [] 
                               (map (fn [rivi] {:id (gensym 1) :urakka urakka-id :maara 400 :tehtava-id (:id rivi)}))
                               flatattu)))
          feikki-post! (fn [_ _ _ {:keys [onnistui epaonnistui onnistuiko? payload]}]
                         (let [e! (tuck/current-send-function)]
                           (if onnistuiko? 
                             (e! (onnistui payload))
                             (e! (epaonnistui payload)))))]
      (-> app (feikki-post! :sopimuksen-tehtavamaarat
                {:urakka-id urakka-id}
                {:onnistui ->SopimuksenHakuOnnistui
                 :epaonnistui ->HakuEpaonnistui
                 :onnistuiko? true
                 :payload (feikki-payload tehtavat-ja-toimenpiteet)}))
      ))
  ValitseTaso
  (process-event
    [{:keys [arvo taso]} {:keys [tehtavat-taulukko tehtavat-ja-toimenpiteet maarat valinnat sopimuksen-maarat] :as app}]
    (as-> app a      
      (assoc-in a [:valinnat taso] arvo)
      (assoc a :taulukon-atomit (muodosta-atomit tehtavat-ja-toimenpiteet (:valinnat a) maarat sopimuksen-maarat))))
  SamatTulevilleMoodi
  (process-event [{:keys [samat?]} app]
    (assoc-in app [:valinnat :samat-tuleville] samat?)))
