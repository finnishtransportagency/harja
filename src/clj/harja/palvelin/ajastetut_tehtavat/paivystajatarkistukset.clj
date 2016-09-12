(ns harja.palvelin.ajastetut-tehtavat.paivystajatarkistukset
  (:require [taoensso.timbre :as log]
            [chime :refer [chime-ch]]
            [chime :refer [chime-at]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot-q]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [clj-time.core :as t]
            [harja.kyselyt.konversio :as konv]
            [clj-time.coerce :as c]))

(defn urakat-ilman-paivytysta
  "Palauttaa urakat, joille ei ole päivystystä kyseisenä päivänä"
  [urakoiden-paivystykset pvm]
  (log/debug "Tarkistetaan urakkakohtaisesti, onko annetulle päivälle " (pr-str pvm) " olemassa päivystys.")
  (let [urakalla-paivystys-annettuna-paivana?
        (fn [paivystykset pvm]
          (let [paivystys-annettuna-paivana? (fn [pvm paivystys-alku paivystys-loppu]
                                               (pvm/valissa? pvm
                                                             paivystys-alku
                                                             paivystys-loppu
                                                             false))]
            (some?
              (some #(paivystys-annettuna-paivana?
                      pvm
                      (:alku %)
                      (:loppu %))
                    paivystykset))))]

    (filter
      #(not (urakalla-paivystys-annettuna-paivana? (:paivystykset %) pvm))
      urakoiden-paivystykset)))

(defn hae-urakoiden-paivystykset
  "Hakee urakoiden päivystystiedot. Palauttaa vain sellaiset urakat jotka ovat olleet
   voimassa annetun päivämäärän aikana."
  [db pvm]
  (let [urakoiden-paivystykset (into []
                                     (map konv/alaviiva->rakenne)
                                     (yhteyshenkilot-q/hae-kaynissa-olevien-urakoiden-paivystykset
                                       db
                                       {:pvm (c/to-sql-time pvm)}))
        urakat (distinct (map #(dissoc % :paivystys) urakoiden-paivystykset))
        ;; sarakkeet-vektoriin ei palauta urakoita, joilla ei ole päivystyksiä.
        ;; Siksi lisätään kaikille urakoille erikseen päivystystiedot
        paivystykset (konv/sarakkeet-vektoriin
                                 urakoiden-paivystykset
                                 {:paivystys :paivystykset}
                                 :id)
        urakoiden-paivystykset (mapv
                                 (fn [urakka]
                                   (if-let [urakan-paivystykset
                                             (first
                                               (filter #(= (:urakka %) (:urakka urakka))
                                                       paivystykset))]
                                     (assoc urakka :paivystykset (:paivystykset urakan-paivystykset))
                                     urakka))
                                 urakat)]
    urakoiden-paivystykset))

(defn- paivystajien-tarkistustehtava [db nykyhetki]
  (let [urakoiden-paivystykset (hae-urakoiden-paivystykset db nykyhetki)]
    (urakat-ilman-paivytysta urakoiden-paivystykset nykyhetki)))

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