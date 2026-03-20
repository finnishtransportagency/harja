(ns harja.tiedot.hallinta.tyokalut.hairiot
  (:require [tuck.core :as tuck]
            [reagent.core :refer [atom]]

            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.tiedot.hairioilmoitukset :as hairio-ui]))

(defonce ^{:private true} nollatut-valinnat {:voimassaolevat-tyypeittain nil
                                             :tulevat nil
                                             :vanhat nil
                                             :valinnat {}
                                             :valittu-rivi {}
                                             :muokataan false
                                             :haku-kaynnissa? true
                                             :tallennus-kaynnissa? false
                                             :asetetaan-hairioilmoitus? false 
                                             :tuore-hairioilmoitus {:tyyppi :hairio :teksti nil}
                                             :muokattava-ilmoitus nil})
(def nakymassa? (atom false))

(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord PaivitysEpaonnistui [vastaus])
(defrecord PaivitysOnnistui [vastaus])
(defrecord AsetaHairioilmoitus [])
(defrecord AsetetaanHairioilmoitus [])
(defrecord TuoreHairioilmoitus [ilmoitus])
(defrecord KumoaIlmoitus [])
(defrecord PoistaHairio [id])
(defrecord PoistaHairioOnnistui [vastaus])
(defrecord TallennaMuokatut [muokatut])
(defrecord MuokkaaIlmoitusta [ilmoitus])
(defrecord MuokkaaIlmoitustaTiedot [tiedot])
(defrecord TallennaMuokattuIlmoitus [])
(defrecord PeruMuokkaus [])


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
     :epaonnistui ->PaivitysEpaonnistui}))


(defn uusi-hairio 
  [{:keys [tuore-hairioilmoitus] :as app}]
  (tuck-apurit/post! app :aseta-hairioilmoitus
    {::hairio/tyyppi (:tyyppi tuore-hairioilmoitus)
     ::hairio/viesti (:teksti tuore-hairioilmoitus)
     ::hairio/alkuaika (:alkuaika tuore-hairioilmoitus)
     ::hairio/loppuaika (:loppuaika tuore-hairioilmoitus)}
    {:onnistui ->PaivitysOnnistui
     :epaonnistui ->PaivitysEpaonnistui}))


(defn poista-hairio [id app]
  (tuck-apurit/post! app :aseta-hairioilmoitus-pois 
    {::hairio/id id}
    {:onnistui ->PaivitysOnnistui
     :epaonnistui ->PaivitysEpaonnistui}))


(defn tallenna-muokatut [app muokatut]
  (tuck-apurit/post! app :tallenna-hairioilmoitukset
    {:tiedot muokatut}
    {:onnistui ->PaivitysOnnistui
     :epaonnistui ->PaivitysEpaonnistui}))


(defn tallenna-muokattu-ilmoitus [{:keys [muokattava-ilmoitus] :as app}]
  (tuck-apurit/post! app :tallenna-hairioilmoitukset
    {:tiedot [(assoc muokattava-ilmoitus
               ::hairio/viesti (:viesti muokattava-ilmoitus)
               ::hairio/alkuaika (:alkuaika muokattava-ilmoitus)
               ::hairio/loppuaika (:loppuaika muokattava-ilmoitus))]}
    {:onnistui ->PaivitysOnnistui
     :epaonnistui ->PaivitysEpaonnistui}))


(defn- kasittele-hairion-virhe [vastaus app]
  (if-let [virhe (:virhe (first (filter :virhe vastaus)))]
    (do
      (viesti/nayta-toast! virhe :varoitus viesti/viestin-nayttoaika-keskipitka)
      (hae-tiedot (assoc app
                    :voimassaolevat-tyypeittain nil
                    :tulevat nil
                    :vanhat nil
                    :haku-kaynnissa? false
                    :tallennus-kaynnissa? false
                    :asetetaan-hairioilmoitus? false
                    :muokattava-ilmoitus nil)))
    (assoc app
      :voimassaolevat-tyypeittain (:voimassaolevat-tyypeittain vastaus)
      :tulevat (:tulevat vastaus)
      :vanhat (:vanhat vastaus)
      :haku-kaynnissa? false
      :tallennus-kaynnissa? false
      :asetetaan-hairioilmoitus? false
      :muokattava-ilmoitus nil)))


(extend-protocol tuck/Event
  
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (->
      (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true)))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app
      :voimassaolevat-tyypeittain (:voimassaolevat-tyypeittain vastaus)
      :tulevat (:tulevat vastaus)
      :vanhat (:vanhat vastaus)
      :haku-kaynnissa? false))

  PaivitysEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  PaivitysOnnistui
  (process-event [{:keys [vastaus]} app]
    (hairio-ui/hae-tuorein-hairioilmoitus!)
    (kasittele-hairion-virhe vastaus app))

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
      :tuore-hairioilmoitus {:tyyppi :hairio :teksti nil}))

  PoistaHairio
  (process-event [{:keys [id]} app]
    (poista-hairio id app)
    (-> app
      (assoc
        :tallennus-kaynnissa? false
        :asetetaan-hairioilmoitus? false)))

  PoistaHairioOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app
      :voimassaolevat-tyypeittain (:voimassaolevat-tyypeittain vastaus)
      :tulevat (:tulevat vastaus)
      :vanhat (:vanhat vastaus)
      :haku-kaynnissa? false
      :tallennus-kaynnissa? false
      :asetetaan-hairioilmoitus? false
      :tuore-hairioilmoitus {:tyyppi :hairio :teksti nil}))

  TallennaMuokatut
  (process-event [{:keys [muokatut]} app]
    (tallenna-muokatut app muokatut)
    app)

  MuokkaaIlmoitusta
  (process-event [{:keys [ilmoitus]} app]
    (assoc app :muokattava-ilmoitus
      (merge ilmoitus
        {:viesti (::hairio/viesti ilmoitus)
         :alkuaika (::hairio/alkuaika ilmoitus)
         :loppuaika (::hairio/loppuaika ilmoitus)})))

  MuokkaaIlmoitustaTiedot
  (process-event [{:keys [tiedot]} app]
    (assoc app :muokattava-ilmoitus (merge (:muokattava-ilmoitus app) tiedot)))

  TallennaMuokattuIlmoitus
  (process-event [_ app]
    (tallenna-muokattu-ilmoitus app)
    (assoc app
      :tallennus-kaynnissa? true
      :muokattava-ilmoitus nil))

  PeruMuokkaus
  (process-event [_ app]
    (assoc app :muokattava-ilmoitus nil)))
