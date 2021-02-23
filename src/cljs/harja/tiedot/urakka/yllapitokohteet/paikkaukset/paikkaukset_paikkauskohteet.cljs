(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet-kartalle :as paikkauskohteet-kartalle]
            [harja.tiedot.urakka.urakka :as tila])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- fmt-aikataulu [alkupvm loppupvm tila]
  (str
    (pvm/fmt-kuukausi-ja-vuosi-lyhyt alkupvm)
    " - "
    (pvm/fmt-p-k-v-lyhyt loppupvm)
    (when-not (= "Valmis" tila)
      " (arv.)")))

(defn fmt-sijainti [tie alkuosa loppuosa alkuet loppuet]
  (str tie "/" alkuosa "/" alkuet " - " tie "/" loppuosa "/" loppuet))

(defrecord AvaaLomake [lomake])
(defrecord SuljeLomake [])
(defrecord HaePaikkauskohteet [])
(defrecord HaePaikkauskohteetOnnistui [vastaus])
(defrecord HaePaikkauskohteetEpaonnistui [vastaus])
(defrecord PaivitaLomake [lomake])
(defrecord TallennaPaikkauskohde [paikkauskohde])
(defrecord TallennaPaikkauskohdeOnnistui [vastaus])
(defrecord TallennaPaikkauskohdeEpaonnistui [vastaus])
(defrecord TilaaPaikkauskohde [paikkauskohde])
(defrecord HylkaaPaikkauskohde [paikkauskohde])
(defrecord PoistaPaikkauskohde [paikkauskohde])

(defn- hae-paikkauskohteet [urakka-id]
  (tuck-apurit/post! :paikkauskohteet-urakalle
                     {:urakka-id urakka-id}
                     {:onnistui ->HaePaikkauskohteetOnnistui
                      :epaonnistui ->HaePaikkauskohteetEpaonnistui
                      :paasta-virhe-lapi? true}))

(extend-protocol tuck/Event
  AvaaLomake
  (process-event [{lomake :lomake} app]
    (-> app
        (assoc :lomake lomake)))

  SuljeLomake
  (process-event [_ app]
    (dissoc app :lomake))

  HaePaikkauskohteet
  (process-event [_ app]
    (do
      (js/console.log "HaePaikkauskohteet -> tehdään serverihaku")
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id))
      app))

  HaePaikkauskohteetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [paikkauskohteet (map (fn [kohde]
                                 (-> kohde
                                     (assoc :formatoitu-aikataulu
                                            (fmt-aikataulu (:alkupvm kohde) (:loppupvm kohde) "Ehdotettu"))
                                     (assoc :formatoitu-sijainti
                                            (fmt-sijainti (:tie kohde) (:aosa kohde) (:losa kohde) (:aet kohde) (:let kohde)))))
                               vastaus)]
      (do
        (reset! paikkauskohteet-kartalle/karttataso-paikkauskohteet paikkauskohteet)
        ;(kartta-tiedot/zoomaa-valittuun-hallintayksikkoon-tai-urakkaan)
        (kartta-tiedot/zoomaa-geometrioihin)
        (assoc app :paikkauskohteet paikkauskohteet))))

  HaePaikkauskohteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Haku epäonnistui, vastaus " vastaus)
      app))

  PaivitaLomake
  (process-event [{lomake :lomake} app]
    (assoc app :lomake lomake))

  TallennaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [ ;; Muutetaan paikkauskohteen tilaa vain, jos sitä ei ole asetettu
          paikkauskohde (if (nil? (:paikkauskohteen-tila paikkauskohde))
                          (assoc paikkauskohde :paikkauskohteen-tila "ehdotettu")
                          paikkauskohde)
          paikkauskohde (-> paikkauskohde
                            (dissoc :sijainti)
                            (assoc :urakka-id (-> @tila/tila :yleiset :urakka :id)))]
      (do
        (js/console.log "Lähetetään paikkauskohde" (pr-str paikkauskohde))
        (tuck-apurit/post! :tallenna-paikkauskohde-urakalle
                           paikkauskohde
                           {:onnistui ->TallennaPaikkauskohdeOnnistui
                            :epaonnistui ->TallennaPaikkauskohdeEpaonnistui
                            :paasta-virhe-lapi? true})
        app)))

  TilaaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (do
      (js/console.log "Tää ei tilaa vielä mitään. Eli implementoi toteutus!" (pr-str paikkauskohde))
      app))

  HylkaaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (do
      (js/console.log "Tää ei hylkaa vielä mitään. Eli implementoi toteutus!" (pr-str paikkauskohde))
      app))

  PoistaPaikkauskohde
  (process-event [{paikkauskohde :paikkauskohde} app]
    (do
      (js/console.log "Tää ei poista vielä mitään. Eli implementoi toteutus!" (pr-str paikkauskohde))
      app))

  TallennaPaikkauskohdeOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Paikkauskohteen tallennus onnistui" vastaus)
      (hae-paikkauskohteet (-> @tila/yleiset :urakka :id))
      (dissoc app :lomake)))

  TallennaPaikkauskohdeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (js/console.log "Paikkauskohteen tallennus epäonnistui" (pr-str vastaus))
      ;;TODO: tämä antaa warningin
      ;(harja.ui.yleiset/virheviesti-sailio "Paikkauskohteen tallennus epäonnistui")
      #_(dissoc app :lomake)
      app))
  )

