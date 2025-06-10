(ns harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot
  (:require [tuck.core :as tuck]
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

(defrecord HaeTyhjatTarjouksenTiedot [])
(defrecord HaeTyhjatTarjouksenTiedotOnnistui [vastaus])
(defrecord HaeTyhjatTarjouksenTiedotEpaonnistui [vastaus])

;; Tallennetaan tarjouksen data
(defrecord TallennaTarjouksenTiedot [tarjous])
(defrecord TallennaTarjouksenTiedotOnnistui [vastaus])
(defrecord TallennaTarjouksenTiedotEpaonnistui [vastaus])


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
    (assoc app :tallennus-kesken? false)))
