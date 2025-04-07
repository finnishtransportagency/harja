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
                                             :ladatut-rivit nil
                                             :haku-kaynnissa? false
                                             :valinnat {:raportti {}
                                                        :aikavali (pvm/kuukauden-aikavali (pvm/nyt))}})

(def nakymassa? (atom false))


(defrecord HaeTiedot [])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord HaeSanktiotOnnistui [vastaus])
(defrecord HaeSanktiotEpaonnistui [vastaus])
(defrecord HaeKorjauksetOnnistui [vastaus])
(defrecord HaeKorjauksetEpaonnistui [vastaus])


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
  (let [ryhma (group-by (fn [rivi]
                          (let [tyyppi (ryhmita-avain rivi)]
                            (if (#{:arvonmuutos :yllapidon_sakko :yllapidon_bonus} tyyppi)
                              tyyppi
                              :muut-kustannukset)))
                data)

        kaikki-tyypit [:arvonmuutos :yllapidon_sakko :yllapidon_bonus :muut-kustannukset]]

    (->> kaikki-tyypit
      (map (fn [tyyppi]
             (let [rivit (ryhma tyyppi)]
               {:id     (gensym)
                :tyyppi tyyppi
                :hinta  (if (seq rivit) (reduce + (map summa-avain rivit))  0)})))
      (vec))))


(defn suodata-ja-laske-korjaukset-yhteen
  "Suodattaa korjaus kustannukset vuoden perusteella 
   Palauttaa vectorin ryhmitettynä:
     :id        Grid tunniste 
     :tyyppi    :korjaus
     :hinta     Summattu hinta"
  [korjaus-kustannukset [alku loppu]]
  (let [suodatettu (filter (fn [{vuosi :kustannusvuosi}]
                             (let [kustannuksen-pvm (pvm/vuoden-eka-pvm vuosi)]
                               (and
                                 (not (pvm/ennen? kustannuksen-pvm alku))
                                 (not (pvm/jalkeen? kustannuksen-pvm loppu)))))
                     korjaus-kustannukset)]
    [{:id     (gensym)
      :tyyppi :korjaus
      :hinta  (reduce + 0 (map :kustannus suodatettu))}]))


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


(defn- hae-korjaus-kustannukset [app]
  (tuck-apurit/post! app :hae-tiemerkinta-kustannuskirjaus
    {:urakka @nav/valittu-urakka}
    {:onnistui ->HaeKorjauksetOnnistui
     :epaonnistui ->HaeKorjauksetEpaonnistui}))


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (hae-yllapito-toteumat app)
    (->
      (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true)
      (assoc-in [:valinnat :aikavali] @u/valittu-aikavali)))

  HaeTiedotOnnistui
  (process-event [{:keys [vastaus]} {:keys [_valinnat] :as app}]
    (->
      (hae-sanktiot-ja-bonukset app)
      (assoc :ladatut-rivit (laske-kustannukset-yhteen vastaus :tyyppi :hinta))))

  HaeTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeSanktiotOnnistui
  (process-event [{:keys [vastaus]} {:keys [_valinnat ladatut-rivit] :as app}]
    (->
      (hae-korjaus-kustannukset app)
      (assoc :ladatut-rivit (laske-kustannukset-yhteen (concat vastaus ladatut-rivit) :laji :summa))))

  HaeSanktiotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeKorjauksetOnnistui
  (process-event [{:keys [vastaus]} {:keys [_valinnat ladatut-rivit] :as app}]
    (-> app
      (assoc :rivit ladatut-rivit)
      (update :rivit into (suodata-ja-laske-korjaukset-yhteen (concat vastaus ladatut-rivit) @u/valittu-aikavali))
      (as-> paivitetty
        (-> paivitetty
          (assoc :haku-kaynnissa? false)
          (assoc-in [:valinnat :raportti] (raporttiparametrit (:rivit paivitetty)))))))

  HaeKorjauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app)))
