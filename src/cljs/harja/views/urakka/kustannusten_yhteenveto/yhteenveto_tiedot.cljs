(ns harja.views.urakka.kustannusten-yhteenveto.yhteenveto-tiedot
  "Tiemerkintöjen kustannusten yhteenveto - tiedot"
  (:require  [harja.pvm :as pvm]
             [tuck.core :as tuck]
             [harja.tiedot.urakka :as u]
             [harja.ui.viesti :as viesti]
             [harja.tiedot.navigaatio :as nav]
             [harja.tyokalut.tuck :as tuck-apurit]
             [reagent.core :refer [atom] :as reagent]
             [harja.tiedot.raportit :as raporttitiedot]))

(defonce ^{:private true} raportti-avain :tiemerkinta-kustannukset-yhteenveto)
(defonce ^{:private true} nollatut-valinnat {:rivit nil
                                             :muokataan false
                                             :haku-kaynnissa? false
                                             :valinnat {:raportti {}
                                                        :aikavali (pvm/kuukauden-aikavali (pvm/nyt))}})

(def nakymassa? (atom false))


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord HaeSanktiotOnnistui [vastaus])
(defrecord HaeSanktiotEpaonnistui [vastaus])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))


(defn laske-kustannukset-yhteen
  "Palauttaa vektorin mapeista ryhmitettynä:
   :id        Grid tunniste 
   :tyyppi    :arvonmuutos`, :yllapidon_sakko, :yllapidon_bonus :muut-kustannukset (kaikki muut kustannukset)
   :hinta     Summattu hinta"
  [data ryhmita-avain summa-avain]
  (->> data
    (group-by (fn [data]
                (let [tyyppi (ryhmita-avain data)]
                  ;; Näytetään nämä erikseen omana rivinään
                  (if (#{:arvonmuutos :yllapidon_sakko :yllapidon_bonus} tyyppi)
                    tyyppi
                    ;; Kaikki muut niputetaan yhteen, nimellä "Muut kustannukset"
                    :muut-kustannukset))))
    (map (fn [[tyyppi arvo]]
           {:id    (gensym)
            :tyyppi tyyppi
            :hinta  (reduce + (map summa-avain arvo))}))
    vec))


(defn- raporttiparametrit [rivit]
  (raporttitiedot/urakkaraportin-parametrit @nav/valittu-urakka-id raportti-avain
    {:rivit rivit
     :urakkatyyppi (:arvo @nav/urakkatyyppi)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)
     :sopimus (-> @u/valittu-sopimusnumero first)}))


(defn hae-yllapito-toteumat [{:keys [_valinnat] :as app}]
  (tuck-apurit/post! app :hae-yllapito-toteumat
    {:urakka  @nav/valittu-urakka-id
     :sopimus (-> @u/valittu-sopimusnumero first)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)}
    {:onnistui ->HaeTiedotOnnistui
     :epaonnistui ->HaeTiedotEpaonnistui}))


(defn hae-sanktiot-ja-bonukset [app]
  (tuck-apurit/post! app :hae-urakan-sanktiot-ja-bonukset
    {:hae-sanktiot? true
     :hae-bonukset? true
     :urakka-id @nav/valittu-urakka-id
     :alku      (-> @u/valittu-aikavali first)
     :loppu     (-> @u/valittu-aikavali second)}
    {:onnistui ->HaeSanktiotOnnistui
     :epaonnistui ->HaeSanktiotEpaonnistui}))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-yllapito-toteumat app)
    (hae-sanktiot-ja-bonukset app)
    (->
      (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true)
      (assoc-in [:valinnat :aikavali] @u/valittu-aikavali)))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} {:keys [_valinnat] :as app}]
    (assoc app
      :rivit (laske-kustannukset-yhteen vastaus :tyyppi :hinta)))

  HaeTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeSanktiotOnnistui
  (process-event [{:keys [vastaus]} {:keys [_valinnat] :as app}]
    (let [rivit (-> app 
                  (update :rivit into (laske-kustannukset-yhteen vastaus :laji :summa)) 
                  :rivit)]
     (-> app
       (assoc :haku-kaynnissa? false :rivit rivit)
       (assoc-in [:valinnat :raportti] (raporttiparametrit rivit)))))

  HaeSanktiotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app)))
