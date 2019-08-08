(ns harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
  (:require [clojure.string :as clj-str]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.tyokalut :as tyokalut]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.jana :as jana]))


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

(defrecord HaeKustannussuunnitelma [e!])
(defrecord HaeTavoiteJaKattohintaOnnistui [vastaus])
(defrecord HaeTavoiteJaKattohintaEpaonnistui [vastaus])
(defrecord HaeHankintakustannuksetOnnistui [vastaus e!])
(defrecord HaeHankintakustannuksetEpaonnistui [vastaus])
(defrecord LaajennaSoluaKlikattu [polku-taulukkoon rivin-id this auki?])
(defrecord PaivitaToimenpiteenHankintaMaara [osa arvo polku-taulukkoon])

(defn hankintojen-taulukko [e! toimenpiteet
                            {laskutukseen-perustuen? :laskutukseen-perustuen?}
                            toimenpide-avain
                            on-oikeus? laskutuksen-perusteella-taulukko?]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-8 col-md-8 col-lg-8"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        polku-taulukkoon (if laskutuksen-perusteella-taulukko?
                           [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen toimenpide-avain]
                           [:hankintakustannukset :toimenpiteet toimenpide-avain])
        paarivi (fn [nimi yhteensa]
                  (let [nimi-k (-> nimi (clj-str/replace #"ä" "a") (clj-str/replace #"ö" "o"))]
                    (jana/->Rivi (keyword nimi-k)
                                 [(osa/->Teksti (keyword nimi-k) (clj-str/capitalize nimi) {:class #{(sarakkeiden-leveys :nimi) "reunaton"}})
                                  (osa/->Teksti (keyword (str nimi-k "-maara-kk")) "" {:class #{(sarakkeiden-leveys :maara-kk) "reunaton"}})
                                  (osa/->Teksti (keyword (str nimi-k "-yhteensa")) yhteensa {:class #{(sarakkeiden-leveys :yhteensa) "reunaton"}})]
                                 #{"reunaton"})))
        paarivi-laajenna (fn [rivi-id vuosi yhteensa]
                           (jana/->Rivi rivi-id
                                        [(with-meta (osa/->Teksti (keyword (str vuosi "-nimi")) (str vuosi ". hoitovuosi") {:class #{(sarakkeiden-leveys :nimi) "reunaton"}})
                                                    {:sarake :nimi})
                                         (with-meta (osa/->Teksti (keyword (str vuosi "-maara-kk")) "" {:class #{(sarakkeiden-leveys :maara-kk) "reunaton"}})
                                                    {:sarake :maara})
                                         (with-meta (osa/->Laajenna (keyword (str vuosi "-yhteensa"))
                                                                    yhteensa
                                                                    #(e! (->LaajennaSoluaKlikattu polku-taulukkoon rivi-id %1 %2))
                                                                    {:class #{(sarakkeiden-leveys :yhteensa)
                                                                              "reunaton"}})
                                                    {:sarake :yhteensa})]
                                        #{"reunaton"}))
        lapsirivi (fn [nimi maara]
                    (jana/->Rivi (keyword nimi)
                                 [(osa/->Teksti (keyword (str nimi "-nimi")) (clj-str/capitalize nimi) {:class #{(sarakkeiden-leveys :nimi) "reunaton" "solu-sisenna-1"}})
                                  (osa/->Syote (keyword (str nimi "-maara-kk"))
                                               {:on-change (fn [arvo]
                                                             (when arvo
                                                               (e! (->PaivitaToimenpiteenHankintaMaara osa/*this* arvo polku-taulukkoon))))}
                                               {:on-change [:positiivinen-numero :eventin-arvo]}
                                               {:class #{(sarakkeiden-leveys :maara-kk) "reunaton"}
                                                :type "text"
                                                :disabled (not on-oikeus?)
                                                :value maara})
                                  (osa/->Teksti (keyword (str nimi "-yhteensa"))
                                                "0"
                                                {:class #{(sarakkeiden-leveys :yhteensa)
                                                          "reunaton"}})]
                                 #{"piillotettu" "reunaton"}))
        otsikkorivi (jana/->Rivi :otsikko-rivi
                                 [(osa/->Teksti :tyhja-otsikko
                                                (if laskutuksen-perusteella-taulukko?
                                                  "Laskutuksen perusteella"
                                                  (if laskutukseen-perustuen?
                                                    "Kiinteät" " "))
                                                {:class #{(sarakkeiden-leveys :nimi) "reunaton"}})
                                  (osa/->Teksti :maara-kk-otsikko "Määrä €/kk" {:class #{(sarakkeiden-leveys :maara-kk) "reunaton"}})
                                  (osa/->Teksti :yhteensa-otsikko "Yhteensä" {:class #{(sarakkeiden-leveys :yhteensa) "reunaton"}})]
                                 #{"reunaton"})
        hankinnat-hoitokausittain (group-by #(pvm/paivamaaran-hoitokausi (:pvm %))
                                            toimenpiteet)
        toimenpide-rivit (sequence
                           (comp
                             (map-indexed (fn [index [_ hoitokauden-hankinnat]]
                                            [(inc index) hoitokauden-hankinnat]))
                             (mapcat (fn [[vuosi hankinnat]]
                                       (let [rivi-id (keyword (str vuosi))]
                                         (concat [(paarivi-laajenna rivi-id vuosi (apply + (map :summa hankinnat)))]
                                                 (map (fn [hankinta]
                                                        (with-meta (lapsirivi (pvm/pvm (:pvm hankinta)) (:summa hankinta))
                                                                   {:vanhempi rivi-id}))
                                                      hankinnat))))))
                           (sort-by ffirst hankinnat-hoitokausittain))
        yhteensa-rivi (paarivi "Yhteensä" (reduce (fn [yhteensa {:keys [summa]}]
                                                    (+ yhteensa summa))
                                                  0 toimenpiteet))]
    (concat [otsikkorivi]
            (map-indexed #(update %2 :luokat conj (if (odd? %1) "pariton-jana" "parillinen-jana"))
                         toimenpide-rivit)
            [yhteensa-rivi])))

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
  HaeKustannussuunnitelma
  (process-event [{:keys [e!]} app]
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
                              :onnistui-parametrit [e!]
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
  (process-event [{:keys [vastaus e!]} {{valinnat :valinnat} :hankintakustannukset :as app}]
    (println "HAE HANKINTAKUSTANNUKSET ONNISTUI")
    (let [hankintojen-pohjadata (hankinnat-pohjadata)
          toimenpiteet (mapcat (fn [toimenpide]
                                 (tyokalut/generoi-pohjadata identity
                                                             hankintojen-pohjadata
                                                             (filter #(= (:toimenpide %) (get toimenpiteiden-avaimet toimenpide))
                                                                     (:kiinteahintaiset-tyot vastaus))
                                                             {:summa 0
                                                              :toimenpide (get toimenpiteiden-avaimet toimenpide)}))
                               toimenpiteet)
          toimenpiteet (group-by :toimenpide
                                 (map (fn [{:keys [vuosi kuukausi] :as maarat}]
                                        (assoc maarat :pvm (pvm/luo-pvm vuosi (dec kuukausi) 15)))
                                      toimenpiteet))]
      (assoc-in app [:hankintakustannukset :toimenpiteet]
                (into {}
                      (map (fn [[toimenpide-avain toimenpide-nimi]]
                             [toimenpide-avain (hankintojen-taulukko e! (get toimenpiteet toimenpide-nimi) valinnat toimenpide-avain true false)])
                           toimenpiteiden-avaimet)))))
  HaeHankintakustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;; TODO
    (println "HAE HANKINTAKUSTANNUKSET EPÄONNISTUI")
    (cljs.pprint/pprint vastaus)
    app)
  LaajennaSoluaKlikattu
  (process-event [{:keys [polku-taulukkoon rivin-id auki?]} app]
    (update-in app polku-taulukkoon (fn [rivit]
                                    (mapv (fn [rivi]
                                            (if (= (-> rivi meta :vanhempi) rivin-id)
                                              (if auki?
                                                (update rivi :luokat disj "piillotettu")
                                                (update rivi :luokat conj "piillotettu"))
                                              rivi))
                                          rivit))))
  PaivitaToimenpiteenHankintaMaara
  (process-event [{:keys [osa arvo polku-taulukkoon]} app]
    (let [[janan-index solun-index] (tyokalut/osan-polku-taulukossa (get-in app polku-taulukkoon) osa)
          vanhemman-janan-id (:vanhempi (meta (get-in app (conj polku-taulukkoon janan-index))))
          vanhempi-jana (first (tyokalut/jana (get-in app polku-taulukkoon) vanhemman-janan-id))
          vanhemman-janan-index (tyokalut/janan-index (get-in app polku-taulukkoon) vanhempi-jana)
          yhteensa-index (first (keep-indexed (fn [index osa]
                                                (when (= (-> osa meta :sarake) :yhteensa)
                                                  index))
                                              (p/janan-osat vanhempi-jana)))
          polku-muutettuun-arvoon (conj polku-taulukkoon janan-index :solut solun-index :parametrit :value)
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