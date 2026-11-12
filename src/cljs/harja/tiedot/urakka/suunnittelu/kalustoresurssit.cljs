(ns harja.tiedot.urakka.suunnittelu.kalustoresurssit
  "Suunnittelun kalustoresurssien näkymän tila ja Tuck-eventit MHU26-urakoille."
  (:require [tuck.core :as tuck]

            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.navigaatio :as nav]
            [harja.fmt :as fmt]))

(defonce nakymassa? (atom false))

(defrecord HaeKalustoresurssit [])
(defrecord HaeKalustoresurssitOnnistui [vastaus])
(defrecord HaeKalustoresurssitEpaonnistui [vastaus])

(defrecord AloitaMuokkaus [])
(defrecord PaivitaMaara [avain arvo])
(defrecord PeruutaMuokkaus [])

(defrecord TallennaKalustoresurssit [])
(defrecord TallennaKalustoresurssitOnnistui [vastaus])
(defrecord TallennaKalustoresurssitEpaonnistui [vastaus])

(defn vastaus->maarat
  "Muuntaa palvelimen palauttaman rivilistan hoitoluokkaryhmä->määrä -mapiksi."
  [vastaus]
  (into {}
    (map (fn [{:keys [hoitoluokkaryhma maara]}]
           [hoitoluokkaryhma maara]))
    vastaus))

(defn hae-kalustoresurssit []
  (tuck-apurit/post! :hae-urakan-kalustoresurssit
    {:urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeKalustoresurssitOnnistui
     :epaonnistui ->HaeKalustoresurssitEpaonnistui
     :paasta-virhe-lapi? true}))

(extend-protocol tuck/Event

  HaeKalustoresurssit
  (process-event [_ app]
    (hae-kalustoresurssit)
    (assoc app :haku-kaynnissa? true))

  HaeKalustoresurssitOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [maarat (vastaus->maarat vastaus)]
      (-> app
        (assoc :haku-kaynnissa? false)
        (assoc :tallennetut-maarat maarat)
        (assoc :rivit maarat)
        (assoc :muokkaustila false))))

  HaeKalustoresurssitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Kalustoresurssien hakeminen epäonnistui: " (pr-str vastaus))
      :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  AloitaMuokkaus
  (process-event [_ app]
    (-> app
      (assoc :muokkaustila true)
      (assoc :rivit (or (:tallennetut-maarat app) {}))))

  PaivitaMaara
  (process-event [{avain :avain arvo :arvo} app]
    (assoc-in app [:rivit avain] (fmt/kokonaisluku-opt arvo)))

  PeruutaMuokkaus
  (process-event [_ app]
    (-> app
      (assoc :muokkaustila false)
      (assoc :rivit (or (:tallennetut-maarat app) {}))))

  TallennaKalustoresurssit
  (process-event [_ app]
    (let [maarat (:rivit app)
          kalustoresurssit (mapv (fn [[hoitoluokkaryhma maara]]
                                   {:hoitoluokkaryhma hoitoluokkaryhma
                                    :maara maara})
                             maarat)]
      (tuck-apurit/post! :tallenna-urakan-kalustoresurssit
        {:urakka-id @nav/valittu-urakka-id
         :kalustoresurssit kalustoresurssit}
        {:onnistui ->TallennaKalustoresurssitOnnistui
         :epaonnistui ->TallennaKalustoresurssitEpaonnistui
         :paasta-virhe-lapi? true})
      (assoc app :tallennus-kaynnissa? true)))

  TallennaKalustoresurssitOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Kalustoresurssit tallennettiin onnistuneesti.")
    (let [maarat (vastaus->maarat vastaus)]
      (-> app
        (assoc :tallennus-kaynnissa? false)
        (assoc :tallennetut-maarat maarat)
        (assoc :rivit maarat)
        (assoc :muokkaustila false))))

  TallennaKalustoresurssitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Kalustoresurssien tallentaminen epäonnistui: " (pr-str vastaus))
      :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :tallennus-kaynnissa? false)))
