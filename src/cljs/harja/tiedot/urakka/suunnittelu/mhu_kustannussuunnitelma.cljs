(ns harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
  (:require [clojure.string :as clj-str]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.viesti :as viesti]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.tyokalut :as tyokalut]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.jana :as jana]))


(defonce viive-kanava (chan))

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(def toimenpiteiden-avaimet
  {:paallystepaikkaukset "Päällysteiden paikkaus (hoidon ylläpito)"
   :mhu-yllapito "MHU Ylläpito"
   :talvihoito "Talvihoito laaja TPI"
   :liikenneympariston-hoito "Liikenneympäristön hoito laaja TPI"
   :sorateiden-hoito "Soratien hoito laaja TPI"
   :mhu-korvausinvestointi "MHU Korvausinvestointi"})

(def talvikausi [10 11 12 1 2 3 4])
(def kesakausi (into [] (range 5 10)))
(def hoitokausi (concat talvikausi kesakausi))

(def kaudet {:kesa kesakausi
             :talvi talvikausi
             :kaikki hoitokausi})

(defn hoitokausi-nyt []
  (let [hoitovuoden-pvmt (pvm/paivamaaran-hoitokausi (pvm/nyt))
        urakan-aloitusvuosi (pvm/vuosi (-> @tiedot/yleiset :urakka :alkupvm))
        kuluva-urakan-vuosi (inc (- urakan-aloitusvuosi (pvm/vuosi (first hoitovuoden-pvmt))))]
    [kuluva-urakan-vuosi hoitovuoden-pvmt]))

(defn yhteensa-yhteenveto [paivitetty-hoitokausi app]
  (apply +
         (map (fn [taulukko]
                (let [yhteensa-sarake-index (p/otsikon-index taulukko "Yhteensä")]
                  (transduce
                    (comp (filter (fn [rivi]
                                    (= :laajenna-lapsilla (p/rivin-skeema taulukko rivi))))
                          (filter (fn [laajenna-lapsilla-rivi]
                                    (= (:hoitokausi laajenna-lapsilla-rivi) paivitetty-hoitokausi)))
                          (map (fn [laajenna-lapsilla-rivi]
                                 (first (tyokalut/arvo laajenna-lapsilla-rivi :lapset))))
                          (map (fn [yhteensa-rivi]
                                 (get (tyokalut/arvo yhteensa-rivi :lapset) yhteensa-sarake-index)))
                          (map (fn [yhteensa-solu]
                                 (tyokalut/arvo yhteensa-solu :arvo))))
                    + 0 (tyokalut/arvo taulukko :lapset))))
              (concat (vals (get-in app [:hankintakustannukset :toimenpiteet]))
                      (vals (get-in app [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen]))))))

(defrecord HaeKustannussuunnitelma [hankintojen-taulukko])
(defrecord HaeTavoiteJaKattohintaOnnistui [vastaus])
(defrecord HaeTavoiteJaKattohintaEpaonnistui [vastaus])
(defrecord HaeHankintakustannuksetOnnistui [vastaus hankintojen-taulukko])
(defrecord HaeHankintakustannuksetEpaonnistui [vastaus])
(defrecord LaajennaSoluaKlikattu [polku-taulukkoon rivin-id this auki?])
(defrecord MuutaTaulukonOsa [osa polku-taulukkoon arvo])
(defrecord PaivitaTaulukonOsa [osa polku-taulukkoon paivitys-fn])
(defrecord PaivitaKustannussuunnitelmanYhteenvedot [maara-solu polku-taulukkoon])
(defrecord TaytaAlas [maara-solu polku-taulukkoon])

(defn hankinnat-pohjadata []
  (let [urakan-aloitus-pvm (-> @tiedot/tila :yleiset :urakka :alkupvm)]
    (into []
          (drop 9
                (drop-last 3
                           (mapcat (fn [vuosi]
                                     (map #(identity
                                             {:vuosi vuosi
                                              :kuukausi %})
                                          (range 1 13)))
                                   (range (pvm/vuosi urakan-aloitus-pvm) (+ (pvm/vuosi urakan-aloitus-pvm) 6))))))))

