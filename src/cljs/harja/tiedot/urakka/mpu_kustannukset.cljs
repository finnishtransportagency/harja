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
                     :haku-kaynnissa? false
                     :kustannusten-tyypit ["Arvonmuutokset" "Indeksi- ja kustannustason muutokset" "Muut kustannukset"]}))

(def nakymassa? (atom false))


(defn voi-tallentaa?
  "Validoi kustannuksen tallennuksen"
  [{:keys [kustannus kustannus-tyyppi kustannus-selite]}]
  (let [kustannus-validi? (and
                            (some? kustannus)
                            (integer? kustannus))
        kustannus-tyyppi-validi? (and
                                   (some? kustannus-tyyppi)
                                   (string? kustannus-tyyppi))
        kustannus-selite-validi? (if
                                   (= kustannus-tyyppi "Muut kustannukset")
                                   (some? (seq kustannus-selite))
                                   true)]
    (and
      kustannus-validi?
      kustannus-tyyppi-validi?
      kustannus-selite-validi?)))


;; Tuck 
(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord HaeSanktiotJaBonuksetOnnistui [vastaus])
(defrecord HaeSanktiotJaBonuksetEpaonnistui [vastaus])
(defrecord AvaaLomake [])
(defrecord SuljeLomake [])
(defrecord MuokkaaLomaketta [rivi])
(defrecord TallennaKustannus [rivi])
(defrecord TallennaKustannusOnnistui [vastaus])
(defrecord TallennaKustannusEpaonnistui [vastaus])
(defrecord HaeMPUSelitteetOnnistui [vastaus])
(defrecord HaeMPUSelitteetEpaonnistui [vastaus])
(defrecord HaeMPUKustannuksetOnnistui [vastaus])
(defrecord HaeMPUKustannuksetEpaonnistui [vastaus])


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


(defn- tallenna-mpu-kustannus [app selite summa]
  (tuck-apurit/post! app :tallenna-mpu-kustannus
    {:urakka-id @nav/valittu-urakka-id
     :selite selite
     :vuosi @urakka/valittu-urakan-vuosi
     :summa summa}
    {:onnistui ->TallennaKustannusOnnistui
     :epaonnistui ->TallennaKustannusEpaonnistui}))


(defn- hae-mpu-selitteet
  "Hakee käyttäjien aikaisemmin kirjoittamat omat selitteet muille kustannuksille"
  [app]
  (tuck-apurit/post! app :hae-mpu-selitteet
    {}
    {:onnistui ->HaeMPUSelitteetOnnistui
     :epaonnistui ->HaeMPUSelitteetEpaonnistui}))


(defn- hae-mpu-kustannukset
  [app]
  (tuck-apurit/post! app :hae-mpu-kustannukset
    {:urakka-id @nav/valittu-urakka-id
     :vuosi @urakka/valittu-urakan-vuosi}
    {:onnistui ->HaeMPUKustannuksetOnnistui
     :epaonnistui ->HaeMPUKustannuksetEpaonnistui}))


(defn- generoi-numero []
  (str "uuid-" (.toString (js/Math.random))))


(extend-protocol tuck/Event

  HaeTiedot
  (process-event [_ app]
    (hae-mpu-selitteet app)
    (hae-paikkaus-kustannukset app)
    (hae-mpu-kustannukset app)
    (hae-sanktiot-ja-bonukset app)
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [;; Laske kaikki kustannukset yhteen
          kustannukset (reduce + (map (fn [rivi] (or (:kokonaiskustannus rivi) 0)) vastaus))]
      (assoc app
        :rivit (vec vastaus)
        :kustannukset-yhteensa kustannukset
        :haku-kaynnissa? false)))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  HaeSanktiotJaBonuksetOnnistui
  (process-event [{vastaus :vastaus} {:keys [rivit] :as app}]
    (let [fn-laske-arvo (fn [avain]
                          (reduce + (map (fn [rivi]
                                           (when (= (:laji rivi) avain)
                                             (or (:summa rivi) 0))) vastaus)))
          ;; Lisätään sanktiot ja bonukset gridin riveihin, ehkä liian hakkerointia ehkä ei 
          bonukset (fn-laske-arvo :yllapidon_bonus)
          sanktiot (fn-laske-arvo :yllapidon_sakko)]
      (assoc app
        :rivit (conj rivit
                 {:id (generoi-numero), :kokonaiskustannus bonukset :tyomenetelma "Bonukset"}
                 {:id (generoi-numero), :kokonaiskustannus sanktiot :tyomenetelma "Sanktiot"}))))

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
    (let [{:keys [kustannus-tyyppi kustannus-selite kustannus]} rivi]
      (let [selite (if (some? (seq kustannus-selite)) kustannus-selite kustannus-tyyppi)]
        (tallenna-mpu-kustannus app selite kustannus))
      (assoc app :muokataan false :lomake-valinnat nil)))

  TallennaKustannusOnnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Kustannus tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
    (tuck/process-event (->HaeTiedot) app)
    app)

  TallennaKustannusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tallennus epäonnistui, vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tallennus epäonnistui, vastaus: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)
  
  HaeMPUKustannuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    ;;TODO
    (println "\n Vastaus: " vastaus)
    app)
  
  HaeMPUKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Kustannusten haku epäonnistui, vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Haku epäonnistui, vastaus: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  HaeMPUSelitteetOnnistui
  (process-event [{vastaus :vastaus} app]
    ;; Palautetaan tilaan kaikki mpu_kustannukset taulun selitteet vectorina
    ;; Siirtää aina "Muut kustannukset" vectorin viimeiseksi
    ;; Esimerkki vastaus: ({:selite Arvonmuutokset} {:selite Tester} {:selite Indeksi- ja kustannustason muutokset})
    ;; Esimerkki output: ["Arvonmuutokset" "Indeksi- ja kustannustason muutokset" "Tester" "Muut kustannukset"]
    (let [fn-kokoa-selitteet (fn [tyypit vastaus]
                               (let [vastauksen-selitteet (map :selite vastaus)
                                     kaikki (set (concat tyypit vastauksen-selitteet))]
                                 ;; Siirrä Muut kustannukset viimeiseksi
                                 (vec (sort-by #(if (= % "Muut kustannukset") 1 0) kaikki))))]
      (assoc app
        :kustannusten-tyypit (fn-kokoa-selitteet (:kustannusten-tyypit app) vastaus))))

  HaeMPUSelitteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Selitteiden haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Selitteiden haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app))
