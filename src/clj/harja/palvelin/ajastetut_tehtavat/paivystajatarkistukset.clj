(ns harja.palvelin.ajastetut-tehtavat.paivystajatarkistukset
  (:require [taoensso.timbre :as log]
            [chime :refer [chime-ch]]
            [chime :refer [chime-at]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot-q]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [clj-time.core :as t]
            [harja.kyselyt.konversio :as konv]))

(defn- tarkista-paivan-urakoiden-paivystykset [urakoiden-paivystykset pvm]
  (log/debug "Tarkistetaan urakkakohtaisesti, onko annetulle päivälle " (pr-str pvm) " olemassa päivystys."))

(defn hae-urakoiden-paivystykset [db]
  (let [urakoiden-paivystykset (into []
                                     (map konv/alaviiva->rakenne)
                                     (yhteyshenkilot-q/hae-kaynissa-olevien-urakoiden-paivystykset db))
        urakoiden-paivystykset (konv/sarakkeet-vektoriin
                                 urakoiden-paivystykset
                                 {:paivystys :paivystykset}
                                 :id)]
    urakoiden-paivystykset))

(defn- paivystajien-tarkistustehtava [db pvm]
  (let [urakoiden-paivystykset (hae-urakoiden-paivystykset db)]
    (tarkista-paivan-urakoiden-paivystykset urakoiden-paivystykset pvm)))

(defn tee-paivystajien-tarkistustehtava [{:keys [db] :as this}]
  (log/debug "Ajastetaan päivystäjien tarkistus")
  (ajastettu-tehtava/ajasta-paivittain
    [5 0 0]
    (fn [_]
      (paivystajien-tarkistustehtava db (t/now)))))

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