(defn tarkista-datan-validius! [hankinnat hankinnat-laskutukseen-perustuen]
  (let [[nil-pvm-hankinnat hankinnat] (reduce (fn [[nil-pvmt pvmt] {:keys [vuosi kuukausi] :as hankinta}]
                                                (if (and vuosi kuukausi)
                                                  [nil-pvmt (conj pvmt (assoc hankinta :pvm (pvm/luo-pvm vuosi (dec kuukausi) 15)))]
                                                  [(conj nil-pvmt hankinta) pvmt]))
                                              [[] []] (concat hankinnat hankinnat-laskutukseen-perustuen))
        hankintojen-vuodet (sort (map pvm/vuosi (flatten (keys (group-by #(pvm/paivamaaran-hoitokausi (:pvm %)) hankinnat)))))
        [urakan-aloitus-vuosi urakan-lopetus-vuosi] [(pvm/vuosi (-> @tiedot/yleiset :urakka :alkupvm))
                                                     (pvm/vuosi (-> @tiedot/yleiset :urakka :loppupvm))]
        hankintoja-urakan-hoitokausien-ulkopuolella? (not= [urakan-aloitus-vuosi urakan-lopetus-vuosi]
                                                        [(first hankintojen-vuodet) (last hankintojen-vuodet)])
        nil-pvm-hankintoja? (> (count nil-pvm-hankinnat) 0)
        hoitokausien-ulkopuolella-teksti (str "Urakalle on merkattu vuodet " urakan-aloitus-vuosi " - " urakan-lopetus-vuosi
                                              ", mutta urakalle on merkattu hankintoja vuosille " (first hankintojen-vuodet) " - " (last hankintojen-vuodet) ".")
        nil-hankintoja-teksti (str "Urakalle on merkattu " (count nil-pvm-hankinnat) " hankintaa ilman päivämäärää.")]
    (when (or hankintoja-urakan-hoitokausien-ulkopuolella? nil-pvm-hankintoja?)
      (viesti/nayta! (cond-> ""
                             hankintoja-urakan-hoitokausien-ulkopuolella? (str hoitokausien-ulkopuolella-teksti "\n")
                             nil-pvm-hankintoja? (str nil-hankintoja-teksti))
                     :warning viesti/viestin-nayttoaika-pitka))))

(extend-protocol tuck/Event
  PaivitaKustannussuunnitelmanYhteenvedot
  (process-event [{:keys [maara-solu polku-taulukkoon]} {:keys [hankintakustannukset] :as app}]
    (let [taulukko (get-in app polku-taulukkoon)
          arvo (:value (tyokalut/arvo maara-solu :arvo))
          [polku-riviin _] (p/osan-polku-taulukossa taulukko maara-solu)
          hoitokauden-container (get-in taulukko polku-riviin)
          hoitokauden-yhteensa-rivi (first (p/janan-osat hoitokauden-container))
          summarivi (last (tyokalut/arvo taulukko :lapset))
          yhteensa-sarakkeen-index (p/otsikon-index taulukko "Yhteensä")
          yhteensa-rivi-yhteensa-solu (nth (p/janan-osat hoitokauden-yhteensa-rivi)
                                           yhteensa-sarakkeen-index)
          maara-rivi-yhteensa-solu (some (fn [lapsirivi]
                                           (when (p/osan-polku lapsirivi maara-solu)
                                             (nth (p/janan-osat lapsirivi)
                                                  yhteensa-sarakkeen-index)))
                                         (p/janan-osat (get-in taulukko polku-riviin)))
          summarivin-yhteensa-solu (nth (tyokalut/arvo summarivi :lapset)
                                        yhteensa-sarakkeen-index)
          entinen-arvo (tyokalut/arvo maara-rivi-yhteensa-solu :arvo)
          arvon-muutos (- arvo entinen-arvo)
          paivita-solu! (fn [app osa]
                          (p/paivita-solu! (get-in app polku-taulukkoon) osa app))
          paivitetty-hoitokausi (:hoitokausi hoitokauden-container)]
      (-> app
          (paivita-solu! (tyokalut/aseta-arvo maara-rivi-yhteensa-solu :arvo arvo))
          (paivita-solu! (tyokalut/paivita-arvo yhteensa-rivi-yhteensa-solu :arvo + arvon-muutos))
          (paivita-solu! (tyokalut/paivita-arvo summarivin-yhteensa-solu :arvo + arvon-muutos))
          (update-in [:hankintakustannukset :yhteenveto (dec paivitetty-hoitokausi) :summa] + arvon-muutos))))
  HaeKustannussuunnitelma
  (process-event [{:keys [hankintojen-taulukko]} app]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)]
      (-> app
          (tuck-apurit/post! :budjettitavoite
                             {:urakka-id urakka-id}
                             {:onnistui ->HaeTavoiteJaKattohintaOnnistui
                              :epaonnistui ->HaeTavoiteJaKattohintaEpaonnistui
                              :paasta-virhe-lapi? true})
          (tuck-apurit/post! :budjetoidut-tyot
                             {:urakka-id urakka-id}
                             {:onnistui ->HaeHankintakustannuksetOnnistui
                              :onnistui-parametrit [hankintojen-taulukko]
                              :epaonnistui ->HaeHankintakustannuksetEpaonnistui
                              :paasta-virhe-lapi? true}))))
  HaeTavoiteJaKattohintaOnnistui
  (process-event [{vastaus :vastaus} app]
    (println "HAE TAVOITE JA KATTOHINTA ONNISTUI")
    (cljs.pprint/pprint vastaus)
    (assoc app :tavoitehinnat (mapv (fn [{:keys [tavoitehinta hoitokausi]}]
                                      {:summa tavoitehinta
                                       :hoitokausi hoitokausi})
                                    vastaus)
               :kattohinnat (mapv (fn [{:keys [kattohinta hoitokausi]}]
                                    {:summa kattohinta
                                     :hoitokausi hoitokausi})
                                  vastaus)))
  HaeTavoiteJaKattohintaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;;TODO
    (println "HAE TAVOITE JA KATTOHINTA EPÄONNISTUI")
    app)
  HaeHankintakustannuksetOnnistui
  (process-event [{:keys [vastaus hankintojen-taulukko]} {{valinnat :valinnat} :hankintakustannukset :as app}]
    (println "HAE HANKINTAKUSTANNUKSET ONNISTUI")
    (let [hankintojen-pohjadata (hankinnat-pohjadata)
          hankintojen-taydennys-fn (fn [hankinnat]
                                     (sequence
                                       (comp
                                         (mapcat (fn [toimenpide]
                                                   (tyokalut/generoi-pohjadata identity
                                                                               hankintojen-pohjadata
                                                                               (filter #(= (:toimenpide %) (get toimenpiteiden-avaimet toimenpide))
                                                                                       hankinnat)
                                                                               {:summa 0
                                                                                :toimenpide (get toimenpiteiden-avaimet toimenpide)})))
                                         (map (fn [{:keys [vuosi kuukausi] :as data}]
                                                (assoc data :pvm (pvm/luo-pvm vuosi (dec kuukausi) 15)))))
                                       toimenpiteet))
          hankinnat (:kiinteahintaiset-tyot vastaus)
          hankinnat-laskutukseen-perustuen (filter #(= (:tyyppi % "laskutettava-tyo"))
                                                   (:kustannusarvioidut-tyot vastaus))
          hankinnat-hoitokausille (hankintojen-taydennys-fn hankinnat)
          hankinnat-laskutukseen-perustuen-hoitokausille (hankintojen-taydennys-fn hankinnat-laskutukseen-perustuen)
          laskutukseen-perustuvat-toimenpiteet (reduce (fn [toimenpide-avaimet toimenpide]
                                                         (conj toimenpide-avaimet
                                                               (some #(when (= (second %) toimenpide)
                                                                        (first %))
                                                                     toimenpiteiden-avaimet)))
                                                       #{} (distinct
                                                             (map :toimenpide hankinnat-laskutukseen-perustuen-hoitokausille)))
          hankinnat-toimenpiteittain (group-by :toimenpide hankinnat-hoitokausille)
          hankinnat-laskutukseen-perustuen-toimenpiteittain (group-by :toimenpide hankinnat-laskutukseen-perustuen-hoitokausille)
          hankinnat-hoitokausittain (group-by #(pvm/paivamaaran-hoitokausi (:pvm %))
                                              (concat hankinnat-laskutukseen-perustuen-hoitokausille hankinnat-hoitokausille))]
      (tarkista-datan-validius! hankinnat hankinnat-laskutukseen-perustuen)
      (-> app
          (assoc-in [:hankintakustannukset :valinnat :laskutukseen-perustuen] laskutukseen-perustuvat-toimenpiteet)
          (assoc-in [:hankintakustannukset :yhteenveto] (into []
                                                              (map-indexed (fn [index [_ tiedot]]
                                                                             {:hoitokausi (inc index)
                                                                              :summa (apply + (map :summa tiedot))})
                                                                           hankinnat-hoitokausittain)))
          (assoc-in [:hankintakustannukset :toimenpiteet]
                    (into {}
                          (map (fn [[toimenpide-avain toimenpide-nimi]]
                                 [toimenpide-avain (hankintojen-taulukko (get hankinnat-toimenpiteittain toimenpide-nimi) valinnat toimenpide-avain true false)])
                               toimenpiteiden-avaimet)))
          (assoc-in [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen]
                    (into {}
                          (map (fn [[toimenpide-avain toimenpide-nimi]]
                                 [toimenpide-avain (hankintojen-taulukko (get hankinnat-laskutukseen-perustuen-toimenpiteittain toimenpide-nimi) valinnat toimenpide-avain true true)])
                               toimenpiteiden-avaimet))))))
  HaeHankintakustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;; TODO
    (println "HAE HANKINTAKUSTANNUKSET EPÄONNISTUI")
    (cljs.pprint/pprint vastaus)
    app)
  LaajennaSoluaKlikattu
  (process-event [{:keys [polku-taulukkoon rivin-id auki?]} app]
    (let [rivin-container (tyokalut/rivin-vanhempi (get-in app (conj polku-taulukkoon :rivit))
                                                   rivin-id)
          toggle-fn (if auki? disj conj)]
      (p/paivita-taulukko! (update (get-in app polku-taulukkoon) :rivit
                                   (fn [rivit]
                                     (mapv (fn [rivi]
                                             (if (p/janan-id? rivi (p/janan-id rivin-container))
                                               (update rivi :janat (fn [[paa & lapset]]
                                                                     (into []
                                                                           (cons paa
                                                                                 (map #(update % :luokat toggle-fn "piillotettu") lapset)))))
                                               rivi))
                                           rivit)))
                           app)))
  MuutaTaulukonOsa
  (process-event [{:keys [osa arvo polku-taulukkoon]} app]
    (p/paivita-solu! (get-in app polku-taulukkoon)
                     (tyokalut/aseta-arvo osa :arvo arvo)
                     app))
  PaivitaTaulukonOsa
  (process-event [{:keys [osa polku-taulukkoon paivitys-fn]} app]
    (p/paivita-solu! (get-in app polku-taulukkoon)
                     (tyokalut/paivita-arvo osa :arvo paivitys-fn)
                     app))
  TaytaAlas
  (process-event [{:keys [maara-solu polku-taulukkoon]} app]
    (let [taulukko (get-in app polku-taulukkoon)
          [polku-riviin _] (p/osan-polku-taulukossa taulukko maara-solu)
          tayta-rivista-eteenpain (first (keep-indexed (fn [index rivi]
                                                         (when (p/osan-polku rivi maara-solu)
                                                           index))
                                                       (tyokalut/arvo (get-in taulukko polku-riviin) :lapset)))
          value (:value (tyokalut/arvo maara-solu :arvo))
          paivitetty-hoitokausi (:hoitokausi (get-in taulukko polku-riviin))
          maara-otsikon-index (p/otsikon-index taulukko "Määrä")
          yhteensa-otsikon-index (p/otsikon-index taulukko "Yhteensä")
          ;; Päivitetään ensin kaikkien maararivien 'määrä' ja 'yhteensä' solut
          app (p/paivita-rivi! taulukko
                               (tyokalut/paivita-arvo (get-in taulukko polku-riviin) :lapset
                                                      (fn [rivit]
                                                        (tyokalut/mapv-range tayta-rivista-eteenpain
                                                                             (fn [maara-rivi]
                                                                               (tyokalut/paivita-arvo maara-rivi
                                                                                                      :lapset
                                                                                                      (fn [osat]
                                                                                                        (tyokalut/mapv-indexed
                                                                                                          (fn [index osa]
                                                                                                            (cond
                                                                                                              (= index maara-otsikon-index) (tyokalut/paivita-arvo osa :arvo assoc :value value)
                                                                                                              (= index yhteensa-otsikon-index) (tyokalut/aseta-arvo osa :arvo value)
                                                                                                              :else osa))
                                                                                                          osat))))
                                                                             rivit)))
                               app)
          taulukko (get-in app polku-taulukkoon)
          ;; Sitten päivitetään yhteensä rivin yhteensä solu
          app (p/paivita-rivi! taulukko
                               (tyokalut/paivita-arvo (get-in taulukko polku-riviin) :lapset
                                                      (fn [rivit]
                                                        (tyokalut/mapv-range 0 1
                                                                             (fn [yhteensa-rivi]
                                                                               (tyokalut/paivita-arvo yhteensa-rivi
                                                                                                      :lapset
                                                                                                      (fn [osat]
                                                                                                        (tyokalut/mapv-indexed
                                                                                                          (fn [index osa]
                                                                                                            (if (= index yhteensa-otsikon-index)
                                                                                                              (tyokalut/aseta-arvo osa :arvo (apply + (map (fn [rivi]
                                                                                                                                                             (tyokalut/arvo (get (tyokalut/arvo rivi :lapset) yhteensa-otsikon-index)
                                                                                                                                                                            :arvo))
                                                                                                                                                           (rest rivit))))
                                                                                                              osa))
                                                                                                          osat))))
                                                                             rivit)))
                               app)
          ;; Sitten Summarivin yhteensä solu
          taulukko (get-in app polku-taulukkoon)
          app (p/paivita-taulukko! (tyokalut/paivita-arvo taulukko :lapset
                                                      (fn [rivit]
                                                        (tyokalut/mapv-range (dec (count rivit)) (count rivit)
                                                                             (fn [summa-rivi]
                                                                               (tyokalut/paivita-arvo summa-rivi
                                                                                                      :lapset
                                                                                                      (fn [osat]
                                                                                                        (tyokalut/mapv-indexed
                                                                                                          (fn [index osa]
                                                                                                            (if (= index yhteensa-otsikon-index)
                                                                                                              (tyokalut/aseta-arvo osa :arvo (apply + (keep (fn [rivi]
                                                                                                                                                              (when (= :laajenna-lapsilla (p/rivin-skeema taulukko rivi))
                                                                                                                                                                (tyokalut/arvo (nth (tyokalut/arvo (first (tyokalut/arvo rivi :lapset))
                                                                                                                                                                                                   :lapset)
                                                                                                                                                                                    yhteensa-otsikon-index)
                                                                                                                                                                               :arvo)))
                                                                                                                                                           rivit)))
                                                                                                              osa))
                                                                                                          osat))))
                                                                             rivit)))
                               app)
          hoitokauden-summa (yhteensa-yhteenveto paivitetty-hoitokausi app)]
      ;; Lopuksi yhteenvedon summa
      (assoc-in app [:hankintakustannukset :yhteenveto (dec paivitetty-hoitokausi) :summa]
                hoitokauden-summa))))