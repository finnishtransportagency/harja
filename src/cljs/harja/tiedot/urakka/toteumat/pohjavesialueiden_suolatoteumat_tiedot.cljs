(ns harja.tiedot.urakka.toteumat.pohjavesialueiden-suolatoteumat-tiedot
  "Tämän nimiavaruuden avulla voidaan hakea urakan suola- ja lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka :as urakka]
            [harja.ui.viesti :as viesti]
            [clojure.set :as set]))

(defonce
  ^{:doc "Jotta voidaan käyttää non-tuck tyyppistä aikavälin valintaa, niin säilötään valittu aikaväli materiaalien tarkastelulle"}
  valittu-aikavali (atom (pvm/kuukauden-aikavali (pvm/nyt))))

(defrecord HaeRajoitusalueet [valittu-vuosi])
(defrecord HaeRajoitusalueetOnnistui [vastaus])
(defrecord HaeRajoitusalueetEpaonnistui [vastaus])

(defrecord HaeRajoitusalueenSummatiedot [parametrit])
(defrecord HaeRajoitusalueenSummatiedotOnnistui [vastaus])
(defrecord HaeRajoitusalueenSummatiedotEpaonnistui [vastaus])

(defrecord HaeRajoitusalueenPaivanToteumat [parametrit])
(defrecord HaeRajoitusalueenPaivanToteumatOnnistui [vastaus])
(defrecord HaeRajoitusalueenPaivanToteumatEpaonnistui [vastaus])

(defn- hae-suolarajoitukset [valittu-vuosi]
  (let [urakka-id (-> @tila/yleiset :urakka :id)
        _ (tuck-apurit/post! :hae-suolatoteumat-rajoitusalueittain
            {:hoitokauden-alkuvuosi valittu-vuosi           ;; Määritellään minkä vuoden rajoitusalueille toteumat haetaan
             :alkupvm (first @valittu-aikavali)
             :loppupvm (second @valittu-aikavali)
             :urakka-id urakka-id}
            {:onnistui ->HaeRajoitusalueetOnnistui
             :epaonnistui ->HaeRajoitusalueetEpaonnistui
             :paasta-virhe-lapi? true})]))

(extend-protocol tuck/Event

  HaeRajoitusalueet
  (process-event [{valittu-vuosi :valittu-vuosi} app]
    (do
      (js/console.log "HaeRAjoitusalueet :: ala käyttämään valittu-aikavali:" (pr-str @valittu-aikavali))
      (hae-suolarajoitukset valittu-vuosi)
      (-> app
        (assoc :rajoitusalueet nil)
        (assoc :valittu-rajoitusalue nil)
        (assoc :rajoitusalueet-haku-kaynnissa? true))))

  HaeRajoitusalueetOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [; Lisätään ui taulukkoa varten osoiteväli
          vastaus (map (fn [rivi]
                         (assoc rivi :osoitevali (str
                                                   (str (:aosa rivi) " / " (:aet rivi))
                                                   " – "
                                                   (str (:losa rivi) " / " (:let rivi)))))
                    vastaus)]
      (-> app
        (assoc :rajoitusalueet-haku-kaynnissa? false)
        (assoc :rajoitusalueet vastaus))))

  HaeRajoitusalueetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Rajoitsalueiden haku epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (-> app
        (assoc :rajoitusalueet-haku-kaynnissa? false)
        (assoc :rajoitusalueet nil))))

  HaeRajoitusalueenSummatiedot
  (process-event [{{:keys [rajoitusalue-id hoitokauden-alkuvuosi] :as parametrit} :parametrit} app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          _ (tuck-apurit/post! :hae-rajoitusalueen-summatiedot
              {:alkupvm (first @valittu-aikavali)
               :loppupvm (second @valittu-aikavali)
               :urakka-id urakka-id
               :rajoitusalue-id rajoitusalue-id}
              {:onnistui ->HaeRajoitusalueenSummatiedotOnnistui
               :epaonnistui ->HaeRajoitusalueenSummatiedotEpaonnistui
               :paasta-virhe-lapi? true})

          ;; Aseta avatun rivin vetolaatikot tyhjäksi, jotta haussa se voidaan täyttää oikeilla tiedoilla
          app (update app :rajoitusalueet
                (fn [rajoitusalueet]
                  (mapv
                    (fn [r]
                      (let [r (if (= rajoitusalue-id (:rajoitusalue_id r))
                                (assoc r :suolasummat nil)  ;; Tyhjennä
                                r)]
                        r))
                    rajoitusalueet)))]
      (-> app
        (assoc :valittu-rajoitusalue rajoitusalue-id)       ;; Asetetaan klikattu rajoitusalue-id talteen
        (assoc :summatiedot-haku-kaynnissa? true))))

  HaeRajoitusalueenSummatiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [valittu-rajoitusalue (:valittu-rajoitusalue app)
          app (update app :rajoitusalueet
                (fn [rajoitusalueet]
                  (do
                    ;(js/console.log "vastauksen jälkeen update ::  rajoitusalueet")
                    (mapv
                      (fn [rajoitusalue]
                        (let [;_ (js/console.log "rajoitusalue: " (pr-str (= valittu-rajoitusalue (:rajoitusalue_id rajoitusalue))))
                              rajoitusalue (if (= valittu-rajoitusalue (:rajoitusalue_id rajoitusalue))
                                             (assoc rajoitusalue :suolasummat vastaus)
                                             rajoitusalue)]
                          rajoitusalue))
                      rajoitusalueet))))]
      (assoc app :summatiedot-haku-kaynnissa? false)))

  HaeRajoitusalueenSummatiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Rajoitsalueiden haku epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (assoc app :summatiedot-haku-kaynnissa? false)))

  HaeRajoitusalueenPaivanToteumat
  (process-event [{parametrit :parametrit} app]
    (let [urakka-id (-> @tila/yleiset :urakka :id)
          hakuparametrit (-> parametrit
                           (assoc :urakka-id urakka-id)
                           (dissoc :rivi-id))
          _ (tuck-apurit/post! :hae-rajoitusalueen-paivan-toteumat
              hakuparametrit
              {:onnistui ->HaeRajoitusalueenPaivanToteumatOnnistui
               :epaonnistui ->HaeRajoitusalueenPaivanToteumatEpaonnistui
               :paasta-virhe-lapi? true})]
      (-> app
        (assoc :valittu-rivi-id (:rivi-id parametrit))
        (assoc :paivatoteumat-haku-kaynnissa? true))))

  HaeRajoitusalueenPaivanToteumatOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [valittu-rajoitusalue (:valittu-rajoitusalue app)
          valittu-rivi-id (:valittu-rivi-id app)
          app (update app :rajoitusalueet
                (fn [rajoitusalueet]
                  (mapv
                    (fn [rajoitusalue]
                      (if (= valittu-rajoitusalue (:rajoitusalue_id rajoitusalue))
                        (update rajoitusalue :suolasummat
                          (fn [toteumat]
                            (mapv (fn [rivi]
                                    (if (= valittu-rivi-id (:rivi-id rivi))
                                      (assoc rivi :paivatoteumat vastaus)
                                      rivi))
                              toteumat)))
                        rajoitusalue))
                    rajoitusalueet)))]
      (assoc app :paivatoteumat-haku-kaynnissa? false)))

  HaeRajoitusalueenPaivanToteumatEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Rajoitsalueiden haku epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
      (assoc app :paivatoteumat-haku-kaynnissa? false)))
  )
