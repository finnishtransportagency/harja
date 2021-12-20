(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [harja.tiedot.urakka.urakka :as tiedot]
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

(extend-protocol tuck/Event
  TehtavaTallennusEpaonnistui
  (process-event
    [_ app]
    app)
  TehtavaTallennusOnnistui
  (process-event
    [vastaus app]
    app)
  TallennaTehtavamaara
  (process-event
    [{[tehtava _] :tehtava} {{samat-tuleville? :samat-tuleville :keys [hoitokausi] :as valinnat} :valinnat :as app}]
    (println "ttt" tehtava)
    (let [{:keys [id maara]} tehtava
          urakka-id (-> @tila/yleiset :urakka :id)]
      (if samat-tuleville?
        (doseq [vuosi (mapv (comp keyword str)
                        (range hoitokausi
                          (-> @tila/yleiset
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
                             :virhe-tallennettaessa (if numero? false
                                                        true)
                             :tallennetaan (if numero? true
                                               false))))))
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

          toimenpide (some
                       (fn [t]
                         (when (= 3 (:taso t))
                           t))
                       tehtavat)
          {tehtavat->taulukko :tehtavat->taulukko
           hoitokausi         :hoitokausi} parametrit]
      (-> app
        (assoc :originaalit tehtavat)
        (assoc :tehtavat-ja-toimenpiteet tehtavat)
        (update :valinnat #(assoc % :noudetaan (do
                                                 (tuck-apurit/post! :tehtavamaarat-hierarkiassa
                                                   {:urakka-id             urakka-id
                                                    :hoitokauden-alkuvuosi (or hoitokausi
                                                                             (:hoitokausi valinnat)
                                                                             alkuvuosi)}
                                                   (merge
                                                     {:onnistui           ->MaaraHakuOnnistui
                                                      :epaonnistui        ->HakuEpaonnistui
                                                      :paasta-virhe-lapi? true}
                                                     (when tehtavat->taulukko {:onnistui-parametrit [tehtavat->taulukko]})))
                                                 (:noudetaan %))
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
    (let [{:keys [hoitokausi prosessori tilan-paivitys-fn]} parametrit
          {urakka-id :id urakka-alkupvm :alkupvm} (-> @tiedot/tila :yleiset :urakka)
          uusi-tila (-> app
                        (tuck-apurit/post! :tehtavamaarat-hierarkiassa
                                           {:urakka-id             urakka-id
                                            :hoitokauden-alkuvuosi (or hoitokausi
                                                                       (pvm/vuosi urakka-alkupvm))}
                                           (merge
                                             {:onnistui           ->MaaraHakuOnnistui
                                              :epaonnistui        ->HakuEpaonnistui
                                              :paasta-virhe-lapi? true} (when prosessori {:onnistui-parametrit [prosessori]})))
                        (update :valinnat #(assoc %
                                             :virhe-noudettaessa false
                                             :noudetaan (inc (:noudetaan %)))))]
      (if tilan-paivitys-fn
        (tilan-paivitys-fn uusi-tila)
        uusi-tila)))
  ValitseTaso
  (process-event
    [{:keys [arvo taso]} {:keys [tehtavat-taulukko tehtavat-ja-toimenpiteet] :as app}]
    (assoc-in app [:valinnat taso] arvo))
  SamatTulevilleMoodi
  (process-event [{:keys [samat?]} app]
    (assoc-in app [:valinnat :samat-tuleville] samat?)))
