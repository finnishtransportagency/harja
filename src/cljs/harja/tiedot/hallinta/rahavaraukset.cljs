(ns harja.tiedot.hallinta.rahavaraukset
  (:require [cljs.core.async :refer [>! <!]]
            [harja.loki :as log]
            [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:valittu-urakka nil
                 :rahavaraukset nil
                 :tehtavat nil
                 :tallennukset {}}))

(defrecord HaeRahavaraukset [])
(defrecord HaeRahavarauksetOnnistui [vastaus])
(defrecord HaeRahavarauksetEpaonnistui [vastaus])
(defrecord HaeUrakoidenRahavaraukset [])
(defrecord HaeUrakoidenRahavarauksetOnnistui [vastaus])
(defrecord HaeUrakoidenRahavarauksetEpaonnistui [vastaus])
(defrecord ValitseUrakanRahavaraus [urakka rahavaraus valittu?])
(defrecord ValitseUrakanRahavarausOnnistui [vastaus tallennus-id])
(defrecord ValitseUrakanRahavarausEpaonnistui [vastaus tallennus-id])

(defrecord ValitseUrakka [urakka])

(extend-protocol tuck/Event
  HaeRahavaraukset
  (process-event [_ app]
    (tuck-apurit/post! :hae-rahavaraukset
      {}
      {:onnistui ->HaeRahavarauksetOnnistui
       :epaonnistui ->HaeRahavarauksetEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeRahavarauksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :rahavaraukset vastaus))

  HaeRahavarauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Rahavarauksien haku epäonnistui" :varoitus)
    app)

  HaeUrakoidenRahavaraukset
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakoiden-rahavaraukset
      {}
      {:onnistui ->HaeUrakoidenRahavarauksetOnnistui
       :epaonnistui ->HaeUrakoidenRahavarauksetEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeUrakoidenRahavarauksetOnnistui
  (process-event [{:keys [vastaus]} {:keys [valittu-urakka] :as app}]
    (let [urakat (sort-by :urakka-nimi
                   (into #{}
                     (map #(select-keys % [:urakka-id :urakka-nimi]) vastaus)))]
      (assoc app
        :urakoiden-rahavaraukset vastaus
        :urakat urakat
        :valittu-urakka (or valittu-urakka (first urakat)))))

  HaeUrakoidenRahavarauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Urakoiden rahavarauksien haku epäonnistui" :varoitus)
    app)

  ValitseUrakanRahavaraus
  (process-event [{:keys [urakka rahavaraus valittu?]} app]
    (let [tallennus-id (gensym)
          app (assoc-in app [:tallennukset-kesken tallennus-id] true)]
      (tuck-apurit/post! :paivita-urakan-rahavaraus
        {:urakka (:urakka-id urakka)
         :rahavaraus (:id rahavaraus)
         :valittu? valittu?}
        {:onnistui ->ValitseUrakanRahavarausOnnistui
         :epaonnistui ->ValitseUrakanRahavarausEpaonnistui
         :onnistui-parametrit [tallennus-id]
         :epaonnistui-parametrit [tallennus-id]
         :paasta-virhe-lapi? true})

      (if valittu?
        (update app :urakoiden-rahavaraukset
          conj {:id (:id rahavaraus)
                :nimi (:nimi rahavaraus)
                :urakka-id (:urakka-id urakka)
                :urakka-nimi (:urakka-nimi urakka)})
        (update app :urakoiden-rahavaraukset
          #(filter %2 %1)
          #(or
             (not= (:id %) (:id rahavaraus))
             (not= (:urakka-id %) (:urakka-id urakka)))))))

  ValitseUrakanRahavarausOnnistui
  (process-event [{:keys [vastaus tallennus-id]} app]
    (let [app (assoc-in app [:tallennukset-kesken tallennus-id] false)]
      (if (every? false? (:tallennukset-kesken app))
        (assoc app :urakoiden-rahavaraukset vastaus)
        app)))

  ValitseUrakanRahavarausEpaonnistui
  (process-event [{:keys [tallennus-id]} app]
    (viesti/nayta-toast! "Rahavarauksen tallennus epäonnistui" :varoitus)
    (assoc-in app [:tallennukset-kesken tallennus-id] false))

  ValitseUrakka
  (process-event [{:keys [urakka]} app]
    (assoc app :valittu-urakka urakka)))
