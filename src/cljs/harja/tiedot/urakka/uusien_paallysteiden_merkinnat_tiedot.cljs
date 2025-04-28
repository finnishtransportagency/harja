(ns harja.tiedot.urakka.uusien-paallysteiden-merkinnat-tiedot
  (:require
   [harja.tiedot.navigaatio :as nav]
   [harja.tiedot.urakka :as u]
   [harja.tyokalut.tuck :as tuck-apurit]
   [harja.ui.viesti :as viesti]
   [tuck.core :as tuck])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn kustannusten-summa [rivit avain]
  (let [summa (reduce + 0 (map avain rivit))]
    summa))

(defn rivin-kustannusten-summa [rivi avaimet]
  (reduce (fn [summa avain] (+ summa (get rivi avain 0))) 0 avaimet))

(def valinnat
  (reaction 
      {:urakka @nav/valittu-urakka
       :sopimus-id (first @u/valittu-sopimusnumero)
       :aikavali @u/valittu-aikavali
       :valittu-hoitokausi @u/valittu-hoitokausi}))

(defrecord PaivitaValinnat [valinnat])

(defrecord HaePaallystysKustannukset [])
(defrecord HaePaallystysKustannuksetOnnistui [vastaus])
(defrecord HaePaallystysKustannuksetEpaonnistui [vastaus])

(defrecord HaePaikkausKustannukset [])
(defrecord HaePaikkausKustannuksetOnnistui [vastaus])
(defrecord HaePaikkausKustannuksetEpaonnistui [vastaus])

(defrecord TallennaPaallystysKustannukset [tiedot])
(defrecord TallennaPaallystysKustannuksetOnnistui [vastaus app])
(defrecord TallennaPaallystysKustannuksetEpaonnistui [vastaus])

(defrecord TallennaPaikkausKustannukset [tiedot])
(defrecord TallennaPaikkausKustannuksetOnnistui [vastaus app])
(defrecord TallennaPaikkausKustannuksetEpaonnistui [vastaus])


(defn- hakeminen-epaonnistui-toast [vastaus]
  (viesti/nayta-toast! (str "Hakeminen epäonnistui \n Vastaus: " (pr-str vastaus)) :varoitus))


(extend-protocol tuck/Event 
  PaivitaValinnat
  (process-event [{valinnat :valinnat} app]
    (let [valinnat (merge (:valinnat app)
                     valinnat)
          paallyste-haku (tuck/send-async! ->HaePaallystysKustannukset)
          paikkaus-haku (tuck/send-async! ->HaePaikkausKustannukset)]
      (go (paallyste-haku valinnat))
      (go (paikkaus-haku valinnat))
      (assoc app :valinnat valinnat)))
  
  HaePaallystysKustannukset
  (process-event [_ app]
    (tuck-apurit/post! :hae-tiemerkinta-paallystyskohteiden-kustannukset
      {:urakka-id (:id @nav/valittu-urakka)
       :urakka-alkupvm (-> @u/valittu-aikavali first)}
      {:onnistui ->HaePaallystysKustannuksetOnnistui
       :epaonnistui ->HaePaallystysKustannuksetEpaonnistui})
    (assoc app :haku-kaynnissa? true))
  
  HaePaallystysKustannuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :haku-kaynnissa? false
      :kustannukset vastaus))

  HaePaallystysKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (hakeminen-epaonnistui-toast vastaus)
    (assoc app
      :haku-kaynnissa? false
      :kustannukset []))
  
  HaePaikkausKustannukset
  (process-event [_ app]
    (tuck-apurit/post! :hae-tiemerkinta-paikkausten-kustannukset
      {:urakka-id (:id @nav/valittu-urakka)
       :urakka-alkupvm (-> @u/valittu-aikavali first)}
      {:onnistui ->HaePaikkausKustannuksetOnnistui
       :epaonnistui ->HaePaikkausKustannuksetEpaonnistui})
    (assoc app :haku-kaynnissa? true)) 
  
  HaePaikkausKustannuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :haku-kaynnissa? false
      :paikkaus-kustannukset vastaus))
  
  HaePaikkausKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (hakeminen-epaonnistui-toast vastaus)
    (assoc app
      :haku-kaynnissa? false
      :paikkaus-kustannukset []))

  TallennaPaallystysKustannukset
  (process-event [{tiedot :tiedot} app]
    (let [payload {:tiedot tiedot}]
      (tuck-apurit/post! :tallenna-tiemerkinta-yllapitokohteiden-kustannukset
        payload
        {:onnistui ->TallennaPaallystysKustannuksetOnnistui
         :epaonnistui ->TallennaPaallystysKustannuksetEpaonnistui}))
    app)

  TallennaPaallystysKustannuksetOnnistui
  (process-event [_ app]
    ((tuck/current-send-function) (->HaePaallystysKustannukset))
    (viesti/nayta-toast! "Kustannusten tallennus onnistui!" :onnistui)
    (assoc app 
      :tallennus-kaynnissa? false 
      :tallennus-onnistui? true))

  TallennaPaallystysKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaKustannuksetEpaonnistui" vastaus)
    (viesti/nayta-toast! "Kustannusten tallennuksessa tapahtui virhe" :varoitus)
    app)

  TallennaPaikkausKustannukset
  (process-event [{tiedot :tiedot} app]
    (let [payload {:tiedot tiedot}]
      (tuck-apurit/post! :tallenna-tiemerkinta-paikkauskohteiden-kustannukset
        payload
        {:onnistui ->TallennaPaikkausKustannuksetOnnistui
         :epaonnistui ->TallennaPaikkausKustannuksetEpaonnistui}))
    app)

  TallennaPaikkausKustannuksetOnnistui
  (process-event [_ app] 
    ((tuck/current-send-function) (->HaePaikkausKustannukset))
    (viesti/nayta-toast! "Kustannusten tallennus onnistui!" :onnistui)
    (assoc app 
      :tallennus-kaynnissa? false 
      :tallennus-onnistui? true))
  
  TallennaPaikkausKustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaPaikkausKustannuksetEpaonnistui" vastaus)
    (viesti/nayta-toast! "Kustannusten tallennuksessa tapahtui virhe" :varoitus)
    app))