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

(defn- fmt-aikataulu [alkuaika loppuaika tila]
  (str
    (pvm/fmt-kuukausi-ja-vuosi-lyhyt alkuaika)
    " - "
    (pvm/fmt-p-k-v-lyhyt loppuaika)
    (when-not (= "Valmis" tila)
      " (arv.)")))

(defn- fmt-sijainti [tie alkuosa loppuosa alkuet loppuet]
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
      (tuck-apurit/post! :paikkauskohteet-urakalle
                         {:urakka-id (-> @tila/yleiset :urakka :id)}
                         {:onnistui ->HaePaikkauskohteetOnnistui
                          :epaonnistui ->HaePaikkauskohteetEpaonnistui
                          :paasta-virhe-lapi? true})
      app))

  HaePaikkauskohteetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [paikkauskohteet (map (fn [kohde]
                                 (-> kohde
                                     (assoc :formatoitu-aikataulu
                                            (fmt-aikataulu (:alkuaika kohde) (:loppuaika kohde) "Ehdotettu"))
                                     (assoc :formatoitu-sijainti
                                            (fmt-sijainti (:tie kohde) (:aosa kohde) (:losa kohde) (:aet kohde) (:let kohde)))))
                               vastaus)]
      (do
        (reset! paikkauskohteet-kartalle/karttataso-paikkauskohteet paikkauskohteet)
        (kartta-tiedot/zoomaa-valittuun-hallintayksikkoon-tai-urakkaan)
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
    (do
      (println "Lähetetään paikkauskohde" (pr-str paikkauskohde))
      (tuck-apurit/post! :tallenna-paikkauskohde-urakalle
                         {:paikkaukohde paikkauskohde}
                         {:onnistui ->TallennaPaikkauskohdeOnnistui
                          :epaonnistui ->TallennaPaikkauskohdeEpaonnistui
                          :paasta-virhe-lapi? true})
      app))

  TallennaPaikkauskohdeOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (println "Paikkauskohteen tallennus onnistui" vastaus)
      (dissoc app :lomake)))

  TallennaPaikkauskohdeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (println "Paikkauskohteen tallennus epäonnistui" vastaus)
      (harja.ui.yleiset/virheviesti-sailio "Paikkauskohteen tallennus epäonnistui")
      (dissoc app :lomake)))
  )

