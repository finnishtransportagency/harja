(ns harja.tiedot.hallinta.yhteydenpito
  (:require [reagent.core :refer [atom]]
            [harja.loki :refer [log tarkkaile!]]
            [cljs.core.async :refer [<! >! chan]]
            [tuck.core :as tuck]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k]
            [tuck.core :as t]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]))

(def alkutila {:yhteydenotto {:otsikko ""
                              :sisalto "Hei,\n\n<Kirjoita viestisi tähän>\n[Älä vastaa tähän sähköpostiviestiin. Palautetta voit lähettää osoitteeseen harjapalaute@solita.fi]\n\nYstävällisin terveisin, \nHarjan ylläpito"}
               :lahetys-kaynnissa? false})
(def nakymassa? (atom false))
(def data (atom alkutila))

(defrecord Muokkaa [yhteydenotto])
(defrecord Laheta [yhteydenotto])
(defrecord LahetysOnnistui [])
(defrecord LahetysEpaonnistui [])

(extend-protocol tuck/Event
  Muokkaa
  (process-event [{yhteydenotto :yhteydenotto} app]
    (assoc app :yhteydenotto yhteydenotto))

  Laheta
  (process-event [{yhteydenotto :yhteydenotto} app]
    (let [tulos! (t/send-async! ->LahetysOnnistui)
          virhe! (t/send-async! ->LahetysEpaonnistui)]
      (go
        (let [vastaus (<! (k/post! :laheta-sahkoposti-kaikille-kayttajille (select-keys yhteydenotto [:otsikko :sisalto])))]
          (if (k/virhe? vastaus)
            (virhe!)
            (tulos!))))
      (app :lahetys-kaynnissa? true)))

  LahetysOnnistui
  (process-event [_ _]
    (viesti/nayta! "Sähköposti lähetetty" :success)
    alkutila)

  LahetysEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Sähköpostin lähetys epäonnistui" :warning)
    (assoc app :lahetys-kaynnissa? false)))





