(ns harja.tiedot.hallinta.urakoiden-lyhytnimet
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [taoensso.timbre :as log]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]))

(defonce tila (atom {:valittu-urakkatyyppi nil}))

(defrecord HaeUrakoidenLyhytnimet [valinnat])
(defrecord HaeUrakoidenLyhytnimetOnnistui [vastaus])
(defrecord HaeUrakoidenLyhytnimetEpaonnistui [vastaus])
(defrecord PaivitaUrakoidenLyhytnimet [urakat])
(defrecord PaivitaUrakoidenLyhytnimetOnnistui [vastaus])
(defrecord PaivitaUrakoidenLyhytnimetEpaonnistui [vastaus])
(defrecord PaivitaValittuUrakkaTyyppi [valittu-urakkatyyppi])

(extend-protocol tuck/Event

  HaeUrakoidenLyhytnimet
  (process-event [{valinnat :valinnat} app]
    (let [parametrit {:urakkatyyppi (:urakkatyyppi valinnat)}]
      (println "PARAMETRIT: " parametrit)
      (println "APP: " app)
    (-> app
      (assoc :hae-urakoiden-lyhytnimet-kesken? true)
      (tuck-apurit/post! :hae-urakoiden-nimet parametrit
        {:onnistui ->HaeUrakoidenLyhytnimetOnnistui
         :epaonnistui ->HaeUrakoidenLyhytnimetEpaonnistui}))))

  HaeUrakoidenLyhytnimetOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app
      :UrakoidenLyhytnimet vastaus
      :hae-urakoiden-lyhytnimet-kesken? false))

  HaeUrakoidenLyhytnimetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Urakoiden nimien haku epäonnistui. Virhe: " vastaus)
    (viesti/nayta-toast! "Urakoiden nimien haku epäonnistui." :varoitus)
    (assoc app :hae-urakoiden-lyhytnimet-kesken? false))

  PaivitaUrakoidenLyhytnimet
  (process-event [{urakat :urakat urakkatyyppi :urakkatyyppi} app]
    (println "Päivitä parametrit: " urakat)
    (println "Urakkatyyppi: " urakkatyyppi)
    (tuck-apurit/post! :tallenna-urakan-lyhytnimi urakat
      {:onnistui ->PaivitaUrakoidenLyhytnimetOnnistui
       :epaonnistui ->PaivitaUrakoidenLyhytnimetEpaonnistui})
    (-> app
      (assoc :paivita-urakoiden-lyhytnimet-kesken? true)
      ))

  PaivitaUrakoidenLyhytnimetOnnistui
  (process-event [{vastaus :vastaus} app]
    (println "Tallennus onnistui!!!")
    (assoc app :UrakoidenLyhytnimet vastaus
      :paivita-urakoiden-lyhytnimet-kesken? false))

  PaivitaUrakoidenLyhytnimetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (log/error "Urakoiden lyhytnimien päivitys epäonnistui. Virhe: " vastaus)
    (viesti/nayta-toast! "Urakoiden lyhytnimien päivitys epäonnistui." :varoitus)
    (assoc app :paivita-urakoiden-lyhytnimet-kesken? false))

  PaivitaValittuUrakkaTyyppi
  (process-event [{valittu-urakkatyyppi :valittu-urakkatyyppi} app]
    (println "Valittu urakkatyyppi: " valittu-urakkatyyppi)
    app))
