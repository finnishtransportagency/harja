(ns harja.palvelin.ajastetut-tehtavat.paivystajatarkistukset
  (:require [taoensso.timbre :as log]
            [chime :refer [chime-ch]]
            [chime :refer [chime-at]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [clj-time.core :as t]))

(defn tarkista-paivan-urakoiden-paivystykset [pvm]
  (log/debug "Tarkistetaan urakkakohtaisesti, onko annetulle päivälle " (pr-str pvm) " olemassa päivystys."))

(defn tee-paivystajien-tarkistustehtava [this]
  (log/debug "Ajastetaan päivystäjien tarkistus")
  (ajastettu-tehtava/ajasta-paivittain
    [5 0 0]
    (fn [_]
      (tarkista-paivan-urakoiden-paivystykset (t/now)))))

(defrecord PaivystajaTarkastukset []
  component/Lifecycle
  (start [this]
    (assoc this
      :paivystajien-tarkistus (tee-paivystajien-tarkistustehtava this)))
  (stop [this]
    (doseq [tehtava [::paivystajien-tarkistus]
            :let [lopeta-fn (get this tehtava)]]
      (when lopeta-fn (lopeta-fn)))
    this))