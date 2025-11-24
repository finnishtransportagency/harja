(ns harja.tiedot.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden tiedot."
  (:require
    [tuck.core :as tuck]
    [reagent.core :refer [atom]]
    [clojure.core.async :refer [go]]

    [harja.tiedot.urakka :as u]
    [harja.ui.viesti :as viesti]
    [harja.tiedot.navigaatio :as nav]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.tiedot.urakka.urakka :as tila]))

(defonce valitavoitteet-app-tila
  (atom {:valittu-hoitokausi nil
         :nakymassa? false
         :urakan-hoitokaudet []

         ;; Haun tila
         :ladataan? false
         :virhe nil

         ;; Data
         :valitavoitteet nil ;; Kaikki välitavoitteet (urakan + valtakunnalliset)
         :urakan-valitavoitteet nil ;; Suodatettu: ei valtakunnallinen-id
         :valtakunnalliset-valitavoitteet nil ;; Suodatettu: on valtakunnallinen-id
         :yllapitokohteet nil

         ;; Tallennustila
         :tallennetaan? false
         :tallennus-virhe nil}))

(defrecord NakymaAvattu [])
(defrecord NakymaSuljettu [])
(defrecord HoitokausiVaihdettu [hoitokausi])

(defrecord HaeValitavoitteet [])
(defrecord HaeValitavoitteetOnnistui [vastaus])
(defrecord HaeValitavoitteetEpaonnistui [vastaus])

(defrecord KopioiValitavoitteet [])
(defrecord KopioiValitavoitteetOnnistui [vastaus])
(defrecord KopioiValitavoitteetEpaonnistui [vastaus])

(defrecord HaeYllapitokohteet [])
(defrecord HaeYllapitokohteetOnnistui [vastaus])
(defrecord HaeYllapitokohteetEpaonnistui [vastaus])

(defrecord TallennaValitavoitteet [tiedot])
(defrecord TallennaValitavoitteetOnnistui [vastaus])
(defrecord TallennaValitavoitteetEpaonnistui [vastaus])

