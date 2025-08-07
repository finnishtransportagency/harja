(ns harja.tiedot.hallinta.urakkatiedot.toimenkuvat-tiedot
  (:require [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def tila (atom {:valittu-urakka nil
                 :toimenkuvat nil
                 :haku-kaynnissa? false
                 :tallennus-kesken? false}))

(defrecord HaeToimenkuvat [])
(defrecord HaeToimenkuvatOnnistui [vastaus])
(defrecord HaeToimenkuvatEpaonnistui [vastaus])

(defrecord MuokkaaToimenkuva [valittu-urakka toimenkuvat])
(defrecord MuokkaaToimenkuvaOnnistui [vastaus])
(defrecord MuokkaaToimenkuvaEpaonnistui [vastaus])
(defrecord PoistaToimenkuvaOnnistui [vastaus])
(defrecord PoistaToimenkuvaEpaonnistui [vastaus])

(defrecord ValitseUrakanToimenkuva [urakka toimenkuva valittu?])
(defrecord ValitseUrakanToimenkuvaOnnistui [vastaus])
(defrecord ValitseUrakanToimenkuvaEpaonnistui [vastaus])

(defrecord ValitseUrakka [urakka])


(defn- kasittele-toimenkuva-vastaus [{:keys [urakoiden-toimenkuvat] :as vastaus} app]
  (let [kaikki-toimenkuvat (:toimenkuvat vastaus)
        ;; Filtteröi vastauksesta urakat
        urakat (sort-by :urakka-nimi
                 (into #{}
                   (map #(select-keys % [:urakka-id :urakka-nimi]) urakoiden-toimenkuvat)))]
    (assoc app
      :toimenkuvat kaikki-toimenkuvat
      :urakoiden-toimenkuvat urakoiden-toimenkuvat
      :urakat urakat
      :haku-kaynnissa? false
      :tallennus-kesken? false
      :valittu-urakka (or (:valittu-urakka app) (first urakat)))))

(extend-protocol tuck/Event
  HaeToimenkuvat
  (process-event [_ app]
    (tuck-apurit/post! :hae-toimenkuvat
      {}
      {:onnistui ->HaeToimenkuvatOnnistui
       :epaonnistui ->HaeToimenkuvatEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :haku-kaynnissa? true))

  HaeToimenkuvatOnnistui
  (process-event [{:keys [vastaus]} app]
    (kasittele-toimenkuva-vastaus vastaus app))

  HaeToimenkuvatEpaonnistui
  (process-event [{:keys [_vastaus]} app]
    (viesti/nayta-toast! "Toimenkuvien haku epäonnistui" :varoitus)
    (assoc app :haku-kaynnissa? false))

  MuokkaaToimenkuva
  (process-event [{:keys [valittu-urakka toimenkuvat]} app]
    ;; Tallenna nappula johtaa aina tänne. Joten muokattiin tai poistettiin, aina ollaan samassa paikassa
    (doseq [toimenkuva toimenkuvat]

      (if (:poistettu toimenkuva)
        ;; Jos poistettiin
        (tuck-apurit/post! :poista-toimenkuva
          {:id (:id toimenkuva)}
          {:onnistui ->PoistaToimenkuvaOnnistui
           :epaonnistui ->PoistaToimenkuvaEpaonnistui
           :paasta-virhe-lapi? true})

        ;; Jos muokataan
        (tuck-apurit/post! :paivita-urakan-toimenkuva
          {:urakka (:urakka-id valittu-urakka)
           :id (:id toimenkuva)
           :nimi (:nimi toimenkuva)
           :urakkakohtainen-nimi (:urakkakohtainen-nimi toimenkuva)
           :valittu? (:valittu? toimenkuva)}
          {:onnistui ->MuokkaaToimenkuvaOnnistui
           :epaonnistui ->MuokkaaToimenkuvaEpaonnistui
           :paasta-virhe-lapi? true})))

    ;; Jos toimenkuvat on nil, käyttäjältä varmistus on käynnissä
    (if (some? toimenkuvat)
      (assoc app :tallennus-kesken? true)
      (tuck-apurit/post! :hae-toimenkuvat
        {}
        {:onnistui ->HaeToimenkuvatOnnistui
         :epaonnistui ->HaeToimenkuvatEpaonnistui
         :paasta-virhe-lapi? true})))

  MuokkaaToimenkuvaOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Toimenkuvan tallennus onnistui!")
    (kasittele-toimenkuva-vastaus vastaus app))

  MuokkaaToimenkuvaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (js/console.error "Virhe: " (pr-str vastaus))
    (viesti/nayta-toast! "Toimenkuvan muokkaus epäonnistui" :varoitus)
    (assoc app :tallennus-kesken? false))

  PoistaToimenkuvaOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Toimenkuvan poisto onnistui!")
    (kasittele-toimenkuva-vastaus vastaus app))

  PoistaToimenkuvaEpaonnistui
  (process-event [{:keys [_vastaus]} app]
    (viesti/nayta-toast! "Toimenkuvan poisto ei onnistu. Se on varmaankin käytössä tarjouksissa tai kustannusten suunnittelussa." :varoitus)
    app)

  ValitseUrakanToimenkuva
  (process-event [{:keys [urakka toimenkuva valittu?]} app]
    (tuck-apurit/post! :paivita-urakan-toimenkuva
      {:urakka (:urakka-id urakka)
       :id (:id toimenkuva)
       :nimi (:nimi toimenkuva)
       :valittu? valittu?}
      {:onnistui ->ValitseUrakanToimenkuvaOnnistui
       :epaonnistui ->ValitseUrakanToimenkuvaEpaonnistui
       :paasta-virhe-lapi? true})

    (assoc app :tallennus-kesken? true))

  ValitseUrakanToimenkuvaOnnistui
  (process-event [{:keys [vastaus]} app]
    (kasittele-toimenkuva-vastaus vastaus app))

  ValitseUrakanToimenkuvaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Toimenkuvan tallennus epäonnistui" :varoitus)
    (kasittele-toimenkuva-vastaus vastaus app))

  ValitseUrakka
  (process-event [{:keys [urakka]} app]
    (assoc app :valittu-urakka urakka)))
