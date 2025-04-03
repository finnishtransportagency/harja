(ns harja.views.urakka.kustannusten-yhteenveto.yhteenveto-tiedot
  "Tiemerkintöjen kustannusten yhteenveto - tiedot"
  (:require  [harja.pvm :as pvm]
             [tuck.core :as tuck]
             [harja.tiedot.urakka :as u]
             [harja.ui.viesti :as viesti]
             [harja.ui.lomake :as lomake]
             [harja.tiedot.navigaatio :as nav]
             [harja.tyokalut.tuck :as tuck-apurit]
             [reagent.core :refer [atom] :as reagent]
             [harja.tiedot.raportit :as raporttitiedot]
             [harja.views.urakka.kustannusten-kirjaus.yhteiset :as yhteiset]))


(defonce ^{:private true} nollatut-valinnat {:rivit nil
                                             :muokataan false
                                             :haku-kaynnissa? false
                                             :valinnat {:raportti {}
                                                        :aikavali (pvm/kuukauden-aikavali (pvm/nyt))}})

(def nakymassa? (atom false))
(defonce tila (atom nollatut-valinnat))
(defonce ^{:private true} raportti-avain :tiemerkinta-kustannukset-yhteenveto)


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))


(defn- raporttiparametrit [tyypit]
  (raporttitiedot/urakkaraportin-parametrit @nav/valittu-urakka-id raportti-avain
    {:urakkatyyppi (:arvo @nav/urakkatyyppi)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)
     :sopimus (-> @u/valittu-sopimusnumero first)}))


(defn hae-tiedot
  [{:keys [_valinnat] :as app}]
  (tuck-apurit/post! app :hae-yllapito-toteumat
    {:urakka  @nav/valittu-urakka-id
     :sopimus (-> @u/valittu-sopimusnumero first)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (->
      (yhteiset/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true)
      (assoc-in [:valinnat :aikavali] @u/valittu-aikavali)))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} {:keys [_valinnat] :as app}]
    (assoc app :rivit vastaus :haku-kaynnissa? false))

  HaeTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app)))
