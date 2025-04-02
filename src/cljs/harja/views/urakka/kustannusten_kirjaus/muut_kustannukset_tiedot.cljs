(ns harja.views.urakka.kustannusten-kirjaus.muut-kustannukset-tiedot
  "Tiemerkintöjen muut kustannukset välilehti - tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.raportit :as raporttitiedot])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:rivit nil
                     :muokataan false
                     :haku-kaynnissa? false
                     :valittu-rivi {}
                     :valinnat {:raportti {}
                                :aikavali (pvm/kuukauden-aikavali (pvm/nyt))}}))

(def nakymassa? (atom false))
(defonce ^{:private true} raportti-avain :tiemerkinta-muut-kustannukset)

(defonce mahd-pk-luokat {:- "Ei PK-luokkaa"
                         :1 "1"
                         :2 "2"
                         :3 "3"})

(defonce tyyppi-valinnat {:lisatyo "Lisätyö"
                          :muu "Muu kustannus"
                          :muutostyo "Muutostyö"
                          :arvonmuutos "Arvonmuutos"
                          :indeksi "Indeksitarkistus"
                          :sopimusalueen-muutos "Sopimusalueen muutos"})


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord AvaaKustannusModal [rivi])
(defrecord HaeTyypit [])
(defrecord HaeTyypitOnnistui [vastaus])
(defrecord HaeTyypitEpaonnistui [vastaus])
(defrecord MuokkaaRivia [rivi])
(defrecord SuljeMuokkaus [])
(defrecord AsetaToteumanPvm [aika])
(defrecord TallennaRivi [rivi])
(defrecord TallennusOnnistui [vastaus])
(defrecord TallennusEpaonnistui [vastaus])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))


(defn hae-tiedot
  [{:keys [valinnat] :as app}]
  (tuck-apurit/post! app :hae-yllapito-toteumat
    {:urakka  @nav/valittu-urakka-id
     :sopimus (-> @u/valittu-sopimusnumero first)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(defn- hae-kustannustyypit [app]
  (tuck-apurit/post! app :hae-tiemerkinta-kustannustyypit
    {:urakka-id @nav/valittu-urakka-id}
    {:onnistui ->HaeTyypitOnnistui
     :epaonnistui ->HaeTyypitEpaonnistui}))


(defn voi-tallentaa?
  ""
  [{:keys [kustannus] :as valittu-rivi}]
  (let [] false))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-kustannustyypit app)
    (hae-tiedot app)
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} {:keys [valinnat] :as app}]
    (-> app
      (assoc :rivit vastaus :haku-kaynnissa? false)
      ;; TODO tee tälle jotain 
      (assoc-in [:valinnat :raportti] (raporttitiedot/urakkaraportin-parametrit @nav/valittu-urakka-id raportti-avain
                                        {:alkupvm  (-> valinnat :aikavali first)
                                         :loppupvm (-> valinnat :aikavali second)
                                         :urakkatyyppi (:arvo @nav/urakkatyyppi)}))))

  HaeTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  HaeTyypit
  (process-event [_ app]
    (hae-kustannustyypit app)
    (assoc app :haku-kaynnissa? true))

  HaeTyypitOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app
      :tyypit vastaus
      :haku-kaynnissa? false))

  HaeTyypitEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (println "epa: " vastaus)
    (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  TallennaRivi
  (process-event [{:keys [rivi]} app]
    (let [rivi (lomake/ilman-lomaketietoja rivi)
          {:keys [pvm hinta id selite tyyppi yllapitoluokka]} rivi
          toteuma {:id id
                   :pvm pvm
                   :hinta hinta
                   :tyyppi tyyppi
                   :selite selite
                   :yllapitoluokka yllapitoluokka
                   :poistettu false}

          parametrit {:urakka-id  @nav/valittu-urakka-id
                      :sopimus-id (-> @u/valittu-sopimusnumero first)
                      :toteumat [toteuma]
                      ;; Ei ole toteuman pvm, vaan näillä haetaan vastaus 
                      :alkupvm (-> @u/valittu-aikavali first)
                      :loppupvm (-> @u/valittu-aikavali second)}]

      (tuck-apurit/post! app :tallenna-yllapito-toteumat
        parametrit
        {:onnistui ->TallennusOnnistui
         :epaonnistui ->TallennusEpaonnistui})
      (assoc app :muokataan false)))

  TallennusOnnistui
  (process-event [_ {:keys [valittu-rivi rivit] :as app}]
    (let [valittu-id (:id valittu-rivi)
          valittu-rivi (some #(when (= (:id %) valittu-id) %) rivit)]
      (viesti/nayta-toast! "Toteuma tallennettu onnistuneesti" :onnistui viesti/viestin-nayttoaika-keskipitka)
      (hae-tiedot app)
      (assoc app :valitu-rivi valittu-rivi)))

  TallennusEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (epaonnistui vastaus app))

  MuokkaaRivia
  (process-event [{:keys [rivi]} app]
    (update app :valittu-rivi merge rivi))

  AsetaToteumanPvm
  (process-event [{aika :aika} app]
    (assoc-in app [:valittu-rivi :pvm] aika))

  AvaaKustannusModal
  (process-event [{:keys [rivi]} app]
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi)))

  SuljeMuokkaus
  (process-event [_ app]
    (assoc app :muokataan false)))
