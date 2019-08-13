(ns harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
  (:require [clojure.string :as clj-str]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.loki :refer [log]]
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

(defrecord HaeKustannussuunnitelma [hankintojen-taulukko])
(defrecord HaeTavoiteJaKattohintaOnnistui [vastaus])
(defrecord HaeTavoiteJaKattohintaEpaonnistui [vastaus])
(defrecord HaeHankintakustannuksetOnnistui [vastaus hankintojen-taulukko])
(defrecord HaeHankintakustannuksetEpaonnistui [vastaus])
(defrecord LaajennaSoluaKlikattu [polku-taulukkoon rivin-id this auki?])
(defrecord PaivitaToimenpiteenHankintaMaara [osa arvo polku-taulukkoon])
(defrecord PaivitaKustannussuunnitelmanYhteenvedot [])

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

(extend-protocol tuck/Event
  #_PaivitaKustannussuunnitelmanYhteenvedot
  #_(process-event [_ {:keys [hankintakustannukset] :as app}]
    (let [{:keys [toimenpiteet toimenpiteet-laskutukseen-perustuen]} hankintakustannukset
          rivi-predikaatti (fn [rivi]
                             (contains? (meta rivi) :hoitokausi))
          osa-predikaatti (fn [osa]
                            (-> osa meta :sarake (= :yhteensa)))
          osan-arvo (fn [osa]
                      (-> osa :teksti js/parseInt))
          yhteenveto-fn (fn [toimenpiteet]
                          (reduce (fn [yhteenveto [_ taulukko]]
                                    (reduce (fn [yhteenveto rivi]
                                              (update yhteenveto (dec (:hoitokausi (meta rivi)))
                                                      (fn [hoitokauden-yhteenveto]
                                                        (update hoitokauden-yhteenveto :summa + (some #(when (osa-predikaatti %)
                                                                                                         (osan-arvo %))
                                                                                                      (p/janan-osat rivi))))))
                                            yhteenveto (filter rivi-predikaatti taulukko)))
                                  (mapv #(identity {:hoitokausi % :summa 0})
                                        (range 1 6))
                                  toimenpiteet))
          toimenpiteet-yhteenveto (sort-by :hoitokausi (yhteenveto-fn toimenpiteet))
          toimenpiteet-lp-yhteenveto (sort-by :hoitokausi (yhteenveto-fn toimenpiteet-laskutukseen-perustuen))
          yhteenveto (mapv #(update %1 :summa + (:summa %2))
                           toimenpiteet-yhteenveto
                           toimenpiteet-lp-yhteenveto)

          yhteenveto-fn (fn [toimenpiteet]
                          (map (fn [toimenpide taulukko]
                                 (let [rivit (filter #(-> % meta :rivi :lapsi) taulukko)
                                       ryhmitellyt-rivit (group-by :hoitokausi rivit)]
                                   {toimenpide (mapv (fn [[hoitokausi hoitokauden-rivit]]
                                                       ())
                                                     (sort-by ffirst ryhmitellyt-rivit))}))
                               toimenpiteet))
          toimenpiteiden-summat {:toimenpiteet ""
                                 :toimenpiteet-laskutukseen-perustuen ""}]
      (assoc-in app [:hankintakustannukset :yhteenveto] yhteenveto)

      (-> app
          (update-in [:hankintakustannukset :toimenpiteet]
                     (fn [toimenpiteet]
                       (into {}
                             (map (fn [toimenpide taulukko]
                                    [toimenpide (map (fn [rivi]
                                                       (let [rivin-tyyppi (-> rivi meta :rivi)
                                                             hoitokausi (-> rivi meta :hoitokausi)]
                                                         (case rivin-tyyppi
                                                           :paa (-> toimenpiteiden-summat :toimenpiteet toimenpide (get hoitokausi))
                                                           :lapsi
                                                           :otsikko
                                                           :yhteensa)))
                                                     taulukko)])
                                  toimenpiteet)))))))
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
          toimenpiteiden-kasittely-fn (fn [haetut-toimenpiteet]
                                        (let [valmis-data (mapcat (fn [toimenpide]
                                                                    (tyokalut/generoi-pohjadata identity
                                                                                                hankintojen-pohjadata
                                                                                                (filter #(= (:toimenpide %) (get toimenpiteiden-avaimet toimenpide))
                                                                                                        haetut-toimenpiteet)
                                                                                                {:summa 0
                                                                                                 :toimenpide (get toimenpiteiden-avaimet toimenpide)}))
                                                                  toimenpiteet)]
                                          (group-by :toimenpide
                                                    (map (fn [{:keys [vuosi kuukausi] :as maarat}]
                                                           (assoc maarat :pvm (pvm/luo-pvm vuosi (dec kuukausi) 15)))
                                                         valmis-data))))
          toimenpiteet (toimenpiteiden-kasittely-fn (:kiinteahintaiset-tyot vastaus))
          toimenpiteet-laskutukseen-perustuen (toimenpiteiden-kasittely-fn (map #(= (:tyyppi % "laskutettava-tyo"))
                                                                                (:kustannusarvioidut-tyot vastaus)))]
      (-> app
          (assoc-in [:hankintakustannukset :toimenpiteet]
                    (into {}
                          (map (fn [[toimenpide-avain toimenpide-nimi]]
                                 [toimenpide-avain (hankintojen-taulukko (get toimenpiteet toimenpide-nimi) valinnat toimenpide-avain true false)])
                               toimenpiteiden-avaimet)))
          (assoc-in [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen]
                    (into {}
                          (map (fn [[toimenpide-avain toimenpide-nimi]]
                                 [toimenpide-avain (hankintojen-taulukko (get toimenpiteet-laskutukseen-perustuen toimenpide-nimi) valinnat toimenpide-avain true true)])
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
          #_#__ (tyokalut/ominaisuus-predikaatilla (get-in app (conj polku-taulukkoon :rivit))
                                                             (fn [index rivi]
                                                               rivi)
                                                             (fn [rivi]
                                                               (when (= (type rivi) jana/RiviLapsilla)
                                                                 (p/janan-id? (first (p/janan-osat rivi)) rivin-id))))]
      (p/paivita-taulukko! (update (get-in app polku-taulukkoon) :rivit
                                   (fn [rivit]
                                     (mapv (fn [rivi]
                                             (if (p/janan-id? rivi (p/janan-id rivin-container))
                                               (if auki?
                                                 (update rivi :janat (fn [[paa & lapset]]
                                                                       (into []
                                                                             (cons paa
                                                                                   (map #(update % :luokat disj "piillotettu") lapset)))))
                                                 (update rivi :janat (fn [[paa & lapset]]
                                                                       (into []
                                                                             (cons paa
                                                                                   (map #(update % :luokat conj "piillotettu") lapset))))))
                                               rivi))
                                           rivit)))
                           app)))
  PaivitaToimenpiteenHankintaMaara
  (process-event [{:keys [osa arvo polku-taulukkoon]} app]
    (p/paivita-solu! (get-in app polku-taulukkoon)
                     (p/aseta-osan-arvo osa arvo)
                     app)
    #_(let [[janan-index & _ :as osan-polku] (tyokalut/osan-polku-taulukossa (get-in app polku-taulukkoon) osa)
          vanhemman-janan-id (:vanhempi (meta (get-in app (conj polku-taulukkoon janan-index))))
          vanhempi-jana (first (tyokalut/jana (get-in app polku-taulukkoon) vanhemman-janan-id))
          vanhemman-janan-index (tyokalut/janan-index (get-in app polku-taulukkoon) vanhempi-jana)
          yhteensa-index (first (keep-indexed (fn [index osa]
                                                (when (= (-> osa meta :sarake) :yhteensa)
                                                  index))
                                              (p/janan-osat vanhempi-jana)))
          polku-muutettuun-arvoon (concat polku-taulukkoon osan-polku [:parametrit :value])
          polku-yhteensa-arvoon (conj polku-taulukkoon vanhemman-janan-index :solut yhteensa-index :teksti)]
      (-> app
          (update-in polku-yhteensa-arvoon
                     (fn [vanha-yhteensa]
                       (let [vanha-arvo (get-in app polku-muutettuun-arvoon)
                             lisays (- (js/parseInt arvo) (js/parseInt vanha-arvo))]
                         (str (+ (js/parseInt vanha-yhteensa) (js/parseInt lisays))))))
          (update-in polku-muutettuun-arvoon
                     (fn [_]
                       arvo))))))