(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [clojure.string :as clj-str]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.ui.taulukko.protokollat :as p]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :as loki]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.urakka :as tila]))

(defrecord PaivitaMaara [tehtava maara])
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


(defn- tarkista-desimaalimerkki
  [arvo]
  (clj-str/replace (str arvo) #"\." ","))

(defn klikatun-rivin-lapsenlapsi?
  [janan-id klikatun-rivin-id taulukon-rivit]
  (let [klikatun-rivin-lapset (get (group-by #(-> % meta :vanhempi)
                                             taulukon-rivit)
                                   klikatun-rivin-id)
        on-lapsen-lapsi? (some #(= janan-id (p/janan-id %))
                               klikatun-rivin-lapset)
        recursion-vastaus (cond
                            (nil? klikatun-rivin-lapset) false
                            on-lapsen-lapsi? true
                            :else (map #(klikatun-rivin-lapsenlapsi? janan-id (p/janan-id %) taulukon-rivit)
                                       klikatun-rivin-lapset))]
    (if (boolean? recursion-vastaus)
      recursion-vastaus
      (some true? recursion-vastaus))))

(defn osien-paivitys-fn [tehtava maara yksikko]
  (fn [osat]
    (mapv
      (fn [osa]
        (cond
          (re-find #"-maara" (name (p/osan-id osa))) (maara osa)
          :else osa))
      osat)))

(defn maarille-tehtavien-tiedot
  [maarat-map {:keys [hoitokauden-alkuvuosi tehtava-id maara]}]
  (if-not (nil? hoitokauden-alkuvuosi)
    (assoc-in maarat-map [tehtava-id hoitokauden-alkuvuosi] maara)
    maarat-map))

#_(defn paivita-maarat-hoitokaudella
  [hoitokausi tehtavat]
  (fn [rivit]
    (mapv (fn [rivi]
            (let [tehtava-id (-> rivi p/janan-id name keyword)
                  kausi (-> hoitokausi str keyword)]
              (p/paivita-arvo rivi :lapset
                              (osien-paivitys-fn
                                identity
                                (fn [o]
                                  (p/aseta-arvo o
                                                :arvo
                                                (tarkista-desimaalimerkki
                                                  (get-in tehtavat
                                                          [tehtava-id :maarat kausi]))))
                                identity)))) rivit)))

#_(defn filtteri-paivitys-fn [valitaso nayta-aina]
  (fn [rivit]
    (mapv (fn [rivi]
            (if (or
                  (= (keyword (str (:id valitaso))) (keyword (namespace (p/janan-id rivi))))
                  (get nayta-aina (p/janan-id rivi)))
              (p/aseta-arvo rivi :piilotettu? false)
              (p/aseta-arvo rivi :piilotettu? true)))
          rivit)))

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
  PaivitaMaara
  (process-event [{:keys [tehtava maara]} {:keys [valinnat] :as app}]
    #_(println tehtava maara)
    (let [{:keys [hoitokausi samat-tuleville]} valinnat
          #_(if samat-tuleville
                (update-in app
                           [:tehtavat-ja-toimenpiteet (-> id str keyword) :maarat]
                           (fn [m]
                             (let [avaimet (mapv (comp keyword str)
                                                 (range hoitokausi
                                                        (-> @tila/yleiset
                                                            :urakka
                                                            :loppupvm
                                                            pvm/vuosi)))]
                               (reduce (fn [acc avain] (assoc acc avain arvo)) m avaimet))))
                (assoc-in app [:tehtavat-ja-toimenpiteet (-> id str keyword) :maarat (-> hoitokausi str keyword)] (tarkista-desimaalimerkki arvo)))]
      #_(p/paivita-solu! (:tehtavat-taulukko app) (p/aseta-arvo solu :arvo (tarkista-desimaalimerkki arvo) :class tyylit) app)
      app))

  SamatTulevilleMoodi
  (process-event [{:keys [samat?]} app]
    (assoc-in app [:valinnat :samat-tuleville] samat?)))
