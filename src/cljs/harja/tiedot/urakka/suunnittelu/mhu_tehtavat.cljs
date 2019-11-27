(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [clojure.string :as clj-str]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalut :as tyokalut]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :as loki]
            [harja.pvm :as pvm]))

(defrecord PaivitaMaara [solu arvo tyylit])
(defrecord LaajennaSoluaKlikattu [laajenna-osa auki?])
(defrecord ValitseTaso [arvo taso])
(defrecord HaeTehtavatJaMaarat [parametrit])
(defrecord TehtavaHakuOnnistui [tehtavat parametrit])
(defrecord HakuEpaonnistui [])
(defrecord MaaraHakuOnnistui [maarat prosessoi-tulokset])
(defrecord TehtavaTallennusOnnistui [vastaus])
(defrecord TehtavaTallennusEpaonnistui [vastaus])
(defrecord TallennaTehtavamaara [tehtava])
(defrecord HaeMaarat [parametrit])
(defrecord SamatKaikilleMoodi [samat?])

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})


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

(defn paivita-tehtavien-maarat
  [tehtavat maarat]
  (reduce (fn [acc {:keys [hoitokauden-alkuvuosi maara tehtava-id]}]
            (if-not (nil? hoitokauden-alkuvuosi)
              (assoc-in acc [(-> tehtava-id str keyword)
                             :maarat
                             (-> hoitokauden-alkuvuosi str keyword)]
                        maara)
              acc))
          tehtavat
          maarat))

(defn paivita-maarat-hoitokaudella
  [hoitokausi tehtavat]
  (fn [rivit]
    (mapv (fn [rivi]
            (let [tehtava-id (-> rivi p/janan-id name keyword)
                  kausi (-> hoitokausi str keyword)]
              (p/paivita-arvo rivi :lapset
                              (osien-paivitys-fn
                                identity
                                (fn [o]
                                  (p/aseta-arvo o :arvo (get-in tehtavat [tehtava-id :maarat kausi])))
                                identity)))) rivit)))

