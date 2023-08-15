(ns harja.tiedot.tyomaapaivakirja-tiedot
  "Työmaapäiväkirja kutsut"
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.ui.viesti :as viesti]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.pvm :as pvm]
            [harja.tiedot.raportit :as raportit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.nakymasiirrin :as siirrin])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))
(defonce raportti-avain :tyomaapaivakirja-nakyma)

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

(defonce raportin-parametrit
  (reaction (let [ur @nav/valittu-urakka
                  valittu-tila @tila
                  valittu-rivi (:valittu-rivi valittu-tila)
                  parametrit (raportit/urakkaraportin-parametrit
                               (:id ur)
                               raportti-avain
                               {:urakkatyyppi (:tyyppi ur)
                                :valittu-rivi valittu-rivi})]
              parametrit)))

(defonce raportin-tiedot
  (reaction<! [p @raportin-parametrit]
    {:nil-kun-haku-kaynnissa? true}
    (when 
      (and p @nakymassa?)
      (raportit/suorita-raportti p))))

(defrecord HaeTiedot [])
(defrecord ValitseRivi [rivi])
(defrecord PoistaRiviValinta [])
(defrecord SelaaPaivakirjoja [suunta])
(defrecord PaivitaAikavali [uudet])
(defrecord PaivitaHakumuoto [uudet])
(defrecord HaeTiedotOnnistui [vastaus])
(defrecord HaeTiedotEpaonnistui [vastaus])
(defrecord TallennaKommentti [kommentti])
(defrecord TallennaKommenttiOnnistui [vastaus])
(defrecord TallennaKommenttiEpaonnistui [vastaus])
(defrecord HaeKommentit [])
(defrecord HaeKommentitOnnistui [vastaus])
(defrecord HaeKommentitEpaonnistui [vastaus])
(defrecord PoistaKommentti [tiedot])
(defrecord PoistaKommenttiOnnistui [vastaus])
(defrecord PoistaKommenttiEpaonnistui [vastaus])

(defn- selaa-paivakirjoja
  "Työmaapäiväkirjan sticky bar Edellinen/Seuraava toiminto
   Jos päiväkirjoja puuttuu välistä, palauttaa vectorin hypätyistä riveistä, viimeisenä elementtinä riville jolle hypättiin"
  [y data id suunta]
  (let [aja? (if (= suunta :seuraava)
               (> (count data) (dec y))
               (> y -1))
        rivi (nth data y nil)
        rivi-id (:tyomaapaivakirja_id rivi)
        indeksi (if (= suunta :seuraava) (inc y) (dec y))
        seuraava-rivi (nth data indeksi nil)
        seuraava-rivi-id (:tyomaapaivakirja_id seuraava-rivi)
        aja-loput-rivit (fn [y data suunta data-seuraavat]
                          ;; Loopataan päiväkirjalistaus loppuun, katsotaan onko seuraavaa riviä ollenkaan olemassa
                          ;; Jos on -> palautetaan data riveistä mitkä hypättiin
                          (let [i (if (= suunta :seuraava) (inc y) (dec y))
                                ;; Ajetaanko seuraava loop
                                aja? (if (= suunta :seuraava)
                                       (> (count data) i)
                                       (> i -1))
                                rivin-data (nth data i nil)
                                rivi-tila (:tila rivin-data)
                                rivi-id (:tyomaapaivakirja_id rivin-data)
                                data-seuraavat (conj data-seuraavat rivin-data)]
                            (when aja?
                              (if (or (nil? rivi-tila) (nil? rivi-id))
                                ;; Seuraavaa työmaapäiväkirjaa ei ole, jatka kunnes käyty kaikki
                                (recur i data suunta data-seuraavat)
                                ;; Seuraava löytyi, palauta kaikki tulokset, myös puuttuvat
                                data-seuraavat))))]
    ;; Ajetaanko seuraava loop
    (when aja?
      (if (= rivi-id id)
        ;; Seuraavaa päiväkirjaa ei ole olemassa
        (if (nil? seuraava-rivi-id)
          ;; Ajetaan loput rivit ja katsotaan hypättiinkö rivejä
          ;; Palautetaan vector hypätyistä riveistä
          [(aja-loput-rivit y data suunta []) seuraava-rivi]
          seuraava-rivi)
        (recur (inc y) data id suunta)))))

