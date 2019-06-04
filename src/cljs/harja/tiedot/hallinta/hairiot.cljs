(ns harja.tiedot.hallinta.hairiot
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.hallinta.yhteydenpito :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.loki :refer [log]]
            [harja.tiedot.hairioilmoitukset :as hairio-ui]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [reagent.ratom :refer [reaction]]))

(def nakymassa? (atom false))
(def hairiot (atom nil))
(def asetetaan-hairioilmoitus? (atom false))
(def tuore-hairioilmoitus (atom {:tyyppi :hairio
                                 :teksti nil}))
(def tallennus-kaynnissa? (atom false))

(defn hae-hairiot []
  (go (let [vastaus (<! (k/post! :hae-hairioilmoitukset {}))]
        (if (k/virhe? vastaus)
          (viesti/nayta! "Häiriöilmoitusten haku epäonnistui" :warn)
          (reset! hairiot vastaus)))))

(defn aseta-hairioilmoitus [hairioilmoitus]
  (reset! tallennus-kaynnissa? true)
  (go (let [vastaus (<! (k/post! :aseta-hairioilmoitus {::hairio/tyyppi (:tyyppi hairioilmoitus)
                                                        ::hairio/viesti (:teksti hairioilmoitus)}))]
        (reset! tallennus-kaynnissa? false)
        (reset! asetetaan-hairioilmoitus? false)
        (if (k/virhe? vastaus)
          (viesti/nayta! "Häiriöilmoituksen asettaminen epäonnistui!" :warn)
          (do (reset! hairiot vastaus)
              (reset! tuore-hairioilmoitus {:tyyppi :hairio :teksti nil})
              (hairio-ui/hae-tuorein-hairioilmoitus!))))))

(defn poista-hairioilmoitus []
  (reset! tallennus-kaynnissa? true)
  (go (let [vastaus (<! (k/post! :aseta-kaikki-hairioilmoitukset-pois {}))]
        (reset! tallennus-kaynnissa? false)
        (if (k/virhe? vastaus)
          (viesti/nayta! "Häiriöilmoituksen poistaminen epäonnistui!" :warn)
          (do (reset! hairiot vastaus)
              (hairio-ui/hae-tuorein-hairioilmoitus!))))))
