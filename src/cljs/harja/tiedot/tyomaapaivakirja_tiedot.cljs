(ns harja.tiedot.tyomaapaivakirja-tiedot
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
                  :myohastyneet "myohassa"
                  :puuttuvat "puuttuu"})

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

(defn suodata-rivit
  "Annetaan parametrina tilan :valinnat josta luetaan hakumuoto"
  [app]
  (let [rivit (filter (fn [rivi]
                        (let [valittu-hakumuoto (get suodattimet (get-in app [:valinnat :hakumuoto]))

                              rivin-toimitustila (if (not= "kommentoitu" (get-in app [:valinnat :hakumuoto]))
                                                   (:tila rivi)
                                                   "kommentoitu")]
                          (or
                            ;; Vastaako valittu hakumuoto rivin toimituksen tilaa
                            (= valittu-hakumuoto nil)
                            (= valittu-hakumuoto rivin-toimitustila)))) (:tiedot app))]
    rivit))


(defn- hae-paivakirjat [app]
  (let [aikavali (get-in app [:valinnat :aikavali])
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
    (hae-paivakirjat app))

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
      (assoc :tiedot vastaus)
      (assoc :nayta-rivit vastaus)))

  HaeTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeTiedotEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeTiedotEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  PaivitaAikavali
  (process-event [{uudet :uudet} app]
    (let [uudet-valinnat (merge (:valinnat app) uudet)
          app (assoc app :valinnat uudet-valinnat)]
      ;; Aikavälin päivittäminen käynnistää tietokannasta hakemisen aina
      (hae-paivakirjat app)
      app))

  PaivitaHakumuoto
  (process-event [{uudet :uudet} app]
    (let [app (assoc-in app [:valinnat :hakumuoto] uudet)
          app (assoc app :nayta-rivit (suodata-rivit app))]
      app))

  ValitseRivi
  (process-event [{rivi :rivi} app]
    (if (not= "puuttuu" (:tila rivi))
      (-> app
        (assoc :valittu-rivi rivi)
        (assoc :viimeksi-valittu rivi))
      (do
        (viesti/nayta-toast! "Valitun päivän työmaapäiväkirjaa ei ole vielä lähetetty." :varoitus)
        app)))

  PoistaRiviValinta
  (process-event [_ app]
    (-> app
      (assoc :valittu-rivi nil))))
