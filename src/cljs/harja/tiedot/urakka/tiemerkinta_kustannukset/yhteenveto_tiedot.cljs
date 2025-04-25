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
(defonce ^{:private true} kategoriat [:korjaus :paikkausten-merkinnat :paallysteiden-merkinnat :arvonmuutos :yllapidon_sakko :yllapidon_bonus :muut-kustannukset])
(defonce ^{:private true} nollatut-valinnat {:rivit nil
                                             :muokataan false
                                             :ladatut-rivit nil
                                             :haku-kaynnissa? true
                                             :valinnat {:raportti {}
                                                        :aikavali (pvm/kuukauden-aikavali (pvm/nyt))}})

(def nakymassa? (atom false))


(defrecord HaeTiedot [])
(defrecord HaeMuutOnnistui [vastaus])
(defrecord HaeMuutEpaonnistui [vastaus])
(defrecord HaeSanktiotOnnistui [vastaus])
(defrecord HaeSanktiotEpaonnistui [vastaus])
(defrecord HaeKorjauksetOnnistui [vastaus])
(defrecord HaeKorjauksetEpaonnistui [vastaus])
(defrecord HaePaikkausOnnistui [vastaus])
(defrecord HaePaikkausEpaonnistui [vastaus])
(defrecord HaePaallystysOnnistui [vastaus])
(defrecord HaePaallystysEpaonnistui [vastaus])


