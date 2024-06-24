(ns harja.tiedot.urakka.mpu-kustannukset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]))

(def default-arvot {:rivit nil
                    :lomake-valinnat nil
                    :kustannukset-yhteensa nil
                    :muokataan false
                    :haku-kaynnissa? false
                    :kustannusten-selitteet ["Arvonmuutokset" "Indeksi- ja kustannustason muutokset" "Muut kustannukset"]})

(def nakymassa? (atom false))
(defonce tila (atom default-arvot))


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
(defrecord HaeKustannustiedot [])
(defrecord HaeKustannustiedotOnnistui [vastaus])
(defrecord HaeKustannustiedotEpaonnistui [vastaus])
(defrecord HaeSanktiotJaBonuksetOnnistui [vastaus])
(defrecord HaeSanktiotJaBonuksetEpaonnistui [vastaus])
(defrecord AvaaLomake [])
(defrecord SuljeLomake [])
(defrecord MuokkaaLomaketta [rivi])
(defrecord TallennaKustannus [rivi])
(defrecord TallennaKustannusOnnistui [vastaus])
(defrecord TallennaKustannusEpaonnistui [vastaus])
(defrecord HaeMPUKustannuksetOnnistui [vastaus])
(defrecord HaeMPUKustannuksetEpaonnistui [vastaus])
(defrecord HaeMPUSelitteetOnnistui [vastaus])
(defrecord HaeMPUSelitteetEpaonnistui [vastaus])


(defn- hae-mpu-selitteet
  "Hakee käyttäjien aikaisemmin kirjoittamat omat selitteet muille kustannuksille"
  [app]
  (tuck-apurit/post! app :hae-mpu-selitteet
    {:urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeMPUSelitteetOnnistui
     :epaonnistui ->HaeMPUSelitteetEpaonnistui}))


(defn- hae-paikkaus-kustannukset 
  "Hakee reikäpaikkausten ja muiden paikkausten kustannukset"
  [app]
  (tuck-apurit/post! app :hae-paikkaus-kustannukset
    {:aikavali (pvm/vuoden-aikavali @urakka/valittu-urakan-vuosi)
     :urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeKustannustiedotOnnistui
     :epaonnistui ->HaeKustannustiedotEpaonnistui}))


(defn- hae-mpu-kustannukset
  "Hakee käyttäjien lisäämät kustannukset"
  [app]
  (tuck-apurit/post! app :hae-mpu-kustannukset
    {:urakka-id @nav/valittu-urakka-id
     :vuosi @urakka/valittu-urakan-vuosi}
    {:onnistui ->HaeMPUKustannuksetOnnistui
     :epaonnistui ->HaeMPUKustannuksetEpaonnistui}))


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


(defn- tallenna-mpu-kustannus [app kustannus-tyyppi selite summa]
  (tuck-apurit/post! app :tallenna-mpu-kustannus
    {:urakka-id @nav/valittu-urakka-id
     :selite selite
     :kustannustyyppi kustannus-tyyppi
     :vuosi @urakka/valittu-urakan-vuosi
     :summa summa}
    {:onnistui ->TallennaKustannusOnnistui
     :epaonnistui ->TallennaKustannusEpaonnistui}))


(defn- generoi-avain 
  "Gridi haluaa tr elementeille uniikki id:t (:tunniste)"
  []
  (gensym "mpu-kustannus"))


