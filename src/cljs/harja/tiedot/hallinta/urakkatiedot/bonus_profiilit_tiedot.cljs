(ns harja.tiedot.hallinta.urakkatiedot.bonus-profiilit-tiedot
  (:require [clojure.string :as str]
            [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.hallinta.urakkatiedot.sanktio-profiilit-tiedot :as sanktio-tiedot]))

(def tila
  (atom {:haku-kaynnissa? false
         :detalji-haku-kaynnissa? false
         :profiilit []
         :profiilin-detaljit {}
         :valittu-profiili-id nil
         :suodattimet {:teksti ""
                       :urakkatyyppi :kaikki
                       :aktiivisuus :kaikki}}))

(def nakymassa? (atom false))

(defrecord HaeBonusProfiilit [])
(defrecord HaeBonusProfiilitOnnistui [vastaus])
(defrecord HaeBonusProfiilitEpaonnistui [vastaus])
(defrecord ValitseBonusProfiili [profiili-id])
(defrecord HaeBonusProfiilinDetalji [profiili-id])
(defrecord HaeBonusProfiilinDetaljiOnnistui [profiili-id vastaus])
(defrecord HaeBonusProfiilinDetaljiEpaonnistui [profiili-id vastaus])
(defrecord PaivitaSuodatin [avain arvo])

(def urakkatyyppi-teksti sanktio-tiedot/urakkatyyppi-teksti)
(def vaikutusajan-alku-teksti sanktio-tiedot/vaikutusajan-alku-teksti)
(def vaikutusaika-teksti sanktio-tiedot/vaikutusaika-teksti)
(def vaikutusajan-loppu-teksti sanktio-tiedot/vaikutusajan-loppu-teksti)

(defn suodata-profiilit
  [{:keys [profiilit suodattimet]}]
  (let [{teksti :teksti
         suodatettu-urakkatyyppi :urakkatyyppi
         aktiivisuus :aktiivisuus} suodattimet
        teksti (str/lower-case (or teksti ""))]
    (filterv
      (fn [{profiilin-nimi :nimi
            profiilin-urakkatyyppi :urakkatyyppi
            aktiivinen :aktiivinen}]
        (and
          (or (str/blank? teksti)
            (str/includes? (str/lower-case profiilin-nimi) teksti))
          (or (= :kaikki suodatettu-urakkatyyppi)
            (= profiilin-urakkatyyppi suodatettu-urakkatyyppi))
          (or (= :kaikki aktiivisuus)
            (and (= :aktiiviset aktiivisuus) aktiivinen)
            (and (= :passiiviset aktiivisuus) (not aktiivinen)))))
      profiilit)))

(defn- hae-detalji!
  [profiili-id]
  (tuck-apurit/post! :hae-bonus-profiilin-detalji-admin
    {:bonus-profiili-id profiili-id}
    {:onnistui (fn [vastaus] (->HaeBonusProfiilinDetaljiOnnistui profiili-id vastaus))
     :epaonnistui (fn [vastaus] (->HaeBonusProfiilinDetaljiEpaonnistui profiili-id vastaus))
     :paasta-virhe-lapi? true}))

(defn- valitse-nakyva-profiili-id
  [app ehdotettu-profiili-id]
  (let [suodatetut-profiilit (suodata-profiilit app)
        nakyvat-profiili-idt (into #{} (map :id) suodatetut-profiilit)]
    (cond
      (and ehdotettu-profiili-id
        (contains? nakyvat-profiili-idt ehdotettu-profiili-id))
      ehdotettu-profiili-id

      (seq suodatetut-profiilit)
      (:id (first suodatetut-profiilit))

      :else nil)))

(defn- paivita-valittu-profiili
  [app ehdotettu-profiili-id]
  (let [valittu-profiili-id (valitse-nakyva-profiili-id app ehdotettu-profiili-id)
        sama-profiili-valittuna? (= valittu-profiili-id (:valittu-profiili-id app))
        detalji-puuttuu? (and valittu-profiili-id
                           (nil? (get-in app [:profiilin-detaljit valittu-profiili-id])))
        hae-detalji? (and detalji-puuttuu?
                       (or (not sama-profiili-valittuna?)
                         (not (:detalji-haku-kaynnissa? app))))]
    (when hae-detalji?
      (hae-detalji! valittu-profiili-id))
    (assoc app
      :valittu-profiili-id valittu-profiili-id
      :detalji-haku-kaynnissa? (boolean hae-detalji?))))

(extend-protocol tuck/Event
  HaeBonusProfiilit
  (process-event [_ app]
    (tuck-apurit/post! :hae-bonus-profiilit-admin
      {}
      {:onnistui ->HaeBonusProfiilitOnnistui
       :epaonnistui ->HaeBonusProfiilitEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :haku-kaynnissa? true))

  HaeBonusProfiilitOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false
        :profiilit vastaus)
      (paivita-valittu-profiili (:valittu-profiili-id app))))

  HaeBonusProfiilitEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Bonus-profiilien haku epäonnistui" :varoitus)
    (assoc app :haku-kaynnissa? false))

  ValitseBonusProfiili
  (process-event [{:keys [profiili-id]} app]
    (let [detalji-puuttuu? (and profiili-id
                             (nil? (get-in app [:profiilin-detaljit profiili-id])))]
      (when detalji-puuttuu?
        (hae-detalji! profiili-id))
      (assoc app
        :valittu-profiili-id profiili-id
        :detalji-haku-kaynnissa? (boolean detalji-puuttuu?))))

  HaeBonusProfiilinDetalji
  (process-event [{:keys [profiili-id]} app]
    (hae-detalji! profiili-id)
    (assoc app :detalji-haku-kaynnissa? true))

  HaeBonusProfiilinDetaljiOnnistui
  (process-event [{:keys [profiili-id vastaus]} app]
    (assoc-in
      (assoc app :detalji-haku-kaynnissa? false)
      [:profiilin-detaljit profiili-id]
      vastaus))

  HaeBonusProfiilinDetaljiEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Bonus-profiilin detaljin haku epäonnistui" :varoitus)
    (assoc app :detalji-haku-kaynnissa? false))

  PaivitaSuodatin
  (process-event [{:keys [avain arvo]} app]
    (-> app
      (assoc-in [:suodattimet avain] arvo)
      (paivita-valittu-profiili (:valittu-profiili-id app)))))
