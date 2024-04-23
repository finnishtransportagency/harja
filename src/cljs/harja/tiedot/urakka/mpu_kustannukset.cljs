(ns harja.tiedot.urakka.mpu-kustannukset
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [reagent.ratom :refer [reaction]]))


(defonce tila (atom {:rivit nil
                     :lomake-valinnat nil
                     :kustannukset-yhteensa nil 
                     :muokataan false
                     :haku-kaynnissa? false}))

(def nakymassa? (atom false))
(def kustannusten-tyypit #{"Arvonmuutokset" "Indeksi- ja kustannustason muutokset" "Muut kustannukset"}) 


(defn voi-tallentaa?
  "Validoi kustannuksen tallennuksen"
  [{:keys [kustannus kustannus-tyyppi]}]
  (let [kustannus-validi? (and
                            (some? kustannus)
                            (integer? kustannus))
        kustannus-tyyppi-validi? (and
                                   (some? kustannus-tyyppi)
                                   (string? kustannus-tyyppi))]
    (and
      kustannus-validi?
      kustannus-tyyppi-validi?)))


;; Tuck 
(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord HaeSanktiotJaBonukset [])
(defrecord HaeSanktiotJaBonuksetOnnistui [vastaus])
(defrecord HaeSanktiotJaBonuksetEpaonnistui [vastaus])
(defrecord AvaaLomake [])
(defrecord SuljeLomake [])
(defrecord MuokkaaLomaketta [rivi])
(defrecord TallennaKustannus [rivi])
(defrecord TallennaKustannusOnnistui [vastaus])
(defrecord TallennaKustannusEpaonnistui [vastaus])


(defn- hae-paikkaus-kustannukset [app]
  (tuck-apurit/post! app :hae-paikkaus-kustannukset
    {:aikavali (pvm/vuoden-aikavali @urakka/valittu-urakan-vuosi)
     :urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(defn- hae-sanktiot-ja-bonukset [app]
  (tuck-apurit/post! app
    :hae-urakan-sanktiot-ja-bonukset
    {:urakka-id @nav/valittu-urakka-id
     :alku      (first (pvm/vuoden-aikavali @urakka/valittu-urakan-vuosi))
     :loppu     (second (pvm/vuoden-aikavali @urakka/valittu-urakan-vuosi))
     :vain-yllapitokohteettomat? false
     :hae-sanktiot? true
     :hae-bonukset? true}
    {:onnistui ->HaeSanktiotJaBonuksetOnnistui
     :epaonnistui ->HaeSanktiotJaBonuksetEpaonnistui}))


(extend-protocol tuck/Event

  HaeTiedot
  (process-event [_ app]
    (hae-paikkaus-kustannukset app)
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [;; Laske kaikki kustannukset yhteen
          kustannukset (reduce + (map (fn [rivi] (or (:kokonaiskustannus rivi) 0)) vastaus))]
      (println "\n\n vecci: " (vec vastaus))
      (assoc app
        :rivit (vec vastaus)
        :kustannukset-yhteensa kustannukset
        :haku-kaynnissa? false)))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  HaeSanktiotJaBonukset
  (process-event [_ app]
    (hae-sanktiot-ja-bonukset app)
    (assoc app :haku-kaynnissa? true))

  HaeSanktiotJaBonuksetOnnistui
  (process-event [{vastaus :vastaus} {:keys [rivit] :as app}]
    (let [fn-laske-arvo (fn [avain]
                          (reduce + (map (fn [rivi]
                                           (when (= (:laji rivi) avain)
                                             (or (:summa rivi) 0))) vastaus)))
          ;; Lisätään sanktiot ja bonukset gridin riveihin, ehkä liian hakkerointia ehkä ei 
          bonukset (fn-laske-arvo :yllapidon_bonus)
          sanktiot (fn-laske-arvo :yllapidon_sakko)
          id (inc (apply max (map :id rivit)))]
      (assoc app
        :rivit (conj rivit
                 {:id id, :kokonaiskustannus bonukset :tyomenetelma "Bonukset"}
                 {:id (inc id), :kokonaiskustannus sanktiot :tyomenetelma "Sanktiot"}))))

  HaeSanktiotJaBonuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Sanktioiden haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Sanktioiden haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  AvaaLomake
  (process-event [_ app]
    (assoc app :muokataan true))

  SuljeLomake
  (process-event [_ app]
    (assoc app :muokataan false))

  MuokkaaLomaketta
  (process-event [{rivi :rivi} app]
    (update app :lomake-valinnat merge rivi))

  TallennaKustannus
  (process-event [{rivi :rivi} app]
    (let [{:keys [kustannus-tyyppi kustannus]} rivi]
      (println "-> TallennaKustannus " kustannus-tyyppi kustannus)
      (assoc app :muokataan false)))

  TallennaKustannusOnnistui
  (process-event [_ app]
    (let []
      (viesti/nayta-toast! "Kustannus tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
      ;; TODO , hae
      app))

  TallennaKustannusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tallennus epäonnistui, vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tallennus epäonnistui, vastaus: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app))