(defn- ilmoita-hypatyt-rivit [hypatyt-rivit suunta]
  ;; Ilmoitetaan käyttäjälle että rivejä hypättiin
  (when (not-empty hypatyt-rivit)
    (let [pvm-alku (if (= suunta :seuraava)
                     (pvm/pvm (:paivamaara (first hypatyt-rivit)))
                     (pvm/pvm (:paivamaara (last hypatyt-rivit))))
          pvm-loppu (if (= suunta :seuraava)
                      (pvm/pvm (:paivamaara (last hypatyt-rivit)))
                      (pvm/pvm (:paivamaara (first hypatyt-rivit))))
          aikavali (if (= pvm-alku pvm-loppu)
                     pvm-alku
                     (str pvm-alku " - " pvm-loppu))]
      (viesti/nayta-toast! (str "Ohitettiin puuttuvat päiväkirjat aikaväliltä: " aikavali) :neutraali-ikoni-keskella 10000))))

(defn siirry-elementin-id [id aika]
  (siirrin/kohde-elementti-id id)
  (.setTimeout js/window (fn [] (siirrin/kohde-elementti-id id)) aika))

(defn scrollaa-kommentteihin []
  ;; Kutsutaan kun käyttäjä poistaa/lisää kommentin
  ;; Tehty nopeaksi (10ms)
  (siirry-elementin-id "Kommentit" 10))

(defn scrollaa-viimeksi-valitulle-riville [e!]
  ;; Poistetaan rivivalinta ja rullataan käyttäjä viimeksi klikatulle riville
  (e! (->PoistaRiviValinta))
  (.setTimeout js/window (fn [] (siirrin/kohde-elementti-luokka "viimeksi-valittu-tausta")) 150))

