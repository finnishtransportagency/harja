(ns harja.tiedot.hallinta.api-jarjestelmatunnukset
  "Hallinnoi integraatiolokin tietoja"
  (:require [cljs.core.async :refer [>! <!]]
            [taoensso.timbre :as log]
            [tuck.core :as tuck]
            [harja.tiedot.organisaatiot :refer [organisaatiot]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))

(def tila (atom {}))

(defn organisaatiovalinnat []
  (distinct (map #(select-keys % [:id :nimi]) @organisaatiot)))

(defrecord HaeUrakat [])
(defrecord HaeUrakatOnnistui [vastaus])
(defrecord HaeUrakatEpaonnistui [vastaus])

(defrecord HaeJarjestelmatunnukset [])
(defrecord HaeJarjestelmatunnuksetOnnistui [vastaus])
(defrecord HaeJarjestelmatunnuksetEpannistui [vastaus])

(defrecord HaeMahdollisetApiOikeudet [])
(defrecord HaeMahdollisetApiOikeudetOnnistui [vastaus])
(defrecord HaeMahdollisetApiOikeudetEpaonnistui [vastaus])

(defrecord TallennaJarjestelmatunnukset [muuttuneet-tunnukset paluukanava])
(defrecord TallennaJarjestelmatunnuksetOnnistui [vastaus paluukanava])
(defrecord TallennaJarjestelmatunnuksetEpaonnistui [vastaus paluukanava])

(defrecord HaeJarjestelmaTunnuksenLisaoikeudet [id])
(defrecord HaeJarjestelmaTunnuksenLisaoikeudetOnnistui [vastaus])
(defrecord HaeJarjestelmaTunnuksenLisaoikeudetEpaonnistui [vastaus])

(defrecord AsetaJarjestelmaTunnuksenLisaoikeudet [id oikeudet])
(defrecord TallennaJarjestelmaTunnuksenLisaoikeudet [muuttuneet-oikeudet kayttaja-id paluukanava])
(defrecord TallennaJarjestelmaTunnuksenLisaoikeudetOnnistui [vastaus paluukanava])
(defrecord TallennaJarjestelmaTunnuksenLisaoikeudetEpaonnistui [vastaus paluukanava])

(extend-protocol tuck/Event
  HaeUrakat
  (process-event [_ app]
    (tuck-apurit/post! app :hae-urakat-lisaoikeusvalintaan
      {}
      {:onnistui ->HaeUrakatOnnistui
       :epaonnistui ->HaeUrakatEpaonnistui}))

  HaeUrakatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :urakkavalinnat vastaus))

  HaeUrakatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Hallintapaneelin urakoiden haussa virhe" vastaus)
    (viesti/nayta-toast! "Urakoiden haussa virhe" :varoitus)
    app)

  HaeJarjestelmatunnukset
  (process-event [_ app]
    (tuck-apurit/post! app :hae-jarjestelmatunnukset
      {}
      {:onnistui ->HaeJarjestelmatunnuksetOnnistui
       :epaonnistui ->HaeJarjestelmatunnuksetEpannistui}))

  HaeJarjestelmatunnuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :jarjestelmatunnukset vastaus))

  HaeJarjestelmatunnuksetEpannistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Hallintapaneelin järjestelmäoikeuksien haussa virhe" vastaus)
    (viesti/nayta-toast! "Järjestelmäoikeuksien haussa virhe" :varoitus)
    app)

  HaeMahdollisetApiOikeudet
  (process-event [_ app]
    (tuck-apurit/post! app :hae-mahdolliset-api-oikeudet
      {}
      {:onnistui ->HaeMahdollisetApiOikeudetOnnistui
       :epaonnistui ->HaeMahdollisetApiOikeudetEpaonnistui}))

  HaeMahdollisetApiOikeudetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :mahdolliset-api-oikeudet vastaus))

  HaeMahdollisetApiOikeudetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Hallintapaneelin mahdollisten apioikeuksien haussa virhe" vastaus)
    (viesti/nayta-toast! "Apioikeuksien haussa virhe" :varoitus) app)

  TallennaJarjestelmatunnukset
  (process-event [{:keys [muuttuneet-tunnukset paluukanava]} app]
    (tuck-apurit/post! app :tallenna-jarjestelmatunnukset
      muuttuneet-tunnukset
      {:onnistui ->TallennaJarjestelmatunnuksetOnnistui
       :epaonnistui ->TallennaJarjestelmatunnuksetEpaonnistui
       :epaonnistui-parametrit [paluukanava]}))

  TallennaJarjestelmatunnuksetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :jarjestelmatunnukset vastaus))

  TallennaJarjestelmatunnuksetEpaonnistui
  (process-event [{:keys [vastaus paluukanava]} app]
    (go (>! paluukanava (:jarjestelmatunnukset app)))
    (log/error "Hallintapaneelin järjestelmätunnusten tallennuksessa virhe" vastaus)
    (viesti/nayta-toast! "Järjestelmätunnusten tallennuksessa virhe" :varoitus)
    app)

  HaeJarjestelmaTunnuksenLisaoikeudet
  (process-event [{id :id} app]
    (-> app
      (assoc-in [:jarjestelmatunnuksen-lisaoikeudet id] nil)
      (tuck-apurit/post! :hae-jarjestelmatunnuksen-lisaoikeudet
        {:kayttaja-id id}
        {:onnistui ->HaeJarjestelmaTunnuksenLisaoikeudetOnnistui
         :epaonnistui ->HaeJarjestelmaTunnuksenLisaoikeudetEpaonnistui})))

  HaeJarjestelmaTunnuksenLisaoikeudetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc-in app [:jarjestelmatunnuksen-lisaoikeudet (:kayttaja (first vastaus))] vastaus))

  HaeJarjestelmaTunnuksenLisaoikeudetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Hallintapaneelin mahdollisten apioikeuksien haussa virhe" vastaus)
    (viesti/nayta-toast! "Apioikeuksien haussa virhe" :varoitus) app)

  AsetaJarjestelmaTunnuksenLisaoikeudet
  (process-event [{id :id oikeudet :oikeudet} app]
    (assoc-in app [:jarjestelmatunnuksen-lisaoikeudet id] oikeudet))

  TallennaJarjestelmaTunnuksenLisaoikeudet
  (process-event [{:keys [muuttuneet-oikeudet kayttaja-id paluukanava]} app]
    (tuck-apurit/post! app :tallenna-jarjestelmatunnuksen-lisaoikeudet
      {:oikeudet muuttuneet-oikeudet
       :kayttaja-id kayttaja-id}
      {:onnistui ->TallennaJarjestelmaTunnuksenLisaoikeudetOnnistui
       :epaonnistui ->TallennaJarjestelmaTunnuksenLisaoikeudetEpaonnistui
       :epaonnistui-parametrit [paluukanava]}))

  TallennaJarjestelmaTunnuksenLisaoikeudetOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc-in app [:jarjestelmatunnuksen-lisaoikeudet (:kayttaja (first vastaus))] vastaus))

  TallennaJarjestelmaTunnuksenLisaoikeudetEpaonnistui
  (process-event [{:keys [vastaus paluukanava]} app]
    (go (>! paluukanava (:jarjestelmatunnuksen-lisaoikeudet app)))
    (log/error "Hallintapaneelin järjestelmätunnuksen oikeuksien tallennuksessa virhe" vastaus)
    (viesti/nayta-toast! "Järjestelmätunnusten oikeukisen tallennuksessa virhe" :varoitus)
    app))
