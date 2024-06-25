(ns harja.tiedot.urakka.mpu-kustannukset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs-time.core :as t]
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
                            (number? kustannus))
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
(defrecord HaeKustannuksetYhteensaOnnistui [vastaus])
(defrecord HaeKustannuksetYhteensaEpaonnistui [vastaus])
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


(defn- hae-mpu-selitteet
  "Hakee käyttäjien aikaisemmin kirjoittamat omat selitteet muille kustannuksille"
  [app]
  (tuck-apurit/post! app :hae-mpu-selitteet
    {:urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeMPUSelitteetOnnistui
     :epaonnistui ->HaeMPUSelitteetEpaonnistui}))


(defn- hae-paikkaus-kustannukset 
  "Hakee reikäpaikkausten ja muiden paikkausten kustannukset"
  [app aikavali vuosi callback]
  (tuck-apurit/post! app :hae-paikkaus-kustannukset
    {:aikavali aikavali 
     :vuosi vuosi
     :urakka-id @nav/valittu-urakka-id}
    callback))


(defn- hae-sanktiot-ja-bonukset [app]
  (tuck-apurit/post! app :hae-urakan-sanktiot-ja-bonukset
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
    ;; hae-mpu-selitteet (autofill)
    ;; -> hae-paikkaus-kustannukset
    ;; -> hae-sanktiot-ja-bonukset
    ;; Kun tullaan näkymään -> Resetoi aina tila
    (let [nollaa-arvot (assoc default-arvot :haku-kaynnissa? true)
          aikavali (pvm/vuoden-aikavali @urakka/valittu-urakan-vuosi)
          vuosi @urakka/valittu-urakan-vuosi
          urakka @nav/valittu-urakka
          aikavali-koko-urakka [(:alkupvm urakka) (:loppupvm urakka)]

          ;; Nouda valitun vuoden tiedot
          callback-valittu {:onnistui ->HaeKustannustiedotOnnistui
                            :epaonnistui ->HaeKustannustiedotEpaonnistui}

          ;; Nouda koko urakka-ajan kustannukset yhteensä (pelkästään summa näytetään)
          callback-koko-urakka {:onnistui ->HaeKustannuksetYhteensaOnnistui
                                :epaonnistui ->HaeKustannuksetYhteensaEpaonnistui}]
      (hae-mpu-selitteet app)
      (hae-paikkaus-kustannukset app aikavali vuosi callback-valittu)
      (hae-paikkaus-kustannukset app aikavali-koko-urakka nil callback-koko-urakka)
      nollaa-arvot))

  HaeKustannuksetYhteensaOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [kustannukset-yhteensa (reduce + (map (fn [rivi] (or (:kokonaiskustannus rivi) 0)) vastaus))]
      (assoc app
        :urakka-ajan-kustannukset-yhteensa kustannukset-yhteensa)))
  
  HaeKustannuksetYhteensaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tietojen haku epäonnistui (koko urakka): " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui (koko urakka): " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  HaeKustannustiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [kustannukset-yhteensa (reduce + (map (fn [rivi] (or (:kokonaiskustannus rivi) 0)) vastaus))

          ;; Mäppää vastaus vectoreihin mikä kelpaa gridille
          muut-kustannukset (reduce (fn [rivit r]
                                      ;; Käyttäjien lisäämät muut kustannukset
                                      (conj rivit
                                        {:id (generoi-avain)
                                         :kokonaiskustannus (:kokonaiskustannus r)
                                         :kustannustyyppi (:kustannustyyppi r)
                                         :selite (:selite r)}))
                              []
                              ;; Muut kustannukset eivät sisällä työmenetelmää
                              (filter (fn [r] (empty? (:tyomenetelma r))) vastaus))

          tyomenetelmittain (reduce (fn [rivit r]
                                      ;; Työmenetelmittäiset kustannukset tulee omalle gridille
                                      ;; Tässä muut paikkaukset sekä reikäpaikkaukset
                                      (conj rivit
                                        {:id (generoi-avain)
                                         :tyomenetelma (:tyomenetelma r)
                                         :kokonaiskustannus (:kokonaiskustannus r)
                                         :kustannustyyppi (:kustannustyyppi r)
                                         :selite (:selite r)}))
                              []
                              ;; Kaikilla muilla kustannuksilla olemassa työmenetelmä 
                              (filter (fn [r] (and (:tyomenetelma r) (seq (:tyomenetelma r)))) vastaus))]

      (hae-sanktiot-ja-bonukset app)
      (assoc app
        :muut-kustannukset muut-kustannukset
        :tyomenetelmittain tyomenetelmittain
        :kustannukset-yhteensa kustannukset-yhteensa)))

  HaeKustannustiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    app)

  HaeSanktiotJaBonuksetOnnistui
  (process-event [{vastaus :vastaus} {:keys [kustannukset-yhteensa muut-kustannukset] :as app}]
    (let [fn-laske-arvo (fn [avain]
                          (reduce + (map (fn [rivi]
                                           (when (= (:laji rivi) avain)
                                             (or (:summa rivi) 0))) vastaus)))
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
