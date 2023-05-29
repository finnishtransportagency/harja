(ns harja.tiedot.tyomaapaivakirja
  "Työmaapäiväkirja kutsut"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.raportit :as raportit]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))

(defonce tila (atom {:tiedot []
                     :nayta-rivit []
                     :valittu-rivi nil
                     :viimeksi-valittu nil
                     :valinnat {:aikavali (pvm/kuukauden-aikavali (pvm/nyt))
                                :hakumuoto :kaikki}}))

(def suodattimet {:kaikki nil
                  :myohastyneet 1
                  :puuttuvat 2})

;(def aikavali-atom (atom (:aikavali (:valinnat @tila))))
(def aikavali-atom (atom (pvm/kuukauden-aikavali (pvm/nyt))))
(defonce raportti-avain :tyomaapaivakirja-nakyma)

(defonce raportin-parametrit
  (reaction (let [ur @nav/valittu-urakka]
              (raportit/urakkaraportin-parametrit
                (:id ur)
                raportti-avain
                {:urakkatyyppi (:tyyppi ur)
                 :valittu-rivi (:valittu-rivi @tila)}))))

(defonce raportin-tiedot
  (reaction<! [p @raportin-parametrit]
    {:nil-kun-haku-kaynnissa? true}
    (when (and p @nakymassa?)
      (raportit/suorita-raportti p))))

(defrecord HaeTiedot [urakka-id])
(defrecord ValitseRivi [rivi])
(defrecord PoistaRiviValinta [])
(defrecord PaivitaAikavali [uudet])
(defrecord PaivitaHakumuoto [uudet])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])

(defn suodata-rivit-aikavalilla [valinnat]
  ;; Annetaan parametrina tilan :valinnat josta luetaan hakumuoto & valittu aikaväli
  (let [rivit (filter (fn [rivi]
                        (let [tyopaiva (:alkupvm rivi)
                              aikavali (:aikavali valinnat)
                              valittu-hakumuoto (get suodattimet (-> @tila :valinnat :hakumuoto))
                              rivin-toimitustila (:tila rivi)]
                          (and
                            ;; Onko "TYÖPÄIVÄ"- sarake valitun aikavälin välissä
                            (pvm/valissa? tyopaiva (first aikavali) (second aikavali))
                            (or
                              ;; Vastaako valittu hakumuoto rivin toimituksen tilaa 
                              (= valittu-hakumuoto nil)
                              (= valittu-hakumuoto rivin-toimitustila))))) (:tiedot @tila))]
    rivit))


(defn- hae-paivakirjat [app]
  (let [_ (js/console.log "hae-paivakirjat :: app" (pr-str (dissoc app :tiedot :nayta-rivit)))
        aikavali (get-in app [:valinnat :aikavali])
        hakumuoto (get-in app [:valinnat :hakumuoto])]
    (tuck-apurit/post! app :tyomaapaivakirja-hae
      {:urakka-id (:id @nav/valittu-urakka)
       :alkuaika (first aikavali)
       :loppuaika (second aikavali)
       :hakumuoto hakumuoto}
      {:onnistui ->HaeTiedotOnnistui
       :epaonnistui ->HaeTiedotEpaonnistui})))
(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (do
      (js/console.log "HaeTiedot :: valinnat" (pr-str (:valinnat app)))
      (js/console.log "HaeTiedot :: app" (pr-str (dissoc app :tiedot :nayta-rivit)))
      (hae-paivakirjat app)))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      ;(js/console.log "HaeTiedotOnnistui :: vastaus" (pr-str vastaus))
      (-> app
        (assoc :tiedot vastaus)
        (assoc :nayta-rivit vastaus))))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeTiedotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeTiedotEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  PaivitaAikavali
  (process-event [{u :uudet} app]
    (let [_ (js/console.log "PaivitaAikavali :: uudet:" (pr-str u))
          uudet-valinnat (merge (:valinnat app) u)
          app (-> app
                (assoc :valinnat uudet-valinnat)
                #_(assoc :nayta-rivit (suodata-rivit-aikavalilla uudet-valinnat)))]
      (hae-paivakirjat app)
      app))

  PaivitaHakumuoto
  (process-event [u app]
    (let [_ (js/console.log "PaivitaHakumuoto :: u" (pr-str u))
          _ (js/console.log "PaivitaHakumuoto :: tila" (pr-str (dissoc @tila :nayta-rivit :tiedot)))
          uudet-valinnat (:uudet u)
          app (-> app
                (assoc-in [:valinnat :hakumuoto] uudet-valinnat)
                #_(assoc :nayta-rivit (suodata-rivit-aikavalilla (-> @tila :valinnat))))]
      (hae-paivakirjat app)
      app))

  ValitseRivi
  (process-event [{rivi :rivi} app]
    (-> app
      (assoc :valittu-rivi rivi)
      (assoc :viimeksi-valittu rivi)))

  PoistaRiviValinta
  (process-event [_ app]
    (-> app
      (assoc :valittu-rivi nil))))