(defn filtteri-paivitys-fn [valitaso nayta-aina]
  (fn [rivit]
    (mapv (fn [rivi]
            (if (or
                  (= (keyword (str (:id valitaso))) (keyword (namespace (p/janan-id rivi))))
                  (get nayta-aina (p/janan-id rivi)))
              (p/aseta-arvo rivi :piillotettu? false)
              (p/aseta-arvo rivi :piillotettu? true)))
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
    [{:keys [tehtava]} {:keys [valinnat tehtavat-ja-toimenpiteet] :as app}]
    (let [{:keys [urakka-id tehtava-id maara]} tehtava
          numero-maara (-> maara (clj-str/replace #"," ".") js/parseFloat)
          numero? (not (js/isNaN numero-maara))
          samat-kaikille? (:samat-kaikille valinnat)]
      (if samat-kaikille?
        (doseq [vuosi (keys (get-in tehtavat-ja-toimenpiteet [(-> tehtava-id str keyword) :maarat]))]
          (let [maara (-> tehtavat-ja-toimenpiteet
                          (get-in [(-> tehtava-id str keyword) :maarat vuosi])
                          (clj-str/replace #"," ".")
                          js/parseFloat)]
            (tuck-apurit/post! :tallenna-tehtavamaarat
                               {:urakka-id             urakka-id
                                :hoitokauden-alkuvuosi (-> vuosi
                                                           name
                                                           js/parseInt)
                                :tehtavamaarat         [{:tehtava-id tehtava-id
                                                         :maara      maara}]}
                               {:onnistui           ->TehtavaTallennusOnnistui
                                :epaonnistui        ->TehtavaTallennusEpaonnistui
                                :paasta-virhe-lapi? true})))
        (when numero?
          (tuck-apurit/post! :tallenna-tehtavamaarat
                             {:urakka-id             urakka-id
                              :hoitokauden-alkuvuosi (:hoitokausi valinnat)
                              :tehtavamaarat         [{:tehtava-id tehtava-id
                                                       :maara      numero-maara}]}
                             {:onnistui           ->TehtavaTallennusOnnistui
                              :epaonnistui        ->TehtavaTallennusEpaonnistui
                              :paasta-virhe-lapi? true})))
      (update app :valinnat #(assoc %
                               :virhe-tallennettaessa (if numero? false
                                                                  true)
                               :tallennetaan (if numero? true
                                                         false)))))
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
          id-avaimilla-hierarkia (reduce (fn [kaikki [avain teht]]
                                           (assoc kaikki avain
                                                         (if (= 4 (:taso teht))
                                                           (assoc teht
                                                             :maarat (reduce
                                                                       (fn [vuodet vuosi]
                                                                         (assoc vuodet (-> vuosi
                                                                                           str
                                                                                           keyword) 0))
                                                                       {}
                                                                       (range alkuvuosi
                                                                              (+ alkuvuosi 5)))
                                                             :alkuvuosi alkuvuosi)
                                                           teht)))
                                         {}
                                         (seq tehtavat))
          toimenpide (some
                       (fn [[_ t]]
                         (when (= 3 (:taso t))
                           t))
                       tehtavat)
          {tehtavat->taulukko :tehtavat->taulukko} parametrit]
      (-> app
          (assoc :tehtavat-ja-toimenpiteet id-avaimilla-hierarkia
                 :tehtavat-taulukko (p/paivita-arvo (tehtavat->taulukko id-avaimilla-hierarkia) :lapset (filtteri-paivitys-fn toimenpide #{:tehtava})))
          (update :valinnat #(assoc % :noudetaan (do
                                                   (tuck-apurit/post! :tehtavamaarat-hierarkiassa
                                                                      {:urakka-id             urakka-id
                                                                       :hoitokauden-alkuvuosi (or (:hoitokausi valinnat)
                                                                                                  alkuvuosi)}
                                                                      (merge
                                                                        {:onnistui           ->MaaraHakuOnnistui
                                                                         :epaonnistui        ->HakuEpaonnistui
                                                                         :paasta-virhe-lapi? true} (when tehtavat->taulukko {:onnistui-parametrit [tehtavat->taulukko]})))
                                                   (:noudetaan %))
                                      :hoitokausi (pvm/vuosi (pvm/nyt))
                                      :toimenpide toimenpide)))))
  MaaraHakuOnnistui
  (process-event
    [{:keys [maarat]} {:keys [tehtavat-ja-toimenpiteet tehtavat-taulukko valinnat] :as app}]
    (let [{:keys [toimenpide hoitokausi]} valinnat
          tehtavat-maarilla (paivita-tehtavien-maarat tehtavat-ja-toimenpiteet maarat)
          paivitetty-taulukko (p/paivita-arvo tehtavat-taulukko :lapset (paivita-maarat-hoitokaudella hoitokausi tehtavat-maarilla))
          filtteroity-taulukko (p/paivita-arvo paivitetty-taulukko :lapset (filtteri-paivitys-fn toimenpide #{:tehtava}))]
      (p/paivita-taulukko! filtteroity-taulukko (-> app
                                                    (assoc :tehtavat-ja-toimenpiteet tehtavat-maarilla)
                                                    (update :valinnat #(assoc % :noudetaan (dec (:noudetaan %))))))))
  HaeTehtavatJaMaarat
  (process-event
    [{parametrit :parametrit} app]
    (-> app
        (tuck-apurit/get! :tehtavat
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
    (let [nayta-aina #{:tehtava}]
      (case taso
        :hoitokausi
        (let [paivitetty-taulukko (p/paivita-arvo tehtavat-taulukko :lapset (paivita-maarat-hoitokaudella arvo tehtavat-ja-toimenpiteet))]
          (p/paivita-taulukko! paivitetty-taulukko
                               (assoc-in app [:valinnat :hoitokausi] arvo)))
        :toimenpide
        (let [taulukko (p/paivita-arvo tehtavat-taulukko :lapset
                                       (filtteri-paivitys-fn arvo nayta-aina))]
          (p/paivita-taulukko! taulukko (assoc-in app [:valinnat :toimenpide] arvo))))))
  PaivitaMaara
  (process-event [{:keys [solu arvo tyylit]} {:keys [valinnat] :as app}]
    (let [{:keys [hoitokausi samat-kaikille]} valinnat
          id (-> (p/osan-id solu)
                 name
                 (clj-str/split #"-")
                 first)
          app (if samat-kaikille
                (update-in app [:tehtavat-ja-toimenpiteet (-> id str keyword) :maarat] (fn [m]
                                                                                         (let [avaimet (keys m)]
                                                                                           (reduce (fn [acc avain] (assoc acc avain arvo)) m avaimet))))
                (assoc-in app [:tehtavat-ja-toimenpiteet (-> id str keyword) :maarat (-> hoitokausi str keyword)] arvo))]
      (p/paivita-solu! (:tehtavat-taulukko app) (p/aseta-arvo solu :arvo arvo :class tyylit) app)))

  SamatKaikilleMoodi
  (process-event [{:keys [samat?]} app]
    (assoc-in app [:valinnat :samat-kaikille] samat?))
  LaajennaSoluaKlikattu
  (process-event [{:keys [laajenna-osa auki?]} app]
    (update app :tehtavat-taulukko
            (fn [taulukon-rivit]
              (let [klikatun-rivin-id (first (keep (fn [rivi]
                                                     (when (p/osan-polku rivi laajenna-osa)
                                                       (p/janan-id rivi)))
                                                   taulukon-rivit))]
                (map (fn [{:keys [janan-id] :as rivi}]
                       (let [{:keys [vanhempi]} (meta rivi)
                             klikatun-rivin-lapsi? (= klikatun-rivin-id vanhempi)
                             klikatun-rivin-lapsenlapsi? (klikatun-rivin-lapsenlapsi? janan-id klikatun-rivin-id taulukon-rivit)]
                         ;; Jos joku rivi on kiinnitetty, halutaan sulkea myös kaikki lapset ja lasten lapset.
                         ;; Kumminkin lapsirivien Laajenna osan sisäinen tila jää väärään tilaan, ellei sitä säädä ulko käsin.
                         ;; Tässä otetaan ja muutetaan se oikeaksi.
                         (when (and (not auki?) klikatun-rivin-lapsenlapsi?)
                           (when-let [rivin-laajenna-osa (some #(when (instance? osa/Laajenna %)
                                                                  %)
                                                               (:solut rivi))]
                             (reset! (p/osan-tila rivin-laajenna-osa) false)))
                         (cond
                           ;; Jos riviä klikataan, piilotetaan lapset
                           (and auki? klikatun-rivin-lapsi?) (vary-meta (update rivi :luokat disj "piillotettu")
                                                                        assoc :piillotettu? false)
                           ;; Jos rivillä on lapsen lapsia, piillotetaan myös ne
                           (and (not auki?) klikatun-rivin-lapsenlapsi?) (vary-meta (update rivi :luokat conj "piillotettu")
                                                                                    assoc :piillotettu? true)
                           :else rivi)))
                     taulukon-rivit))))))