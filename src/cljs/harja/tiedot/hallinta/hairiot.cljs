(ns harja.tiedot.hallinta.hairiot
  (:require [harja.pvm :as pvm]
            [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.domain.hairioilmoitus :as hairio]
            [harja.tiedot.hairioilmoitukset :as hairio-ui]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))
(def hairiot (atom nil))
(def asetetaan-hairioilmoitus? (atom false))

(def tyhja-hairioilmoitus {:tyyppi :hairio
                           :teksti nil
                           :alkuaika (pvm/nyt)})

(def tuore-hairioilmoitus (atom tyhja-hairioilmoitus))
(def tallennus-kaynnissa? (atom false))

(defn hae-hairiot []
  (go (let [vastaus (<! (k/post! :hae-hairioilmoitukset {}))]
        (if (k/virhe? vastaus)
          (viesti/nayta! "Häiriöilmoitusten haku epäonnistui" :warn)
          (reset! hairiot vastaus)))))

(defn aseta-hairioilmoitus [{:keys [tyyppi teksti alkuaika loppuaika]}]
  (reset! tallennus-kaynnissa? true)
  (go (let [vastaus (<! (k/post! :aseta-hairioilmoitus {::hairio/tyyppi tyyppi
                                                        ::hairio/viesti teksti
                                                        ::hairio/alkuaika alkuaika
                                                        ::hairio/loppuaika loppuaika}))]
        (reset! tallennus-kaynnissa? false)
        (reset! asetetaan-hairioilmoitus? false)
        (if (or
              (k/virhe? vastaus)
              (:virhe (first vastaus)))
          (viesti/nayta-toast!
            (str "Häiriöilmoituksen asettaminen epäonnistui!" "\n" (:virhe (first vastaus)))
            :varoitus
            (* 60 1000))
          (do (reset! hairiot vastaus)
            (reset! tuore-hairioilmoitus tyhja-hairioilmoitus)
            (hairio-ui/hae-tuorein-hairioilmoitus!))))))

(defn poista-hairioilmoitus [{:keys [id]}]
  (reset! tallennus-kaynnissa? true)
  (go (let [vastaus (<! (k/post! :aseta-hairioilmoitus-pois {::hairio/id id}))]
        (reset! tallennus-kaynnissa? false)
        (if (k/virhe? vastaus)
          (viesti/nayta! "Häiriöilmoituksen poistaminen epäonnistui!" :warn)
          (do (reset! hairiot vastaus)
            (hairio-ui/hae-tuorein-hairioilmoitus!))))))
