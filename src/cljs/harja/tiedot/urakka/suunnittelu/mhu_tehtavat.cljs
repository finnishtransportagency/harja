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

(defrecord PaivitaMaara [solu arvo])
(defrecord LaajennaSoluaKlikattu [laajenna-osa auki?])
(defrecord JarjestaTehtavienMukaan [])
(defrecord JarjestaMaaranMukaan [])
(defrecord ValitseTaso [arvo taso])
(defrecord HaeTehtavat [parametrit])
(defrecord TehtavaHakuEpaonnistui [])
(defrecord TehtavaHakuOnnistui [tehtavat prosessoi-tulokset])
(defrecord TehtavaTallennusOnnistui [])
(defrecord TehtavaTallennusEpaonnistui [])
(defrecord TallennaTehtavamaara [tehtava])

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(defn ylempi-taso?
  [ylempi-taso alempi-taso]
  (case ylempi-taso
    "ylataso" (not= "ylataso" alempi-taso)
    "valitaso" (= "alataso" alempi-taso)
    "alataso" false))

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

(defn jarjesta-tehtavan-otsikon-mukaisesti [tehtavat otsikko]
  (let [tehtavan-nimi (fn [tehtava]
                        (let [solu (some #(when (= (-> % meta :sarake) "Tehtävä")
                                            %)
                                         (p/janan-osat tehtava))]
                          (:teksti solu)))
        tehtavan-maara (fn [tehtava]
                         (let [solu (some #(when (= (-> % meta :sarake) "Määrä")
                                             %)
                                          (p/janan-osat tehtava))
                               maara (cond
                                       (= (type solu) osa/Teksti) (get solu :teksti)
                                       (= (type solu) osa/Syote) (get-in solu [:parametrit :value]))]
                           (if (= "" maara) 0 (js/parseFloat maara))))
        tehtavan-id (fn [tehtava]
                      (p/janan-id tehtava))
        vanhemman-tehtava (fn [tehtava]
                            (some #(when (= (:vanhempi (meta tehtava)) (p/janan-id %))
                                     %)
                                  tehtavat))
        tehtavan-tunniste (juxt (case otsikko
                                  :tehtava tehtavan-nimi
                                  :maara tehtavan-maara)
                                tehtavan-id)]
    (into []
          (sort-by (fn [tehtava]
                     (if (= (p/janan-id tehtava) :tehtavataulukon-otsikko)
                       []
                       (case (-> tehtava meta :tehtavaryhmatyyppi)
                         "ylataso" [(tehtavan-tunniste tehtava) nil nil]
                         "valitaso" [(tehtavan-tunniste (vanhemman-tehtava tehtava))
                                     (tehtavan-tunniste tehtava)
                                     nil]
                         "alitaso" [(-> tehtava vanhemman-tehtava vanhemman-tehtava tehtavan-tunniste)
                                    (tehtavan-tunniste (vanhemman-tehtava tehtava))
                                    (tehtavan-tunniste tehtava)])))
                   tehtavat))))