(extend-protocol tuck/Event

  HaeKustannustiedot
  (process-event [_ app]
    ;; hae-paikkaus-kustannukset
    ;; hae-mpu-selitteet (autofill)
    ;; -> hae-mpu-kustannukset
    ;; -> hae-sanktiot-ja-bonukset
    ;; Kun tullaan näkymään -> Resetoi aina tila
    (let [nollaa-arvot (assoc default-arvot :haku-kaynnissa? true)]
      (hae-mpu-selitteet app)
      (hae-paikkaus-kustannukset app)
      nollaa-arvot))

  HaeKustannustiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [kustannukset (reduce + (map (fn [rivi] (or (:kokonaiskustannus rivi) 0)) vastaus))]
      ;; Hae käyttäjien lisäämät muut kustannukset 
      (hae-mpu-kustannukset app)
      ;; Tähän tulee kustannukset pelkästään työmenetelmittäin
      (assoc app
        :tyomenetelmittain (vec vastaus)
        :kustannukset-yhteensa kustannukset)))

  HaeKustannustiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  HaeMPUKustannuksetOnnistui
  (process-event [{vastaus :vastaus} {:keys [kustannukset-yhteensa] :as app}]
    (let [kustannukset (reduce + (map (fn [rivi] (or (:summa rivi) 0)) vastaus))
          kustannukset-yhteensa (+ kustannukset-yhteensa kustannukset)
          ;; Mäppää vastaus vectoriksi mikä kelpaa gridille
          mpu-kustannukset (reduce (fn [rivit r]
                                     (conj rivit
                                       {:id (:id r)
                                        :kokonaiskustannus (:summa r)
                                        :kustannustyyppi (:kustannustyyppi r)
                                        :selite (:selite r)}))
                             []
                             vastaus)]
      (hae-sanktiot-ja-bonukset app)
      (assoc app
        :muut-kustannukset mpu-kustannukset
        :kustannukset-yhteensa kustannukset-yhteensa)))

  HaeMPUKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Kustannusten haku epäonnistui, vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Haku epäonnistui, vastaus: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  HaeSanktiotJaBonuksetOnnistui
  (process-event [{vastaus :vastaus} {:keys [kustannukset-yhteensa muut-kustannukset] :as app}]
    (let [fn-laske-arvo (fn [avain]
                          (reduce + (map (fn [rivi]
                                           (when (= (:laji rivi) avain)
                                             (or (:summa rivi) 0))) vastaus)))
          ;; Lisätään sanktiot ja bonukset gridin riveihin, ehkä liian hakkerointia ehkä ei 
          bonukset (fn-laske-arvo :yllapidon_bonus)
          sanktiot (fn-laske-arvo :yllapidon_sakko)
          ;; Vähennä/Lisää vielä sanktiot ja bonukset 
          kustannukset-yhteensa (+ kustannukset-yhteensa bonukset sanktiot)
          ;; Lisää bonukset ja sanktiot muihin kustannuksiin (alempi grid)
          bonukset-ja-sanktiot [{:id (generoi-avain), :kokonaiskustannus bonukset :kustannustyyppi "Bonukset"}
                                {:id (generoi-avain), :kokonaiskustannus sanktiot :kustannustyyppi "Sanktiot"}]
          ;; Lyö muut kustannukset ja bonukset yhteen, näytetään nämä alemmassa taulukossa
          muut-kustannukset (concat bonukset-ja-sanktiot muut-kustannukset)
          ;; Sorttaa rivit aakkosilla
          rivit-sortattu (sort-by #(str/lower-case (:kustannustyyppi %)) muut-kustannukset)]

      (assoc app
        :muut-kustannukset rivit-sortattu
        :haku-kaynnissa? false
        :kustannukset-yhteensa kustannukset-yhteensa)))

  HaeSanktiotJaBonuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Sanktioiden haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Sanktioiden haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  AvaaLomake
  (process-event [_ app]
    (assoc app :muokataan true :lomake-valinnat nil))

  SuljeLomake
  (process-event [_ app]
    (assoc app :muokataan false))

  MuokkaaLomaketta
  (process-event [{rivi :rivi} app]
    (update app :lomake-valinnat merge rivi))

  TallennaKustannus
  (process-event [{rivi :rivi} app]
    (let [{:keys [kustannus-tyyppi kustannus-selite kustannus]} rivi]
      (let [selite (if (some? (seq kustannus-selite))
                     (cond
                       ;; Käyttäjä kirjoitti oman selitten
                       (string? kustannus-selite)
                       kustannus-selite
                       ;; Käyttäjä valitsi dropdown autofill itemin
                       :else
                       (second kustannus-selite))
                     ;; Käyttäjä ei kirjoittanut mitään 
                     "")]
        (tallenna-mpu-kustannus app kustannus-tyyppi selite kustannus))
      (assoc app :muokataan false :lomake-valinnat nil)))

  TallennaKustannusOnnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Kustannus tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
    (tuck/process-event (->HaeKustannustiedot) app)
    app)

  TallennaKustannusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tallennus epäonnistui, vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tallennus epäonnistui, vastaus: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  HaeMPUSelitteetOnnistui
  (process-event [{vastaus :vastaus} app]
    ;; Palautetaan tilaan kaikki mpu_kustannukset taulun selitteet (käyttäjien lisäämät selitteet) vectorina
    (let [fn-kokoa-selitteet (fn [suodata vastaus]
                               (let [vastauksen-selitteet (map :selite vastaus)
                                     ;; Suodata vakio selitteet tästä, tätä käytetään vain autofillissä
                                     suodatettu (remove (set suodata) vastauksen-selitteet)]
                                 (vec suodatettu)))]
      (assoc app
        :kayttajien-selitteet (fn-kokoa-selitteet (:kustannusten-selitteet app) vastaus))))

  HaeMPUSelitteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Selitteiden haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Selitteiden haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app))
