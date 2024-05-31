(ns harja.tiedot.hallinta.rahavaraukset
  (:require [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [>! <!]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def tila (atom {:valittu-urakka nil
                 :rahavaraukset nil
                 :tehtavat nil
                 :haku-kaynnissa? false
                 :tallennus-kesken? false}))

(defrecord HaeRahavaraukset [])
(defrecord HaeRahavarauksetOnnistui [vastaus])
(defrecord HaeRahavarauksetEpaonnistui [vastaus])
(defrecord HaeRahavarauksetTehtavineen [])
(defrecord HaeRahavarauksetTehtavineenOnnistui [vastaus])
(defrecord HaeRahavarauksetTehtavineenEpaonnistui [vastaus])
(defrecord MuokkaaRahavaraus [valittu-urakka rahavaraukset])
(defrecord MuokkaaRahavarausOnnistui [vastaus])
(defrecord MuokkaaRahavarausEpaonnistui [vastaus])

(defrecord ValitseUrakanRahavaraus [urakka rahavaraus valittu?])
(defrecord ValitseUrakanRahavarausOnnistui [vastaus])
(defrecord ValitseUrakanRahavarausEpaonnistui [vastaus])

(defrecord ValitseUrakka [urakka])

(defrecord HaeTehtavat [])
(defrecord HaeTehtavatOnnistui [vastaus])
(defrecord HaeTehtavatEpaonnistui [vastaus])
(defrecord LisaaUusiTehtavaRahavaraukselle [rahavaraus-id])
(defrecord TallennaTehtavaRahavaraukselle [rahavaraus-id valittu-tehtava vanha-tehtava-id])
(defrecord TallennaRahavarauksenTehtavaOnnistui [vastaus])
(defrecord TallennaRahavarauksenTehtavaEpaonnistui [vastaus])
(defrecord PoistaTehtavaRahavaraukselta [rahavaraus-id tehtava-id])
(defrecord PoistaTehtavaRahavaraukseltaOnnistui [vastaus])
(defrecord PoistaTehtavaRahavaraukseltaEpaonnistui [vastaus])
(defrecord PoistaRahavarausOnnistui [vastaus])
(defrecord PoistaRahavarausEpaonnistui [vastaus])

(defn- kasittele-rahavaraus-vastaus [{:keys [urakoiden-rahavaraukset] :as vastaus} app]
  (let [kaikki-rahavaraukset (:rahavaraukset vastaus)
        ;; Filtteröi vastauksesta urakat
        urakat (sort-by :urakka-nimi
                 (into #{}
                   (map #(select-keys % [:urakka-id :urakka-nimi]) urakoiden-rahavaraukset)))]
    (assoc app
      :rahavaraukset kaikki-rahavaraukset
      :urakoiden-rahavaraukset urakoiden-rahavaraukset
      :urakat urakat
      :haku-kaynnissa? false
      :tallennus-kesken? false
      :valittu-urakka (or (:valittu-urakka app) (first urakat)))))

(extend-protocol tuck/Event
  HaeRahavaraukset
  (process-event [_ app]
    (tuck-apurit/post! :hae-rahavaraukset
      {}
      {:onnistui ->HaeRahavarauksetOnnistui
       :epaonnistui ->HaeRahavarauksetEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :haku-kaynnissa? true))

  HaeRahavarauksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (kasittele-rahavaraus-vastaus vastaus app))

  HaeRahavarauksetEpaonnistui
  (process-event [{:keys [_vastaus]} app]
    (viesti/nayta-toast! "Rahavarausten haku epäonnistui" :varoitus)
    (assoc app :haku-kaynnissa? false))

  HaeRahavarauksetTehtavineen
  (process-event [_ app]
    (tuck-apurit/post! :hae-rahavaraukset-tehtavineen
      {}
      {:onnistui ->HaeRahavarauksetTehtavineenOnnistui
       :epaonnistui ->HaeRahavarauksetTehtavineenEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeRahavarauksetTehtavineenOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :rahavaraukset-tehtavineen vastaus))

  HaeRahavarauksetTehtavineenEpaonnistui
  (process-event [{:keys [_vastaus]} app]
    (viesti/nayta-toast! "Rahavarausten haku epäonnistui" :varoitus)
    (assoc app :rahavaraukset-tehtavineen nil))

  MuokkaaRahavaraus
  (process-event [{:keys [valittu-urakka rahavaraukset]} app]
    (let [tallennus-id (gensym)]
      ;; Tallenna nappula johtaa aina tänne. Joten muokattiin tai poistettiin, aina ollaan samassa paikassa
      (doseq [rahavaraus rahavaraukset]

        (if (:poistettu rahavaraus)
          ;; Jos poistettiin
          (tuck-apurit/post! :poista-rahavaraus
            {:id (:id rahavaraus)}
            {:onnistui ->PoistaRahavarausOnnistui
             :epaonnistui ->PoistaRahavarausEpaonnistui
             :onnistui-parametrit [tallennus-id]
             :epaonnistui-parametrit [tallennus-id]
             :paasta-virhe-lapi? true})

          ;; Jos muokataan
          (tuck-apurit/post! :paivita-urakan-rahavaraus
            {:urakka (:urakka-id valittu-urakka)
             :id (:id rahavaraus)
             :nimi (:nimi rahavaraus)
             :urakkakohtainen-nimi (:urakkakohtainen-nimi rahavaraus)
             :valittu? (:valittu? rahavaraus)}
            {:onnistui ->MuokkaaRahavarausOnnistui
             :epaonnistui ->MuokkaaRahavarausEpaonnistui
             :onnistui-parametrit [tallennus-id]
             :epaonnistui-parametrit [tallennus-id]
             :paasta-virhe-lapi? true})))
      (assoc app :tallennus-kesken? true)))

  MuokkaaRahavarausOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Rahavarauksen tallennus onnistui!")
    (kasittele-rahavaraus-vastaus vastaus app))

  MuokkaaRahavarausEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (js/log.error "Virhe: " (pr-str vastaus))
    (viesti/nayta-toast! "Rahavarausten muokkaus epäonnistui" :varoitus)
    (assoc app :tallennus-kesken? false))

  PoistaRahavarausOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Rahavarauksen poisto onnistui!")
    (kasittele-rahavaraus-vastaus vastaus app))

  PoistaRahavarausEpaonnistui
  (process-event [{:keys [_vastaus]} app]
    (viesti/nayta-toast! "Rahavarauksen poisto ei onnistu - Sillä on varmaan jo kuluja tai suunnitelmia, eikä sitä voida enää poistaa" :varoitus)
    (assoc app :tehtavat nil))

  ValitseUrakanRahavaraus
  (process-event [{:keys [urakka rahavaraus valittu?]} app]
    (tuck-apurit/post! :paivita-urakan-rahavaraus
      {:urakka (:urakka-id urakka)
       :id (:id rahavaraus)
       :valittu? valittu?}
      {:onnistui ->ValitseUrakanRahavarausOnnistui
       :epaonnistui ->ValitseUrakanRahavarausEpaonnistui
       :paasta-virhe-lapi? true})

    (if valittu?
      (-> app
        (update :urakoiden-rahavaraukset
          conj {:id (:id rahavaraus)
                :nimi (:nimi rahavaraus)
                :urakka-id (:urakka-id urakka)
                :urakka-nimi (:urakka-nimi urakka)})
        (assoc :tallennus-kesken? true))
      (-> app
        (update :urakoiden-rahavaraukset
          #(filter %2 %1)
          #(or
             (not= (:id %) (:id rahavaraus))
             (not= (:urakka-id %) (:urakka-id urakka))))
        (assoc :tallennus-kesken? true))))

  ValitseUrakanRahavarausOnnistui
  (process-event [_ app]
    (assoc app :tallennus-kesken? false))

  ValitseUrakanRahavarausEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Rahavarauksen tallennus epäonnistui" :varoitus)
    (assoc app :tallennus-kesken? false))

  ValitseUrakka
  (process-event [{:keys [urakka]} app]
    (assoc app :valittu-urakka urakka))

  HaeTehtavat
  (process-event [_ app]
    (tuck-apurit/post! :hae-rahavaraukselle-mahdolliset-tehtavat
      {}
      {:onnistui ->HaeTehtavatOnnistui
       :epaonnistui ->HaeTehtavatEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeTehtavatOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :tehtavat vastaus))

  HaeTehtavatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tehtävien haku epäonnistui" :varoitus)
    (assoc app :tehtavat nil))

  LisaaUusiTehtavaRahavaraukselle
  (process-event [{:keys [rahavaraus-id]} app]
    (let [;; Haetaan annetun rahavaraus-id:n perusteella rahavaraus app-statesta
          rahavaraukset-tehtavineen (:rahavaraukset-tehtavineen app)
          rahavaraus (->> rahavaraukset-tehtavineen
                       (filter #(= (:id %) rahavaraus-id))
                       first)
          ;; Lisätään uusi tehtävä rahavaraukselle
          rahavaraus (update rahavaraus :tehtavat
                       conj {:id 0
                             :nimi ""})
          ;; Poistetaan vanha rahavaraus
          rahavaraukset-tehtavineen (filter #(not= (:id %) rahavaraus-id) rahavaraukset-tehtavineen)
          ;;ja lisätään uusi
          rahavaraukset-tehtavineen (conj rahavaraukset-tehtavineen rahavaraus)]
      (assoc app :rahavaraukset-tehtavineen rahavaraukset-tehtavineen)))

  TallennaTehtavaRahavaraukselle
  (process-event [{:keys [rahavaraus-id valittu-tehtava vanha-tehtava-id]} app]
    (tuck-apurit/post! :tallenna-rahavarauksen-tehtava
      {:vanha-tehtava-id vanha-tehtava-id
       :uusi-tehtava valittu-tehtava
       :rahavaraus-id rahavaraus-id}
      {:onnistui ->TallennaRahavarauksenTehtavaOnnistui
       :epaonnistui ->TallennaRahavarauksenTehtavaEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  TallennaRahavarauksenTehtavaOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tehtävä lisättiin" :onnistunut)
    (assoc app :rahavaraukset-tehtavineen vastaus))

  TallennaRahavarauksenTehtavaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tehtävän tallennus epäonnistui" :varoitus)
    app)

  PoistaTehtavaRahavaraukselta
  (process-event [{:keys [rahavaraus-id tehtava-id]} app]
    (tuck-apurit/post! :poista-rahavarauksen-tehtava
      {:tehtava-id tehtava-id
       :rahavaraus-id rahavaraus-id}
      {:onnistui ->PoistaTehtavaRahavaraukseltaOnnistui
       :epaonnistui ->PoistaTehtavaRahavaraukseltaEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  PoistaTehtavaRahavaraukseltaOnnistui
  (process-event [{:keys [vastaus]} app]
    ;; Jos poisto on onnistunut, niin vastauksen mukana uusitut rahavaraukset tehtavineen.
    (viesti/nayta-toast! "Tehtävä poistettiin" :onnistunut)
    (assoc app :rahavaraukset-tehtavineen vastaus))

  PoistaTehtavaRahavaraukseltaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tehtävän poistaminen epäonnistui" :varoitus)
    app))
