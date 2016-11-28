(ns harja-laadunseuranta.ui.ilmoitukset
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer [timeout <!]]
            [harja-laadunseuranta.tiedot.ilmoitukset :as tiedot]
            [cljs-time.local :as l]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn ilmoitusjonokomponentti
  "Piirtää jonossa olevat ilmoitukset yksi kerrallaan"
  [ilmoitukset-atom]
  (let [ilmoitus-nakyvissa? (atom false)]

    ;; Poista ilmoitus näkyvistä asynkronisesti tietyn ajan päästä,
    ;; ellei ole jo pyydetty poistamaan
    (when (and (not @ilmoitus-nakyvissa?)
               (not (empty? @ilmoitukset-atom)))
      (reset! ilmoitus-nakyvissa? true)
      (go (<! (timeout tiedot/+ilmoituksen-nakymisaika-ms+))
          ;; Tyhjennetään jonosta näytetty ilmoitus pois
          (reset! ilmoitukset-atom (vec (rest @ilmoitukset-atom)))
          (reset! ilmoitus-nakyvissa? nil)))

    (when (first @ilmoitukset-atom)
      [:div.ilmoitukset
       [:div.ilmoitus (:ilmoitus (first @ilmoitukset-atom))]])))

(defn ilmoituskomponentti
  "Piirtää nykyisen ilmoituksen"
  [ilmoitus-atom]
  (when @ilmoitus-atom
    (tiedot/tyhjenna-ilmoitus-nakymisajan-jalkeen @ilmoitus-atom ilmoitus-atom))

  (when @ilmoitus-atom
    [:div.ilmoitukset
     [:div.ilmoitus (:ilmoitus @ilmoitus-atom)]]))