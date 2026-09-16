(ns harja.tiedot.hallinta.urakkatiedot.sanktio-profiilit-tiedot
  (:require [clojure.string :as str]
            [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.domain.urakka :as urakka-domain]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]))

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

(defrecord HaeSanktioProfiilit [])
(defrecord HaeSanktioProfiilitOnnistui [vastaus])
(defrecord HaeSanktioProfiilitEpaonnistui [vastaus])
(defrecord ValitseSanktioProfiili [profiili-id])
(defrecord HaeSanktioProfiilinDetalji [profiili-id])
(defrecord HaeSanktioProfiilinDetaljiOnnistui [profiili-id vastaus])
(defrecord HaeSanktioProfiilinDetaljiEpaonnistui [profiili-id vastaus])
(defrecord PaivitaSuodatin [avain arvo])

(def urakkatyyppi-erikoistekstit
  {:teiden-hoito "MHU"
   :hoito "Alueurakka"})

(defn urakkatyyppi-teksti
  [urakkatyyppi]
  (or (get urakkatyyppi-erikoistekstit urakkatyyppi)
    (get urakka-domain/urakkatyyppi->otsikko urakkatyyppi)
    (some-> urakkatyyppi name)
    "-"))

(defn- vaikutusajan-pvm-teksti
  [arvo]
  (cond
    (nil? arvo) ""
    (string? arvo)
    (if-let [[_ vuosi kuukausi paiva] (re-matches #"(\d{4})(\d{2})(\d{2})T\d{6}" arvo)]
      (str paiva "." kuukausi "." vuosi)
      arvo)
    :else
    (pvm/pvm-opt arvo)))

(defn vaikutusajan-alku-teksti
  [{:keys [alkupvm]}]
  (vaikutusajan-pvm-teksti alkupvm))

(defn vaikutusajan-loppu-teksti
  [{:keys [loppupvm]}]
  (if loppupvm
    (vaikutusajan-pvm-teksti loppupvm)
    "toistaiseksi"))

(defn vaikutusaika-teksti
  [{:keys [alkupvm] :as profiili}]
  (str "Valitaan urakoille, joiden alkupäivä on "
    (vaikutusajan-pvm-teksti alkupvm)
    (if (:loppupvm profiili)
      (str " - " (vaikutusajan-loppu-teksti profiili))
      " tai myöhemmin")
    ". Profiili pysyy samana koko sopimuskauden."))

(defn- hae-detalji!
  [profiili-id]
  (tuck-apurit/post! :hae-sanktio-profiilin-detalji-admin
    {:sanktio-profiili-id profiili-id}
    {:onnistui (fn [vastaus] (->HaeSanktioProfiilinDetaljiOnnistui profiili-id vastaus))
     :epaonnistui (fn [vastaus] (->HaeSanktioProfiilinDetaljiEpaonnistui profiili-id vastaus))
     :paasta-virhe-lapi? true}))

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
  HaeSanktioProfiilit
  (process-event [_ app]
    (tuck-apurit/post! :hae-sanktio-profiilit-admin
      {}
      {:onnistui ->HaeSanktioProfiilitOnnistui
       :epaonnistui ->HaeSanktioProfiilitEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :haku-kaynnissa? true))

  HaeSanktioProfiilitOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false
        :profiilit vastaus)
      (paivita-valittu-profiili (:valittu-profiili-id app))))

  HaeSanktioProfiilitEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Sanktio-profiilien haku epäonnistui" :varoitus)
    (assoc app :haku-kaynnissa? false))

  ValitseSanktioProfiili
  (process-event [{:keys [profiili-id]} app]
    (let [detalji-puuttuu? (and profiili-id
                             (nil? (get-in app [:profiilin-detaljit profiili-id])))]
      (when detalji-puuttuu?
        (hae-detalji! profiili-id))
      (assoc app
        :valittu-profiili-id profiili-id
        :detalji-haku-kaynnissa? (boolean detalji-puuttuu?))))

  HaeSanktioProfiilinDetalji
  (process-event [{:keys [profiili-id]} app]
    (hae-detalji! profiili-id)
    (assoc app :detalji-haku-kaynnissa? true))

  HaeSanktioProfiilinDetaljiOnnistui
  (process-event [{:keys [profiili-id vastaus]} app]
    (assoc-in
      (assoc app :detalji-haku-kaynnissa? false)
      [:profiilin-detaljit profiili-id]
      vastaus))

  HaeSanktioProfiilinDetaljiEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Sanktio-profiilin detaljin haku epäonnistui" :varoitus)
    (assoc app :detalji-haku-kaynnissa? false))

  PaivitaSuodatin
  (process-event [{:keys [avain arvo]} app]
    (-> app
      (assoc-in [:suodattimet avain] arvo)
      (paivita-valittu-profiili (:valittu-profiili-id app)))))