(defn- epaonnistui [vastaus app]
  (js/console.warn "Tietojen haku epäonnistui: " (pr-str vastaus))
  (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
  (assoc app :haku-kaynnissa? false))


(defn laske-korjaukset-yhteen
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


(defn laske-paallysteiden-merkinnat
  "Laskee pienmerkinnät, linjamerkinnät ja jyrsinnät yhteen
   Palauttaa vectorin ryhmitettynä:
   :id        Grid tunniste 
   :tyyppi    Kustannuksen tyyppi
   :hinta     Summattu hinta"
  [kohteet tyyppi]
  [{:id     (gensym)
    :tyyppi tyyppi
    :hinta  (reduce + 0 (map #(+
                               (:pienmerkinnat % 0)
                               (:linjamerkinnat % 0)
                               (:jyrsinnat % 0)) kohteet))}])


(defn sanktiot-ja-muut-yhteen [rivit]
  (let [s-avain (if (contains? (first rivit) :laji) :laji :tyyppi)
        m-avain (if (contains? (first rivit) :summa) :summa :hinta)
        ryhmitetty (group-by
                     (fn [rivi]
                       (let [arvo (s-avain rivi)]
                         (if (#{:arvonmuutos :yllapidon_sakko :yllapidon_bonus} arvo)
                           arvo
                           :muut-kustannukset)))
                     rivit)]
    ;; Palauta myös nolla arvot
    (mapv (fn [k]
            {:tyyppi k
             :hinta  (reduce + 0 (map m-avain (get ryhmitetty k)))})
      kategoriat)))


(defn summaa-yhteenveto
  "Laskee eurot yhteen ja palauttaa vectorin ryhmitettynä:
   :id        Grid tunniste 
   :tyyppi    Kustannuksen tyyppi
   :hinta     Summattu hinta"
  [vec1 vec2]
  (let [kaikki (concat vec1 vec2)
        ryhmitetty (group-by :tyyppi kaikki)]
    (mapv (fn [k]
            {:id (gensym)
             :tyyppi k
             :hinta (reduce + 0 (map :hinta (get ryhmitetty k)))})
      kategoriat)))


(defn- raporttiparametrit [rivit]
  (raporttitiedot/urakkaraportin-parametrit @nav/valittu-urakka-id raportti-avain
    {:rivit rivit
     :urakkatyyppi (:arvo @nav/urakkatyyppi)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)
     :sopimus (-> @u/valittu-sopimusnumero first)
     :kaikki? (u/koko-urakkakausi-valittuna?)}))


(defn hae-muut-kustannukset [{:keys [_valinnat] :as app}]
  (tuck-apurit/post! app :hae-yllapito-toteumat
    {:urakka  @nav/valittu-urakka-id
     :sopimus (-> @u/valittu-sopimusnumero first)
     :alkupvm  (-> @u/valittu-aikavali first)
     :loppupvm (-> @u/valittu-aikavali second)}
    {:onnistui ->HaeMuutOnnistui
     :epaonnistui ->HaeMuutEpaonnistui}))


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


(defn- hae-paikkaus-kustannukset [app]
  (tuck-apurit/post! app :hae-tiemerkinta-paikkausten-kustannukset
    {:urakka-id (:id @nav/valittu-urakka)
     :urakka-alkupvm (-> @u/valittu-aikavali first)}
    {:onnistui ->HaePaikkausOnnistui
     :epaonnistui ->HaePaikkausEpaonnistui}))


(defn- hae-paallystys-kustannukset [app]
  (tuck-apurit/post! app :hae-tiemerkinta-paallystyskohteiden-kustannukset
    {:urakka-id (:id @nav/valittu-urakka)
     :urakka-alkupvm (-> @u/valittu-aikavali first)}
    {:onnistui ->HaePaallystysOnnistui
     :epaonnistui ->HaePaallystysEpaonnistui}))


(extend-protocol tuck/Event
  ;; callback # 1
  HaeTiedot
  (process-event [_ app]
    (hae-muut-kustannukset app)
    (->
      (tuck-apurit/nollaa-tuck-tila app nollatut-valinnat)
      (assoc :haku-kaynnissa? true :rivit nil)
      (assoc-in [:valinnat :aikavali] @u/valittu-aikavali)))


  ;; callback # 2 
  HaeMuutOnnistui
  (process-event [{:keys [vastaus]} app]
    (->
      (hae-sanktiot-ja-bonukset app)
      (assoc :ladatut-rivit (sanktiot-ja-muut-yhteen vastaus))))


  ;; callback # 3
  HaeSanktiotOnnistui
  (process-event [{:keys [vastaus]} {:keys [ladatut-rivit] :as app}]
    (let [sanktiot (sanktiot-ja-muut-yhteen vastaus)
          yhteenveto (summaa-yhteenveto sanktiot ladatut-rivit)]
      (->
        (hae-paikkaus-kustannukset app)
        (assoc :ladatut-rivit yhteenveto))))


  ;; callback # 4
  HaePaikkausOnnistui
  (process-event [{:keys [vastaus]} {:keys [ladatut-rivit] :as app}]
    (let [paikkaus-merkinnat (laske-paallysteiden-merkinnat vastaus :paikkausten-merkinnat)
          yhteenveto (summaa-yhteenveto paikkaus-merkinnat ladatut-rivit)]
      (->
        (hae-paallystys-kustannukset app)
        (assoc :ladatut-rivit yhteenveto))))


  ;; callback # 5
  HaePaallystysOnnistui
  (process-event [{:keys [vastaus]} {:keys [ladatut-rivit] :as app}]
    (let [paallystys-merkinnat (laske-paallysteiden-merkinnat vastaus :paallysteiden-merkinnat)
          yhteenveto (summaa-yhteenveto paallystys-merkinnat ladatut-rivit)]
      (->
        (hae-korjaus-kustannukset app)
        (assoc :ladatut-rivit yhteenveto))))


  ;; viimeinen
  HaeKorjauksetOnnistui
  (process-event [{:keys [vastaus]} {:keys [ladatut-rivit] :as app}]
    ;; Kaikki kustannukset on nyt "ladatut-rivit sisällä"
    ;; Laske vielä korjaukset yhteen, ja summaa kaikki 
    (let [korjaukset (laske-korjaukset-yhteen vastaus @u/valittu-aikavali)
          kaikki-kustannukset (summaa-yhteenveto korjaukset ladatut-rivit)]
      ;; Tee riveistä samalla raporttiparametrit 
      (-> app
        (assoc :rivit kaikki-kustannukset)
        (assoc-in [:valinnat :raportti] (raporttiparametrit kaikki-kustannukset))
        (assoc :haku-kaynnissa? false))))

  HaeSanktiotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeMuutEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaeKorjauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaePaikkausEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app))

  HaePaallystysEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (epaonnistui vastaus app)))
