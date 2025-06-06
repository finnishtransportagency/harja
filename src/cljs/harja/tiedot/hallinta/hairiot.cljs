(ns harja.tiedot.hallinta.hairiot
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]

            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.tiedot.hairioilmoitukset :as hairio-ui])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce ^{:private true} nollatut-valinnat {:rivit nil
                                             :valittu-rivi {}
                                             :muokataan false
                                             :haku-kaynnissa? true
                                             :asetetaan-hairioilmoitus? false 
                                             :tallennus-kaynnissa? false
                                             :valinnat {}
                                             :tuore-hairioilmoitus {:tyyppi :hairio :teksti nil}})
(def nakymassa? (atom false))


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord Epaonnistui [vastaus])
(defrecord Onnistui [vastaus])
(defrecord AsetaHairioilmoitus [])
(defrecord AsetetaanHairioilmoitus [])
(defrecord TuoreHairioilmoitus [ilmoitus])
(defrecord KumoaIlmoitus [])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! "Tietojen haku epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app 
    :haku-kaynnissa? false
    :tallennus-kaynnissa? false
    :asetetaan-hairioilmoitus? false))


(defn hae-tiedot [app]
  (tuck-apurit/post! app :hae-hairioilmoitukset
    {}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->Epaonnistui}))


(defn uusi-hairio [{:keys [tuore-hairioilmoitus] :as app}]
  (tuck-apurit/post! app :aseta-hairioilmoitus
    {::hairio/tyyppi (:tyyppi tuore-hairioilmoitus)
     ::hairio/viesti (:teksti tuore-hairioilmoitus)
     ::hairio/alkuaika (:alkuaika tuore-hairioilmoitus)
     ::hairio/loppuaika (:loppuaika tuore-hairioilmoitus)}

    {:onnistui ->Onnistui
     :epaonnistui ->Epaonnistui}))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (->
      (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true)))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :rivit vastaus))

  Epaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  Onnistui
  (process-event [_vastaus app]
    (assoc app
      :haku-kaynnissa? true
      :tallennus-kaynnissa? false
      :asetetaan-hairioilmoitus? false))

  AsetaHairioilmoitus
  (process-event [_ app]
    (uusi-hairio app)
    (-> app
      (assoc
        :tallennus-kaynnissa? true
        :asetetaan-hairioilmoitus? false)))

  AsetetaanHairioilmoitus
  (process-event [_ app]
    (assoc app :asetetaan-hairioilmoitus? true))

  TuoreHairioilmoitus
  (process-event [ilmoitus app]
    (assoc app :tuore-hairioilmoitus (:ilmoitus ilmoitus)))

  KumoaIlmoitus
  (process-event [_ app]
    (assoc app
      :asetetaan-hairioilmoitus? false
      :tuore-hairioilmoitus {:tyyppi :hairio :teksti nil})))




(def hairiot (atom nil))
(def asetetaan-hairioilmoitus? (atom false))




(def tyhja-hairioilmoitus {:tyyppi :hairio
                           :teksti nil
                           :alkuaika (pvm/nyt)})

(def tuore-hairioilmoitus (atom tyhja-hairioilmoitus))
(def tallennus-kaynnissa? (atom false))

(defn hae-hairiot []
  (go (let [vastaus (<! (k/post! :hae-hairioilmoitukset {}))]
        (if (k/virhe? vastaus)
          (viesti/nayta! "Häiriöilmoitusten haku epäonnistui" :warn viesti/viestin-nayttoaika-lyhyt)
          (reset! hairiot vastaus)))))





(defn aseta-hairioilmoitus [{:keys [tyyppi teksti alkuaika loppuaika]}]
  (reset! tallennus-kaynnissa? true)
  (go (let [vastaus (<! (k/post! :aseta-hairioilmoitus {::hairio/tyyppi tyyppi
                                                        ::hairio/viesti teksti
                                                        ::hairio/alkuaika alkuaika
                                                        ::hairio/loppuaika loppuaika}))]
        (reset! tallennus-kaynnissa? false)
        (reset! asetetaan-hairioilmoitus? false)
        (if (or
              (k/virhe? vastaus)
              (:virhe (first vastaus)))
          (viesti/nayta-toast!
            (str "Häiriöilmoituksen asettaminen epäonnistui!" "\n" (:virhe (first vastaus)))
            :varoitus
            (* 60 1000))
          (do (reset! hairiot vastaus)
            (reset! tuore-hairioilmoitus tyhja-hairioilmoitus)
            (hairio-ui/hae-tuorein-hairioilmoitus!))))))

(defn poista-hairioilmoitus [{:keys [id]}]
  (reset! tallennus-kaynnissa? true)
  (go (let [vastaus (<! (k/post! :aseta-hairioilmoitus-pois {::hairio/id id}))]
        (reset! tallennus-kaynnissa? false)
        (if (k/virhe? vastaus)
          (viesti/nayta! "Häiriöilmoituksen poistaminen epäonnistui!" :warn)
          (do (reset! hairiot vastaus)
            (hairio-ui/hae-tuorein-hairioilmoitus!))))))


