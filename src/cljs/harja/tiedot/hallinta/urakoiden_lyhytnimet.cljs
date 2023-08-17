(ns harja.tiedot.hallinta.urakoiden-lyhytnimet
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [taoensso.timbre :as log]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]))

(defonce tila (atom {:valittu-urakkatyyppi {:nimi "Kaikki" :arvo :kaikki}
                     :vain-puuttuvat false
                     :urakan-tila :kaikki}))

(defrecord HaeUrakoidenLyhytnimet [valinnat])
(defrecord HaeUrakoidenLyhytnimetOnnistui [vastaus])
(defrecord HaeUrakoidenLyhytnimetEpaonnistui [vastaus])
(defrecord PaivitaUrakoidenLyhytnimet [urakat])
(defrecord PaivitaUrakoidenLyhytnimetOnnistui [vastaus])
(defrecord PaivitaUrakoidenLyhytnimetEpaonnistui [vastaus])
(defrecord PaivitaValittuUrakkaTyyppi [valittu-urakkatyyppi])
(defrecord PaivitaVainPuuttuvat [vain-puuttuvat])
(defrecord PaivitaUrakanTila [urakan-tila])

(defn- hae-urakoiden-nimet [parametrit]
  (tuck-apurit/post! :hae-urakoiden-nimet parametrit
    {:onnistui ->HaeUrakoidenLyhytnimetOnnistui
     :epaonnistui ->HaeUrakoidenLyhytnimetEpaonnistui}))

(defn- parsi-urakkatyyppi [app]
  (:arvo (:valittu-urakkatyyppi app)))

(defn- hakuparametrit [app]
   {:urakkatyyppi (parsi-urakkatyyppi app)
    :vain-puuttuvat (:vain-puuttuvat app)
    :urakan-tila (:urakan-tila app)})

(extend-protocol tuck/Event

  HaeUrakoidenLyhytnimet
  (process-event [_ app]
    (do
      (hae-urakoiden-nimet (hakuparametrit app))
      (assoc app :hae-urakoiden-lyhytnimet-kesken? true)))

  HaeUrakoidenLyhytnimetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :urakoiden-lyhytnimet vastaus
      :hae-urakoiden-lyhytnimet-kesken? false))

  HaeUrakoidenLyhytnimetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Urakoiden nimien haku epäonnistui. Virhe: " vastaus)
    (viesti/nayta-toast! "Urakoiden nimien haku epäonnistui." :varoitus)
    (assoc app :hae-urakoiden-lyhytnimet-kesken? false))

  PaivitaUrakoidenLyhytnimet
  (process-event [{urakat :urakat} app]
    (do
      (tuck-apurit/post! :tallenna-urakoiden-lyhytnimet {:tiedot urakat :haku-parametrit (hakuparametrit app)}
        {:onnistui ->PaivitaUrakoidenLyhytnimetOnnistui
         :epaonnistui ->PaivitaUrakoidenLyhytnimetEpaonnistui})
      (assoc app :paivita-urakoiden-lyhytnimet-kesken? true)))

  PaivitaUrakoidenLyhytnimetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :urakoiden-lyhytnimet vastaus
      :paivita-urakoiden-lyhytnimet-kesken? false))

  PaivitaUrakoidenLyhytnimetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Urakoiden lyhytnimien päivitys epäonnistui. Virhe: " vastaus)
    (viesti/nayta-toast! "Urakoiden lyhytnimien päivitys epäonnistui." :varoitus)
    (assoc app :paivita-urakoiden-lyhytnimet-kesken? false))

  PaivitaValittuUrakkaTyyppi
  (process-event [{valittu-urakkatyyppi :valittu-urakkatyyppi} app]
    (let [app (assoc app :valittu-urakkatyyppi valittu-urakkatyyppi)]
      (hae-urakoiden-nimet (hakuparametrit app))
      app))

  PaivitaVainPuuttuvat
  (process-event [{vain-puuttuvat :vain-puuttuvat} app]
    (let [app (assoc app :vain-puuttuvat vain-puuttuvat)]
      (hae-urakoiden-nimet (hakuparametrit app))
      app))

  PaivitaUrakanTila
  (process-event [{urakan-tila :urakan-tila} app]
    (let [app (assoc app :urakan-tila urakan-tila)]
      (hae-urakoiden-nimet (hakuparametrit app))
      app)))
