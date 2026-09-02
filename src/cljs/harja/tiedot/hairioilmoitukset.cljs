(ns harja.tiedot.hairioilmoitukset
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<! timeout]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def tarkkailuvali-ms (* 1000 60))
(def hairion-piilotusaika-ms (* 1000 60 60))
(def tuore-hairioilmoitus (atom nil))
(def tarkkaile-hairioilmoituksia? (atom false))
(def nayta-hairioilmoitus? (atom true))

;; Tyyppikohtaiset ilmoitukset ja piilotustilat
(def hairioilmoitukset-tyypeittain (atom nil))
(def nayta-hairioilmoitus-tyypeittain? (atom {:hairio true :tiedote true}))

(defn hae-tuorein-hairioilmoitus! []
  (go (let [vastaus (<! (k/post! :hae-voimassaoleva-hairioilmoitus {}))]
        (when-not (k/virhe? vastaus)
          (reset! tuore-hairioilmoitus vastaus)
          (reset! hairioilmoitukset-tyypeittain (:hairioilmoitukset-tyypeittain vastaus))))))

(defn tarkkaile-hairioilmoituksia! []
  (when-not @tarkkaile-hairioilmoituksia?
    (reset! tarkkaile-hairioilmoituksia? true)
    (hae-tuorein-hairioilmoitus!)
    (go-loop []
             (<! (timeout tarkkailuvali-ms))
             (hae-tuorein-hairioilmoitus!)
             (recur))))

(defn piilota-hairioilmoitus! []
  (reset! nayta-hairioilmoitus? false)
  (go
    (<! (timeout hairion-piilotusaika-ms))
    (reset! nayta-hairioilmoitus? true)))

(defn piilota-hairioilmoitus-tyypilla! [tyyppi]
  (swap! nayta-hairioilmoitus-tyypeittain? assoc tyyppi false)
  (go
    (<! (timeout hairion-piilotusaika-ms))
    (swap! nayta-hairioilmoitus-tyypeittain? assoc tyyppi true)))

