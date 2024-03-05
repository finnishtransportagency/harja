(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-reikapaikkaukset
  "Reikäpaikkaukset- tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.tierekisteri :as tr]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:rivit nil
                     :valittu-rivi nil
                     :muokataan false
                     :nayta-virhe-modal false
                     :tyomenetelmat nil
                     :excel-virheet nil
                     :valinnat {:aikavali (pvm/kuukauden-aikavali (pvm/nyt))
                                :tr-osoite nil}}))

(def nakymassa? (atom false))
(def paivita-kartta? (atom false))
(def aikavali-atom (atom (pvm/kuukauden-aikavali (pvm/nyt))))

;; Kartta jutskat
(defonce valittu-reikapaikkaus (atom nil)) ;; TODO en nyt tiedä miten tämän pitäisi toimia 
(def karttataso-reikapaikkaukset (atom false))

;; Kartalle hakufunktio
(defn hae-urakan-reikapaikkaukset [urakka-id]
  (k/post! :hae-reikapaikkaukset {:urakka-id urakka-id}))

;; Tulokset reaction
(defonce haetut-reikapaikkaukset
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?
               paivita-kartta? @paivita-kartta?]
    {:nil-kun-haku-kaynnissa? true}
    (when (or nakymassa? paivita-kartta?)
      (hae-urakan-reikapaikkaukset urakka-id)))) 

;; Karttataso
(defonce reikapaikkaukset-kartalla
  (reaction
    (let [valittu-id 36] ;; TODO 
      (when @karttataso-reikapaikkaukset
        (kartalla-esitettavaan-muotoon
          @haetut-reikapaikkaukset
          #(= valittu-id (:id %))
          (comp
            (keep #(and (:sijainti %) %))
            (map #(assoc % :tyyppi-kartalla :reikapaikkaus))))))))

;; Tuck 
(defrecord PaivitaAikavali [uudet])
(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord HaeTyomenetelmat [])
(defrecord HaeTyomenetelmatOnnistui [vastaus])
(defrecord HaeTyomenetelmatEpaonnistui [vastaus])
(defrecord AvaaMuokkausModal [rivi])
(defrecord MuokkaaRivia [rivi])
(defrecord AsetaToteumanPvm [aika])
(defrecord SuljeMuokkaus [])
(defrecord SuljeVirheModal [])
(defrecord TiedostoLadattu [vastaus])



;; Funktiot
(defn hae-reikapaikkaukset [app]
  (tuck-apurit/post! app :hae-reikapaikkaukset
    {:urakka-id (:id @nav/valittu-urakka)}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(defn voi-tallentaa? [{:keys [paikkaus_maara kustannus alkuaika tyomenetelma] :as valittu-reikapaikkaus}
                      {:keys [tyomenetelmat]}]
  ;; Validoi toteuman muokkauslomakkeen
  (let [tyomenetelma-validi? (boolean (some #(= tyomenetelma (:id %)) tyomenetelmat)) ;; Onko valittua työmenetelmä id:tä olemassa tietokannassa? (HaeTyomenetelmat)
        tr-validi? (boolean (tr/validi-osoite? (select-keys valittu-reikapaikkaus [:tie :aosa :aet :losa :let]))) ;; Onko syötetty tr-osoite validi? 
        pvm-validi? (pvm/pvm? alkuaika) ;; Päivämäärä 
        kustannus-validi? (some? kustannus) ;; Sallitaan myös 0, muttei nil
        paikkaus_maara-validi? (some? paikkaus_maara)]
    (and
      tr-validi?
      pvm-validi?
      kustannus-validi?
      tyomenetelma-validi?
      paikkaus_maara-validi?)))


(extend-protocol tuck/Event

  HaeTiedot
  (process-event [_ app]
    (hae-reikapaikkaukset app)
    app)

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (reset! paivita-kartta? false)
    (assoc app :rivit vastaus))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (reset! paivita-kartta? false)
    (js/console.warn "HaeTiedotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeTiedotEpaonnistui Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  HaeTyomenetelmat
  (process-event [_ app]
    (tuck-apurit/post! app :hae-tyomenetelmat
      {}
      {:onnistui ->HaeTyomenetelmatOnnistui
       :epaonnistui ->HaeTyomenetelmatEpaonnistui})
    app)

  HaeTyomenetelmatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :tyomenetelmat vastaus))

  HaeTyomenetelmatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeTyomenetelmatEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeTyomenetelmatEpaonnistui Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  AsetaToteumanPvm
  (process-event [{aika :aika} app]
    (assoc-in app [:valittu-rivi :alkuaika] aika))

  AvaaMuokkausModal
  (process-event [{rivi :rivi} app]
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi)))

  MuokkaaRivia
  (process-event [{rivi :rivi} app]
    (update app :valittu-rivi merge rivi))

  SuljeMuokkaus
  (process-event [_ app]
    (assoc app :muokataan false))

  SuljeVirheModal
  (process-event [_ app]
    (assoc app :nayta-virhe-modal false))

  TiedostoLadattu
  (process-event [{vastaus :vastaus} app]
    (let [status (:status vastaus)
          response (:response vastaus)
          virheet (cond
                    (= status 500) ["Sisäinen käsittelyvirhe"]
                    :else response)]
      (cond
        ;; Virheitä Excel tuonnissa
        (and
          (not (nil? status))
          (not= 200 status))
        (do
          (println "Status: " status)
          (viesti/nayta-toast! "Ladatun tiedoston käsittelyssä virhe" :varoitus viesti/viestin-nayttoaika-lyhyt)
          ;; Lisää virheet app stateen jotka näytetään modalissa
          (-> app
            (assoc :nayta-virhe-modal true)
            (assoc :excel-virheet virheet)))
        ;; Ei virheitä
        :else
        (do
          ;; Päivitä uudet reitit kartalle 
          (reset! paivita-kartta? true)
          ;; Hae päivtetty lista näkymään
          (hae-reikapaikkaukset app)
          (-> app
            (assoc :excel-virheet nil)
            (assoc :nayta-virhe-modal false))))))

  PaivitaAikavali
  (process-event [{uudet :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app) uudet)]
      (assoc app :valinnat uudet-valinnat))))
