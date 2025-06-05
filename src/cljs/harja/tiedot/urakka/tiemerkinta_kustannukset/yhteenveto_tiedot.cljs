(ns harja.tiedot.urakka.tiemerkinta-kustannukset.yhteenveto-tiedot
  "Tiemerkintöjen kustannusten yhteenveto - tiedot"
  (:require  [tuck.core :as tuck]
             [reagent.core :refer [atom] :as reagent]

             [harja.pvm :as pvm]
             [harja.tiedot.urakka :as u]
             [harja.ui.viesti :as viesti]
             [harja.tiedot.navigaatio :as nav]
             [harja.tyokalut.tuck :as tuck-apurit]
             [harja.tiedot.raportit :as raporttitiedot]))

(defonce ^{:private true} raportti-avain :tiemerkinta-kustannukset-yhteenveto)
(defonce ^{:private true} nollatut-valinnat {:rivit nil
                                             :muokataan false
                                             :ladatut-rivit nil
                                             :haku-kaynnissa? true
                                             :valinnat {:raportti {}
                                                        :aikavali (pvm/kuukauden-aikavali (pvm/nyt))}})

(def nakymassa? (atom false))


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))


(defn- raporttiparametrit [rivit]
  (raporttitiedot/urakkaraportin-parametrit @nav/valittu-urakka-id raportti-avain
    {:rivit rivit
     :urakkatyyppi (:arvo @nav/urakkatyyppi)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)
     :sopimus (-> @u/valittu-sopimusnumero first)
     :kaikki? (u/koko-urakkakausi-valittuna?)}))


(defn- hae-tiedot [app]
  (tuck-apurit/post! app :hae-tiemerkinta-yhteenveto
    {:urakan-tiedot @nav/valittu-urakka
     :valittu-aikavali @u/valittu-aikavali
     :kaikki? (u/koko-urakkakausi-valittuna?)
     :sopimus (-> @u/valittu-sopimusnumero first)}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-tiedot app)
    (->
      (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true :rivit nil)
      (assoc-in [:valinnat :aikavali] @u/valittu-aikavali)))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false :rivit vastaus)
      (assoc-in [:valinnat :raportti] (raporttiparametrit vastaus))))

  HaeTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app)))