(defn paivitys-fn [arvo hierarkia nayta-aina]
  (fn [rivit]
   (mapv (fn [rivi]
           (if (or
                 (= (:id arvo) (get hierarkia (p/janan-id rivi)))
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
    [_ app]
    app)
  TallennaTehtavamaara
  (process-event
    [{:keys [tehtava]} {:keys [valinnat] :as app}]
    (let [{:keys [urakka-id tehtava-id maara]} tehtava]
      (tuck-apurit/post! :tallenna-tehtavamaarat
                         {:urakka-id             urakka-id
                          :hoitokauden-alkuvuosi (:hoitokausi valinnat)
                          :tehtavamaarat         [{:tehtava-id tehtava-id
                                                   :maara      (js/parseInt maara)}]}
                         {:onnistui ->TehtavaTallennusOnnistui
                          :epaonnistui ->TehtavaTallennusEpaonnistui
                          :paasta-virhe-lapi? true})
      (update app :valinnat #(assoc %
                               :virhe-tallennettaessa false
                               :tallennetaan true))))
  TehtavaHakuEpaonnistui
  (process-event
    [_ app]
    (update app :valinnat #(assoc %
                               :virhe-noudettaessa true
                               :noudetaan false)))
  TehtavaHakuOnnistui
  (process-event
    [{:keys [tehtavat prosessoi-tulokset]} app]
    (let [hierarkia (reduce (fn [acc {:keys [id tehtavaryhmatyyppi vanhempi toimenpide]}]
                              (case tehtavaryhmatyyppi
                                "otsikko"
                                (assoc acc id toimenpide)
                                "toimenpide"
                                acc
                                "tehtava"
                                (assoc acc id vanhempi))) {} tehtavat)
          toimenpide (some (fn [t] (when (= "toimenpide" (:tehtavaryhmatyyppi t)) t)) tehtavat)
          valitaso (some (fn [t] (when (and
                                         (= (:id toimenpide) (:toimenpide t))
                                         (= "otsikko" (:tehtavaryhmatyyppi t))) t)) tehtavat)]
      (-> app
         (update :valinnat #(assoc % :noudetaan false
                                     :hoitokausi (pvm/vuosi (pvm/nyt))
                                     :toimenpide toimenpide
                                     :valitaso valitaso))
         (assoc :tehtava-ja-maaraluettelo tehtavat
                :hierarkia hierarkia
                :tehtavat-taulukko (p/paivita-arvo (prosessoi-tulokset tehtavat) :lapset (paivitys-fn valitaso hierarkia #{:tehtava}))))))
  HaeTehtavat
  (process-event
    [{:keys [parametrit]} app]
    (let [{:keys [hoitokausi prosessori tilan-paivitys-fn]} parametrit
          {urakka-id :id urakka-alkupvm :alkupvm} (-> @tiedot/tila :yleiset :urakka)
          uusi-tila (-> app
                        (tuck-apurit/post! :tehtavamaarat-hierarkiassa
                                           {:urakka-id urakka-id
                                            :hoitokauden-alkuvuosi (or hoitokausi
                                                                       (harja.pvm/vuosi urakka-alkupvm))}
                                           (merge
                                             {:onnistui            ->TehtavaHakuOnnistui
                                              :epaonnistui         ->TehtavaHakuEpaonnistui
                                              :paasta-virhe-lapi?  true} (when prosessori {:onnistui-parametrit [prosessori]})))
                        (update :valinnat #(assoc %
                                             :virhe-noudettaessa false
                                             :noudetaan true)))]
      (if tilan-paivitys-fn
        (tilan-paivitys-fn uusi-tila)
        uusi-tila)))
  ValitseTaso
  (process-event
    [{:keys [arvo taso]} {:keys [tehtavat-taulukko hierarkia tehtava-ja-maaraluettelo] :as app}]
    (let [nayta-aina #{:tehtava}]
      (case taso
       :hoitokausi
       (assoc-in app [:valinnat :hoitokausi] arvo)
       :ylataso
       (let [valitaso (some #(when (and (= "otsikko" (:tehtavaryhmatyyppi %))
                                        (= (:id arvo) (:toimenpide %))) %) tehtava-ja-maaraluettelo)
             toimenpide-ja-valitaso (update app :valinnat (fn [valinnat]
                                                            (assoc valinnat
                                                              :toimenpide arvo
                                                              :valitaso valitaso)))
             taulukko (p/paivita-arvo tehtavat-taulukko :lapset (paivitys-fn valitaso hierarkia nayta-aina))]
         (p/paivita-taulukko! taulukko toimenpide-ja-valitaso))
       :valitaso
       (let [taulukko (p/paivita-arvo tehtavat-taulukko :lapset
                                      (paivitys-fn arvo hierarkia nayta-aina))]
         (p/paivita-taulukko! taulukko (assoc-in app [:valinnat :valitaso] arvo))))))
  PaivitaMaara
  (process-event [{:keys [solu arvo]} app]
    (p/paivita-solu! (:tehtavat-taulukko app) (p/aseta-arvo solu :arvo arvo) app))

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
                        taulukon-rivit)))))
  JarjestaTehtavienMukaan
  (process-event [_ app]
    (update app :tehtavat-taulukko
            (fn [tehtavat]
              (jarjesta-tehtavan-otsikon-mukaisesti tehtavat :tehtava))))
  JarjestaMaaranMukaan
  (process-event [_ app]
    (update app :tehtavat-taulukko
            (fn [tehtavat]
              (jarjesta-tehtavan-otsikon-mukaisesti tehtavat :maara)))))