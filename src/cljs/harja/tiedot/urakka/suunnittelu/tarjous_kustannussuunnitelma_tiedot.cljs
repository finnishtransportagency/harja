(ns harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot
  (:require [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]))

(defn muunna-vuodet
  "Muunnetaan UI Gridin käyttämä tietomalli bäkkärin käyttämään muotoon.
  UI Grille on oltava jokainen vuosi omassa avaimeessaa tyyliin :vuosi-2023, :vuosi-2024 jne.
  Bäkärille ne niputetaan yhteen :hoitokauden_alkuvuodet-avaimen alle, joka on lista vuosista ja summista:
  [:vuosi 2023 :summa 10.00 :vuosi 2024 :summa 20.00 ...]"
  [m]
  (let [vuosi-avaimet (filter #(re-matches #":vuosi-\d{4}" (str %)) (keys m))
        vuosisummat (mapv (fn [k]
                            (let [vuosi-numero (js/parseInt (subs (str k) 7))]
                              {:vuosi vuosi-numero :summa (get m k)}))
                      vuosi-avaimet)
        muut-avaimet (apply dissoc m vuosi-avaimet)]
    (assoc muut-avaimet :hoitovuosittaiset-arvot (vec (flatten vuosisummat)))))

;; Haetaan tarjouksen data
(defrecord HaeTarjouksenTiedot [])
(defrecord HaeTarjouksenTiedotOnnistui [vastaus])
(defrecord HaeTarjouksenTiedotEpaonnistui [vastaus])

;; Haetaan kustannussuunnitelman tiedot
(defrecord HaeKustannussuunnitelmanTiedot [])
(defrecord HaeKustannussuunnitelmanTiedotOnnistui [vastaus])
(defrecord HaeKustannussuunnitelmanTiedotEpaonnistui [vastaus])


(defrecord HaeTyhjatTarjouksenTiedot [])
(defrecord HaeTyhjatTarjouksenTiedotOnnistui [vastaus])
(defrecord HaeTyhjatTarjouksenTiedotEpaonnistui [vastaus])

;; Tallennetaan tarjouksen data
(defrecord TallennaTarjouksenTiedot [tarjous])
(defrecord TallennaTarjouksenTiedotOnnistui [vastaus])
(defrecord TallennaTarjouksenTiedotEpaonnistui [vastaus])

;; Tallennetaan kilpailutettavat hankinnat kustannussuunnitelmaan
(defrecord TallennaKilpailutettavatHankinnat [kilpailutettavat-hankinnat])
(defrecord TallennaKilpailutettavatHankinnatOnnistui [vastaus])
(defrecord TallennaKilpailutettavatHankinnatEpaonnistui [vastaus])


(defrecord ValitseHoitokausiKustannussuunnitelmaan [vuosi])

(defn hae-kustannussuunnitelman-tiedot
  "Haetaan kustannussuunnitelman tiedot, jotta voidaan näyttää ne UI Gridissä.
  Vuosi on hoitovuoden alkuvuosi, jolle kustannussuunnitelma haetaan."
  [urakka-id vuosi]
  (tuck-apurit/post! :hae-kustannussuunnitelman-tiedot
    {:urakka-id urakka-id :hoitovuoden-alkuvuosi vuosi}
    {:onnistui ->HaeKustannussuunnitelmanTiedotOnnistui
     :epaonnistui ->HaeKustannussuunnitelmanTiedotEpaonnistui}))

(extend-protocol tuck/Event

  HaeTarjouksenTiedot
  (process-event
    [_ app]
    (tuck-apurit/post! :hae-tarjouksen-tiedot
      {:urakka-id (-> @tila/yleiset :urakka :id)}
      {:onnistui ->HaeTarjouksenTiedotOnnistui
       :epaonnistui ->HaeTarjouksenTiedotEpaonnistui})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :tallennus-kesken? false)))

  HaeTarjouksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))))

  HaeTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  HaeTyhjatTarjouksenTiedot
  (process-event
    [_ app]
    (tuck-apurit/post! :hae-tyhjat-tarjouksen-tiedot
      {:urakka-id (-> @tila/yleiset :urakka :id)}
      {:onnistui ->HaeTyhjatTarjouksenTiedotOnnistui
       :epaonnistui ->HaeTyhjatTarjouksenTiedotEpaonnistui})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :tallennus-kesken? false)))

  HaeTyhjatTarjouksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))))

  HaeTyhjatTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  TallennaTarjouksenTiedot
  (process-event
    [{tarjous :tarjous} app]
    (let [;; Muutetaan formilta saatu tarjous oikeaan muotoon
          muunnettu-tarjous {:tarjous (map #(muunna-vuodet %) tarjous)}
          muunnettu-tarjous (assoc muunnettu-tarjous :urakka-id (-> @tila/yleiset :urakka :id))]
      (tuck-apurit/post! :tallenna-tarjouksen-tiedot
        muunnettu-tarjous
        {:onnistui ->TallennaTarjouksenTiedotOnnistui
         :epaonnistui ->TallennaTarjouksenTiedotEpaonnistui})
      (assoc app :tallennus-kesken? true)))

  TallennaTarjouksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :tallennus-kesken? false)
      (assoc :tarjous (:tarjous vastaus))))

  TallennaTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen tallentaminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :tallennus-kesken? false))

  HaeKustannussuunnitelmanTiedot
  (process-event
    [_ app]
    (hae-kustannussuunnitelman-tiedot (-> @tila/yleiset :urakka :id) (pvm/vuosi (first (:valittu-hoitokausi app))))
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :tallennus-kesken? false)))

  HaeKustannussuunnitelmanTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))))

  HaeKustannussuunnitelmanTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  TallennaKilpailutettavatHankinnat
  (process-event
    [{kilpailutettavat-hankinnat :kilpailutettavat-hankinnat} app]
    (let [vuosi (pvm/vuosi (first (:valittu-hoitokausi app)))]
      (tuck-apurit/post! :tallenna-kilpailutettavat-hankinnat
        {:urakka-id (-> @tila/yleiset :urakka :id)
         :hoitovuoden-alkuvuosi vuosi
         :toimenpiteet kilpailutettavat-hankinnat}
        {:onnistui ->TallennaKilpailutettavatHankinnatOnnistui
         :epaonnistui ->TallennaKilpailutettavatHankinnatEpaonnistui})
      (assoc app :tallennus-kesken? true)))

  TallennaKilpailutettavatHankinnatOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :tallennus-kesken? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))))

  TallennaKilpailutettavatHankinnatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen tallentaminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :tallennus-kesken? false))

  ValitseHoitokausiKustannussuunnitelmaan
  (process-event [{vuosi :vuosi} app]
    (let [app (-> app
                (assoc :valittu-kuukausi nil)
                ;; Lupaukset on kiinteässä linkissä kustannusten seurannan kanssa joten tarvitaan hoitokaudellekin sama avain
                (assoc :valittu-hoitokausi [(pvm/hoitokauden-alkupvm vuosi)
                                            (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                (assoc :nykyhetki (pvm/nyt))
                (assoc :haku-kaynnissa? true)
                (assoc :hoitokauden-alkuvuosi vuosi))]
      ;; Haetaan kaikki välikatselmuksessa tarvittavat tiedot
      (hae-kustannussuunnitelman-tiedot (-> @tila/yleiset :urakka :id) vuosi)
      (assoc app :haku-kaynnissa? true))))
