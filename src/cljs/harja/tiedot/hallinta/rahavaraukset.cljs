(ns harja.tiedot.hallinta.rahavaraukset
  (:require [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def tila (atom {:valittu-urakka nil
                 :rahavaraukset nil
                 :tehtavat nil
                 :tallennukset {}}))

(defrecord HaeRahavaraukset [])
(defrecord HaeRahavarauksetOnnistui [vastaus])
(defrecord HaeRahavarauksetEpaonnistui [vastaus])
(defrecord HaeRahavarauksetTehtavineen [])
(defrecord HaeRahavarauksetTehtavineenOnnistui [vastaus])
(defrecord HaeRahavarauksetTehtavineenEpaonnistui [vastaus])
(defrecord HaeUrakoidenRahavaraukset [])
(defrecord HaeUrakoidenRahavarauksetOnnistui [vastaus])
(defrecord HaeUrakoidenRahavarauksetEpaonnistui [vastaus])
(defrecord ValitseUrakanRahavaraus [urakka rahavaraus valittu?])
(defrecord ValitseUrakanRahavarausOnnistui [vastaus tallennus-id])
(defrecord ValitseUrakanRahavarausEpaonnistui [vastaus tallennus-id])

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
    (js/console.log "HaeRahavarauksetOnnistui :: vastaus: " (pr-str vastaus))
    (assoc app :rahavaraukset vastaus))

  HaeRahavarauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Rahavarauksten haku epäonnistui" :varoitus)
    app)

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
    (js/console.log "HaeRahavarauksetTehtavineenOnnistui :: vastaus: " (pr-str vastaus))
    (assoc app :rahavaraukset-tehtavineen vastaus))

  HaeRahavarauksetTehtavineenEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Rahavarausten haku epäonnistui" :varoitus)
    (assoc app :rahavaraukset-tehtavineen nil))

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
