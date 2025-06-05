(ns harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot
  (:require [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti])
  (:require-macros [harja.tyokalut.tuck :refer [varmista-kasittelyjen-jarjestys]]
                   [harja.ui.taulukko.grid :refer [jarjesta-data triggeroi-seurannat]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn kovakoodattu-tarjous [app]
  (assoc app :tarjous [{:nimi "Kilpailutettavat hankinnat", :osio "hankintakustannukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                       ;; Rahavaraukset
                       {:nimi "Äkilliset hoitotyöt", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 1
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                       {:nimi "Vahinkojen korjaukset", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 2
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                       {:nimi "Tilaajan rahavaraus kannustinjärjestelmään", :osio "tavoitehintaiset-rahavaraukset" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id 3
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}] :yhteensa 60.00}
                       ;; Erillishankinnat
                       {:nimi "Erillishankinnat", :osio "erillishankinnat" :toimenkuva-id nil :tehtava-id nil :tehtavaryhma-id 28 :rahavaraus-id nil
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                       ;; Johto ja hallintokorvaukset eli toimenkuvat
                       {:nimi "Valmistelukausi ennen urakka-ajan alkua", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 10 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                       {:nimi "Vastuunalainen työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 2 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                       {:nimi "Päätoiminen apulainen / työnjohtaja", :osio "johto-ja-hallintokorvaus" :toimenkuva-id 4 :tehtava-id nil :tehtavaryhma-id nil :rahavaraus-id nil
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}

                       ;; Hoidonjohtopalkkio
                       {:nimi "Hoidonjohtopalkkio", :toimenkuva-id nil :tehtava-id 3061 :tehtavaryhma-id nil :rahavaraus-id nil
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 10.00} {:vuosi 2024 :summa 20.00} {:vuosi 2025 :summa 30.00}], :yhteensa 60.00}
                       {:nimi "Yhteensä tavoitehinta", :osio "yhteensa"
                        :hoitovuosittaiset-arvot [{:vuosi 2023 :summa 50.00} {:vuosi 2024 :summa 100.00} {:vuosi 2025 :summa 150.00}], :yhteensa 300.00}]))

(defn muunna-vuodet
  "Muunnetaan UI Gridin käyttämä tietomalli bäkkärin käyttämään muotoon.
  UI Grille on oltava jokainen vuosi omassa avaimeessaa tyyliin :vuosi-2023, :vuosi-2024 jne.
  Bäkärille ne niputetaan yhteen :hoitokauden_alkuvuodet-avaimen alle, joka on lista vuosista ja summista:
  [:vuosi 2023 :summa 10.00 :vuosi 2024 :summa 20.00 ...]"
  [m]
  (let [vuosi-avaimet (filter #(re-matches #":vuosi-\d{4}" (str %)) (keys m))
         vuosisummat (mapv (fn [k]
                      (let [vuosi-numero (js/parseInt (subs (str k) 7))]
                        [:vuosi vuosi-numero :summa (get m k)]))
                 vuosi-avaimet)
        muut-avaimet (apply dissoc m vuosi-avaimet)]
    (assoc muut-avaimet :hoitokauden_alkuvuodet (vec (flatten vuosisummat)))))

;; Haetaan tarjouksen data
(defrecord HaeTarjouksenTiedot [])
(defrecord HaeTarjouksenTiedotOnnistui [vastaus])
(defrecord HaeTarjouksenTiedotEpaonnistui [vastaus])

;; Tallennetaan tarjouksen data
(defrecord TallennaTarjouksenTiedot [tarjous])
(defrecord TallennaTarjouksenTiedotOnnistui [vastaus])
(defrecord TallennaTarjouksenTiedotEpaonnistui [vastaus])


(extend-protocol tuck/Event

  HaeTarjouksenTiedot
  (process-event
    [_ app]
    (let [app (-> app
                (assoc :haku-kaynnissa? false)
                (assoc :tallennus-kesken? false))]
      (kovakoodattu-tarjous app)

      ;; Hae tarjouksen tiedot bäkkäriltä, kun siellä on valmista
      #_(tuck-apurit/post! :hae-tarjouksen-tiedot
          {}
          {:onnistui ->HaeTarjouksenTiedotOnnistui
           :epaonnistui ->HaeTarjouksenTiedotEpaonnistui})))

  HaeTarjouksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))))

  HaeTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  TallennaTarjouksenTiedot
  (process-event
    [{tarjous :tarjous} app]
    (let [;; Muutetaan formilta saatu tarjous oikeaan muotoon
          muunnettu-tarjous (map #(muunna-vuodet %) tarjous)]

      (js/console.log "TallennaTarjouksenTiedot :: bäkärille kelpaava tarjous: " (pr-str muunnettu-tarjous))
      ;; Palautetaan kuitenkin tässä vaiheessa vain kovakoodattu tarjous
      (kovakoodattu-tarjous app)

      #_(tuck-apurit/post! :tallenna-tarjouksen-tiedot
          {:tarjous muunnettu-tarjous}
          {:onnistui ->TallennaTarjouksenTiedotOnnistui
           :epaonnistui ->TallennaTarjouksenTiedotEpaonnistui})))

  TallennaTarjouksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))))

  TallennaTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen tallentaminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false)))
