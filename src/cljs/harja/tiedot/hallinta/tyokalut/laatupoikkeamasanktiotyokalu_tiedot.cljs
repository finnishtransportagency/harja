(ns harja.tiedot.hallinta.tyokalut.laatupoikkeamasanktiotyokalu-tiedot
  "Hallinnan sanktiotyokalun ui controlleri."
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def +sanktiolajit+
  [:muistutus :A :B :C :arvonvahennyssanktio])

(defn sanktiotyypit-lajille [app sanktio]
  (let [urakan-alkupvm (or (get-in sanktio [:valittu-urakka :alkupvm])
                         (get-in @nav/valittu-urakka [:alkupvm])
                         (pvm/nyt))]
    (vec (sanktio-domain/sanktiolaji->sanktiotyypit
           (:laji sanktio)
           (:sanktiotyypit app)
           urakan-alkupvm))))

(defn- paivita-tyyppi [app sanktio]
  (let [valinnat (sanktiotyypit-lajille app sanktio)
        valittu-id (get-in sanktio [:tyyppi :id])
        tyyppi-loytyy? (some #(= (:id %) valittu-id) valinnat)]
    (cond
      (empty? valinnat)
      (assoc sanktio :tyyppi nil)

      tyyppi-loytyy?
      sanktio

      :else
      (assoc sanktio :tyyppi (first valinnat)))))

(defn- koordinaatit-annettu?
  [{:keys [alku-x alku-y]}]
  (and (number? alku-x) (number? alku-y)))

(defn- muodosta-alkusijainti
  [{:keys [alku-x alku-y] :as sanktio}]
  (when (koordinaatit-annettu? sanktio)
    {:x alku-x :y alku-y}))

(defn- muodosta-loppusijainti
  [{:keys [loppu-x loppu-y]}]
  (when (and (number? loppu-x) (number? loppu-y))
    {:x loppu-x :y loppu-y}))

(def alkutila
  {:sanktio {:valittu-hallintayksikko nil
             :valittu-urakka nil
             :sanktiomuoto :suorasanktio
             :laji :A
             :tyyppi nil
             :summa 1000
             :perintapvm (pvm/nyt)
             :maarattypvm (pvm/nyt)
             :paivamaara (pvm/nyt)
             :kasittelyaika (pvm/nyt)
             :kasittelytapa :valikatselmus
             :alku-x 430746
             :alku-y 7199055
             :loppu-x nil
             :loppu-y nil
             :kohde "Leppäjärven ramppi"
             :kuvaus "Laatupoikkeaman kuvaus"
             :perustelu "Sanktion perustelu"
             :liitteet []}
   :sanktiotyypit []
   :sanktiotyypit-haku-kaynnissa? false
   :tieosoitteen-haku-kaynnissa? false
   :haettu-tr-osoite nil
   :lahetys-kaynnissa? false
   :mahdolliset-urakat []})

(def data (atom alkutila))
(def nakymassa? (atom false))

(defn koosta-json-map [app]
  {:otsikko {
             :lahettaja {
                         :jarjestelma "Urakoitsijan järjestelmä",
                         :organisaatio {
                                        :nimi "Urakoitsija",
                                        :ytunnus "1234567-8"}},
             :viestintunniste {:id 123},
             :lahetysaika (pvm/aika->str-iso8601-UTC (get-in app [:sanktio :kasittelyaika]))},
   :tunniste {:id 123},
   :alkusijainti {:x (get-in app [:sanktio :alku-x]),
                  :y (get-in app [:sanktio :alku-y])},
   :kuvaus (get-in app [:sanktio :kuvaus]),
   :kohde (get-in app [:sanktio :kohde]),
   :kirjaaja {:id 54697481,
              :etunimi "Ville",
              :sukunimi "Vaara"},
   :aika (pvm/aika->str-iso8601-UTC (get-in app [:sanktio :kasittelyaika])),
   :sisaltaa-poikkeamaraportin true,
   :liitteet (or (get-in app [:sanktio :liitteet]) []),
   :kommentit []})

(defrecord Muokkaa [sanktio])
(defrecord Laheta [sanktio])
(defrecord LahetysOnnistui [vastaus])
(defrecord LahetysEpaonnistui [vastaus])

(defrecord HaeHallintayksikonUrakatOnnistui [vastaus])
(defrecord HaeHallintayksikonUrakatEpaonnistui [vastaus])
(defrecord HaeSanktiotyypit [])
(defrecord HaeSanktiotyypitOnnistui [vastaus])
(defrecord HaeSanktiotyypitEpaonnistui [vastaus])
(defrecord HaeTieosoite [])
(defrecord HaeTieosoiteOnnistui [vastaus])
(defrecord HaeTieosoiteEpaonnistui [vastaus])

(defn- hae-hallintayksikon-urakat [hallintayksikko]
  (when hallintayksikko
    (tuck-apurit/post! :hallintayksikon-urakat
      (:id hallintayksikko)
      {:onnistui ->HaeHallintayksikonUrakatOnnistui
       :epaonnistui ->HaeHallintayksikonUrakatEpaonnistui
       :paasta-virhe-lapi? true})))

(extend-protocol tuck/Event
  Muokkaa
  (process-event [{sanktio :sanktio} app]
    (let [hallintayksikko-vaihtui? (not= (get-in app [:sanktio :valittu-hallintayksikko])
                                     (:valittu-hallintayksikko sanktio))
          koordinaatit-muuttuivat? (not= (select-keys (get-in app [:sanktio]) [:alku-x :alku-y :loppu-x :loppu-y])
                                     (select-keys sanktio [:alku-x :alku-y :loppu-x :loppu-y]))
          sanktio (if hallintayksikko-vaihtui?
                    (do
                      (hae-hallintayksikon-urakat (:valittu-hallintayksikko sanktio))
                      (assoc sanktio :valittu-urakka nil))
                    sanktio)
          sanktio (if (= :muistutus (:laji sanktio))
                    (assoc sanktio :summa nil)
                    sanktio)
          sanktio (paivita-tyyppi app sanktio)]
      (cond-> (assoc app :sanktio sanktio)
        koordinaatit-muuttuivat?
        (assoc :haettu-tr-osoite nil))))

  HaeTieosoite
  (process-event [_ app]
    (let [sanktio (:sanktio app)
          alkusijainti (muodosta-alkusijainti sanktio)
          loppusijainti (muodosta-loppusijainti sanktio)]
      (if-not alkusijainti
        (do
          (viesti/nayta-toast! "Anna ensin alkusijainnin x- ja y-koordinaatit" :varoitus)
          app)
        (do
          (tuck-apurit/post! :debug-hae-tierekisteriosoite-koordinaateista
            {:alkusijainti alkusijainti
             :loppusijainti loppusijainti}
            {:onnistui ->HaeTieosoiteOnnistui
             :epaonnistui ->HaeTieosoiteEpaonnistui
             :paasta-virhe-lapi? true})
          (assoc app :tieosoitteen-haku-kaynnissa? true)))))

  HaeTieosoiteOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [tr-osoite (:tr-osoite vastaus)]
      (if tr-osoite
        (viesti/nayta-toast! "Tieosoite löytyi koordinaateista" :onnistui)
        (viesti/nayta-toast! "Tieosoitetta ei löytynyt annetuista koordinaateista" :varoitus))
      (-> app
        (assoc :haettu-tr-osoite tr-osoite)
        (assoc :tieosoitteen-haku-kaynnissa? false))))

  HaeTieosoiteEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (or (k/virheviesti vastaus)
                           "Tieosoitteen haku epäonnistui")
      :varoitus
      viesti/viestin-nayttoaika-pitka)
    (assoc app :tieosoitteen-haku-kaynnissa? false))

  Laheta
  (process-event [{sanktio :sanktio} app]
    (let [tulos! (tuck/send-async! ->LahetysOnnistui)
          virhe! (tuck/send-async! ->LahetysEpaonnistui)
          urakka-id (get-in sanktio [:valittu-urakka :id])
          payload (koosta-json-map app)
          url (str "api/urakat/" urakka-id "/laatupoikkeama")]
      (if-not urakka-id
        (do
          (viesti/nayta-toast! "Urakka pitää valita ennen lähetystä" :varoitus)
          (assoc app :lahetys-kaynnissa? false))
        (do
          (go
            (let [params {:body (.stringify js/JSON (clj->js payload))
                          :content-type :json
                          :accept :json
                          :headers {"X-CSRF-Token" (k/get-csrf-token)}}
                  vastaus (<! (http/post url params))]
              (if (or (k/virhe? vastaus) (false? (:success vastaus)))
                (virhe! vastaus)
                (tulos! vastaus))))
          (assoc app :lahetys-kaynnissa? true)))))

  LahetysOnnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Sanktion lähetys onnistui" :onnistui)
    (assoc app :lahetys-kaynnissa? false))

  LahetysEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (or (k/virheviesti vastaus)
                           "Sanktion lähetys epäonnistui")
      :varoitus
      viesti/viestin-nayttoaika-pitka)
    (assoc app :lahetys-kaynnissa? false))

  HaeHallintayksikonUrakatOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :mahdolliset-urakat vastaus))

  HaeHallintayksikonUrakatEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Urakoiden haku epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
    app)

  HaeSanktiotyypit
  (process-event [_ app]
    (tuck-apurit/get! :hae-sanktiotyypit
      {:onnistui ->HaeSanktiotyypitOnnistui
       :epaonnistui ->HaeSanktiotyypitEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :sanktiotyypit-haku-kaynnissa? true))

  HaeSanktiotyypitOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [app (-> app
                (assoc :sanktiotyypit vastaus)
                (assoc :sanktiotyypit-haku-kaynnissa? false))
          paivitetty (paivita-tyyppi app (:sanktio app))]
      (assoc app :sanktio paivitetty)))

  HaeSanktiotyypitEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Sanktiotyyppien haku epäonnistui" :varoitus viesti/viestin-nayttoaika-pitka)
    (assoc app :sanktiotyypit-haku-kaynnissa? false)))

