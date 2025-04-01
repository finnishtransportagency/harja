(ns harja.views.urakka.kustannusten-kirjaus.muut-kustannukset-tiedot
  "Tiemerkintöjen muut kustannukset välilehti - tiedot"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr]
            [harja.tiedot.raportit :as raporttitiedot])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce tila (atom {:rivit nil
                     :muokataan false
                     :valittu-rivi nil
                     :haku-kaynnissa? false
                     :valinnat {:raportti {}
                                :aikavali (pvm/kuukauden-aikavali (pvm/nyt))
                                :pk-luokat {:tyhja "Ei PK-luokkaa"
                                            :1 "1"
                                            :2 "2"
                                            :3 "3"}}}))

(def nakymassa? (atom false))
(defonce ^{:private true} raportti-avain :tiemerkinta-muut-kustannukset)


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord AvaaKustannusModal [rivi])
(defrecord HaeTyypit [])
(defrecord HaeTyypitOnnistui [vastaus])
(defrecord HaeTyypitEpaonnistui [vastaus])
(defrecord MuokkaaRivia [rivi])
(defrecord SuljeMuokkaus [])


(defn hae-muut-kustannukset
  [{:keys [valinnat] :as app}]
  (tuck-apurit/post! app :hae-tiemerkinta-muut-kustannukset
    {:aikavali (:aikavali valinnat)
     :urakka-id @nav/valittu-urakka-id}
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
    (hae-muut-kustannukset app)
    (assoc app :haku-kaynnissa? true))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} {:keys [valinnat] :as app}]
    (-> app
      (assoc :rivit vastaus :haku-kaynnissa? false)
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

  MuokkaaRivia
  (process-event [{:keys [rivi]} app]
    (update app :valittu-rivi merge rivi))

  AvaaKustannusModal
  (process-event [{:keys [rivi]} app]
    (-> app
      (assoc :muokataan true)
      (assoc :valittu-rivi rivi)))

  SuljeMuokkaus
  (process-event [_ app]
    (assoc app :muokataan false)))