(extend-protocol tuck/Event
  NakymaAvattu
  (process-event [_ app]
    ;; Alusta hoitokausi globaalista jos ei ole asetettu
    (let [globaali-hk @u/valittu-hoitokausi
          app-hk (:valittu-hoitokausi app)
          hoitokausi (or app-hk globaali-hk)
          hoitokaudet @u/valitun-urakan-hoitokaudet
          yllapitokohdeurakka? @u/yllapitokohdeurakka?
          hae-valitavoitteet! (tuck/send-async! ->HaeValitavoitteet)
          hae-yllapitokohteet! (tuck/send-async! ->HaeYllapitokohteet)]

      ;; Hae välitavoitteet
      (go (hae-valitavoitteet!))
      ;; Hae ylläpitokohteet jos ylläpitokohdeurakka
      (when yllapitokohdeurakka?
        (go (hae-yllapitokohteet!)))

      (-> app
        (assoc :nakymassa? true)
        (assoc :ladataan? true)
        (assoc :valittu-hoitokausi hoitokausi)
        (assoc :urakan-hoitokaudet hoitokaudet))))

  NakymaSuljettu
  (process-event [_ app]
    (-> app
      (assoc :nakymassa? false)
      (assoc :ladataan? false)
      (assoc :valitavoitteet nil)
      (assoc :urakan-valitavoitteet nil)
      (assoc :valtakunnalliset-valitavoitteet nil)
      (assoc :yllapitokohteet nil)
      (assoc :virhe nil)))

  HoitokausiVaihdettu
  (process-event [{:keys [hoitokausi]} app]
    ;; Päivitä globaali hoitokausi
    (u/valitse-hoitokausi! hoitokausi)

    ;; Hae uudet välitavoitteet valitulle hoitokaudelle 
    (assoc app :valittu-hoitokausi hoitokausi))

  HaeValitavoitteet
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakan-valitavoitteet
      (:id @nav/valittu-urakka)
      {:onnistui ->HaeValitavoitteetOnnistui
       :epaonnistui ->HaeValitavoitteetEpaonnistui})
    (assoc app :ladataan? true :virhe nil))

  HaeValitavoitteetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [urakan (filterv (comp not :valtakunnallinen-id) vastaus)
          valtakunnalliset (filterv :valtakunnallinen-id vastaus)]
      (-> app
        (assoc :ladataan? false)
        (assoc :valitavoitteet vastaus)
        (assoc :urakan-valitavoitteet urakan)
        (assoc :valtakunnalliset-valitavoitteet valtakunnalliset))))

  HaeValitavoitteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Välitavoitteiden haku epäonnistui" :varoitus)
    (-> app
      (assoc :ladataan? false)
      (assoc :virhe vastaus)
      (assoc :valitavoitteet nil)
      (assoc :urakan-valitavoitteet nil)
      (assoc :valtakunnalliset-valitavoitteet nil)))

  KopioiValitavoitteet
  (process-event [_ app]
    (tuck-apurit/post! app :kopioi-urakan-valitavoitteet-tuleville-hk
      {:urakka-id (-> @tila/yleiset :urakka :id)
       :hoitokaudet @u/valitun-urakan-hoitokaudet
       :valittu-hoitokausi (:valittu-hoitokausi app)}
      {:onnistui ->KopioiValitavoitteetOnnistui
       :epaonnistui ->KopioiValitavoitteetEpaonnistui})
    (assoc app :ladataan? true))

  KopioiValitavoitteetOnnistui
  (process-event [{_vastaus :vastaus} app]
    (viesti/nayta-toast! "Välitavoitteiden kopiointi onnistui" :onnistunut)
    ((tuck/current-send-function) (->NakymaAvattu)))

  KopioiValitavoitteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Välitavoitteiden kopiointi epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
    (-> app
      (assoc :virhe vastaus)
      (assoc :ladataan? false)))

  HaeYllapitokohteet
  (process-event [_ app]
    (let [urakka-id (:id @nav/valittu-urakka)
          [sopimus-id _] @u/valittu-sopimusnumero]
      (when (and urakka-id sopimus-id)
        (tuck-apurit/post! :urakan-yllapitokohteet-lomakkeelle
          {:urakka-id urakka-id
           :sopimus-id sopimus-id}
          {:onnistui ->HaeYllapitokohteetOnnistui
           :epaonnistui ->HaeYllapitokohteetEpaonnistui})))
    app)

  HaeYllapitokohteetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :ladataan? false
      :yllapitokohteet vastaus))

  HaeYllapitokohteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Ylläpitokohteiden haku epäonnistui" :varoitus)
    (assoc app
      :ladataan? false
      :yllapitokohteet nil))

  TallennaValitavoitteet
  (process-event [{tiedot :tiedot} app]
    (let [urakka-id (:id @nav/valittu-urakka)
          payload {:urakka-id urakka-id
                   :valitavoitteet tiedot}]
      (tuck-apurit/post! :tallenna-urakan-valitavoitteet
        payload
        {:onnistui ->TallennaValitavoitteetOnnistui
         :epaonnistui ->TallennaValitavoitteetEpaonnistui}))
    (assoc app :tallennetaan? true :tallennus-virhe nil))

  TallennaValitavoitteetOnnistui
  (process-event [_ app]
    ((tuck/current-send-function) (->HaeValitavoitteet))
    (viesti/nayta-toast! "Välitavoitteiden tallennus onnistui!" :onnistui)
    (assoc app :tallennetaan? false))

  TallennaValitavoitteetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Välitavoitteiden tallennuksessa tapahtui virhe" :varoitus)
    (-> app
      (assoc :tallennetaan? false)
      (assoc :tallennus-virhe vastaus))))


