(ns harja.tiedot.urakka.suunnittelu.kalustoresurssit
  "Suunnittelun kalustoresurssien näkymän tila ja Tuck-eventit MHU26-urakoille."
  (:require [clojure.string :as str]
            [harja.ui.viesti :as viesti]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]))

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

(defn- urakka-id []
  (:id (-> @tiedot/tila :yleiset :urakka)))

(defn vastaus->maarat
  "Muuntaa palvelimen palauttaman rivilistan hoitoluokkaryhmä->määrä -mapiksi."
  [vastaus]
  (into {}
    (map (fn [{:keys [hoitoluokkaryhma maara]}]
           [hoitoluokkaryhma maara]))
    vastaus))

(defn hae-kalustoresurssit []
  (tuck-apurit/post! :hae-urakan-kalustoresurssit
    {:urakka-id (urakka-id)}
    {:onnistui ->HaeKalustoresurssitOnnistui
     :epaonnistui ->HaeKalustoresurssitEpaonnistui
     :paasta-virhe-lapi? true}))

(defn- parsi-maara
  "Parsii käyttäjän syöttämän arvon kokonaisluvuksi tai nil:ksi, jos arvo on tyhjä."
  [arvo]
  (cond
    (nil? arvo) nil
    (and (string? arvo) (str/blank? arvo)) nil
    (number? arvo) arvo
    :else (let [n (js/parseInt arvo 10)]
            (when-not (js/isNaN n) n))))

(extend-protocol tuck/Event

  HaeKalustoresurssit
  (process-event [_ app]
    (hae-kalustoresurssit)
    (assoc app :haku-kaynnissa? true))

  HaeKalustoresurssitOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tallennetut-maarat (vastaus->maarat vastaus))
      (assoc :muokkaustila? false)
      (dissoc :muokkausbufferi)))

  HaeKalustoresurssitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Kalustoresurssien hakeminen epäonnistui: " (pr-str vastaus))
      :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  AloitaMuokkaus
  (process-event [_ app]
    (-> app
      (assoc :muokkaustila? true)
      (assoc :muokkausbufferi (or (:tallennetut-maarat app) {}))))

  PaivitaMaara
  (process-event [{avain :avain arvo :arvo} app]
    (assoc-in app [:muokkausbufferi avain] (parsi-maara arvo)))

  PeruutaMuokkaus
  (process-event [_ app]
    (-> app
      (assoc :muokkaustila? false)
      (dissoc :muokkausbufferi)))

  TallennaKalustoresurssit
  (process-event [_ app]
    (let [maarat (:muokkausbufferi app)
          kalustoresurssit (mapv (fn [[hoitoluokkaryhma maara]]
                                   {:hoitoluokkaryhma hoitoluokkaryhma
                                    :maara maara})
                             maarat)]
      (tuck-apurit/post! :tallenna-urakan-kalustoresurssit
        {:urakka-id (urakka-id)
         :kalustoresurssit kalustoresurssit}
        {:onnistui ->TallennaKalustoresurssitOnnistui
         :epaonnistui ->TallennaKalustoresurssitEpaonnistui
         :paasta-virhe-lapi? true})
      (assoc app :tallennus-kaynnissa? true)))

  TallennaKalustoresurssitOnnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Kalustoresurssit tallennettiin onnistuneesti.")
    (-> app
      (assoc :tallennus-kaynnissa? false)
      (assoc :muokkaustila? false)
      (assoc :tallennetut-maarat (vastaus->maarat vastaus))
      (dissoc :muokkausbufferi)))

  TallennaKalustoresurssitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Kalustoresurssien tallentaminen epäonnistui: " (pr-str vastaus))
      :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :tallennus-kaynnissa? false)))