(defn suodata-rivit
  "Suodatetaan tulokset käyttäjän valitsemien suodattimien perusteella"
  [{:keys [tiedot]} hakumuoto]
  (filter
    (fn [{:keys [tila kommenttien-maara]}]
      (let [rivin-toimitustila tila]
        (or
          ;; Tila valittuna (Myöhästyneet / puuttuvat) 
          ;; -> Vastaako valittu hakumuoto rivin toimituksen tilaa
          (and
            hakumuoto
            (or
              (and (= hakumuoto :puuttuvat) (= rivin-toimitustila "puuttuu"))
              (and (= hakumuoto :myohastyneet) (= rivin-toimitustila "myohassa"))))
          ;; Kommentoidut valittu -> onko rivi kommentoitu
          (and
            (= hakumuoto :kommentoidut)
            (> kommenttien-maara 0))
          ;; Kaikki valittu -> pass 
          (= hakumuoto :kaikki)))) tiedot))

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
    (hae-paivakirjat app)
    app)

  HaeTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (assoc app :tiedot vastaus)]
      ;; Suodatetaan vielä rivit vastaukseen
      (assoc app :nayta-rivit (suodata-rivit app (get-in app [:valinnat :hakumuoto])))))

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
          app (assoc app :nayta-rivit (suodata-rivit app uudet))]
      app))

  ValitseRivi
  (process-event [{rivi :rivi} app]
    (if (or (= "ok" (:tila rivi)) (= "myohassa" (:tila rivi)))
      (do
        (swap! tila assoc :valittu-rivi rivi)
        (-> app
          (assoc :valittu-rivi rivi)
          (assoc :viimeksi-valittu rivi)))
      (do
        (viesti/nayta-toast! "Valitun päivän työmaapäiväkirjaa ei ole vielä lähetetty." :varoitus)
        app)))

  PoistaRiviValinta
  (process-event [_ app]
    ;; Raportin sulkeminen käynnistää listauksen hakemisen tietokannasta aina
    (hae-paivakirjat app)
    (-> app
      (assoc :valittu-rivi nil)))

  SelaaPaivakirjoja
  (process-event [{suunta :suunta} {:keys [nayta-rivit valittu-rivi] :as app}]
    ;; Etsitään seuraava/edellinen päiväkirja listauksesta 
    (let [etsitty-rivi (selaa-paivakirjoja 0 (vec nayta-rivit) (:tyomaapaivakirja_id valittu-rivi) suunta)
          ;; Jos etsitty on vectori, hypättiin rivejä
          hypatyt-rivit (when (vector? etsitty-rivi) (first etsitty-rivi))
          ;; Viimeinen elementti on mille riville hypättiin
          hyppaa-riville (last hypatyt-rivit)
          ;; Muut elementit ovat rivejä mitkä hypättiin yli
          hypatyt-rivit (drop-last hypatyt-rivit)
          ;; Näytä viesti jos rivejä hypättiin
          etsitty-rivi (if (map? hyppaa-riville)
                         (do
                           (ilmoita-hypatyt-rivit hypatyt-rivit suunta)
                           hyppaa-riville)
                         ;; Else -> Rivejä ei hypätty
                         etsitty-rivi)
          ;; Jos seuraava päiväkirjaa ei ole, ollaan rullattu loppuun
          etsitty-rivi (if (or 
                             (nil? (:tila etsitty-rivi))
                             (nil? (:tyomaapaivakirja_id etsitty-rivi))) nil etsitty-rivi)]
      ;; Jos rivi olemassa, lataa se ja maalaa listauksessa valituksi
      (if etsitty-rivi
        (assoc app
          :valittu-rivi etsitty-rivi
          :viimeksi-valittu etsitty-rivi)
        ;; Päiväkirjat rullattu loppuun
        (do
          (scrollaa-viimeksi-valitulle-riville app)
          (assoc app :valittu-rivi etsitty-rivi)))))

  TallennaKommentti
  (process-event [{kommentti :kommentti} {:keys [valittu-rivi] :as app}]
    (tuck-apurit/post! app :tyomaapaivakirja-tallenna-kommentti
      {:urakka-id (:id @nav/valittu-urakka)
       :tyomaapaivakirja_id (:tyomaapaivakirja_id valittu-rivi)
       :versio (:versio valittu-rivi)
       :kommentti kommentti}
      {:onnistui ->TallennaKommenttiOnnistui
       :epaonnistui ->TallennaKommenttiEpaonnistui})
    app)

  TallennaKommenttiOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :kommentit vastaus))

  TallennaKommenttiEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaKommenttiEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "TallennaKommenttiEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  HaeKommentit
  (process-event [_ {:keys [valittu-rivi] :as app}]
    (tuck-apurit/post! app :tyomaapaivakirja-hae-kommentit
      {:urakka-id (:id @nav/valittu-urakka)
       :tyomaapaivakirja_id (:tyomaapaivakirja_id valittu-rivi)}
      {:onnistui ->HaeKommentitOnnistui
       :epaonnistui ->HaeKommentitEpaonnistui})
    app)

  HaeKommentitOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc-in app [:valittu-rivi :kommentit] vastaus))

  HaeKommentitEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "HaeKommentitEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "HaeKommentitEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app)

  PoistaKommentti
  (process-event [{tiedot :tiedot} app]
    ;; Sallitaan vaan omien kommenttien poisto
    (when (not= (:id @istunto/kayttaja) (:luoja tiedot))
      (viesti/nayta-toast! (str "Et voi poistaa muiden käyttäjien kommentteja.") :neutraali 3000))
    (tuck-apurit/post! app :tyomaapaivakirja-poista-kommentti
      {:kayttaja (:id @istunto/kayttaja)
       :urakka-id (:id @nav/valittu-urakka)
       :id (:id tiedot)
       :tyomaapaivakirja_id (:tyomaapaivakirja_id tiedot)}
      {:onnistui ->PoistaKommenttiOnnistui
       :epaonnistui ->PoistaKommenttiEpaonnistui})
    app)

  PoistaKommenttiOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc-in app [:valittu-rivi :kommentit] vastaus))

  PoistaKommenttiEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaKommenttiEpaonnistui :: vastaus: " (pr-str vastaus))
    (viesti/nayta-toast! (str "PoistaKommenttiEpaonnistui \n Vastaus: " (pr-str vastaus)) :varoitus)
    app))
