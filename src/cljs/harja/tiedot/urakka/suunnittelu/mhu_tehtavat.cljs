(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [clojure.string :as clj-str]
            [harja.ui.taulukko.protokollat :as p]))

(defrecord MuutaTila [polku arvo])
(defrecord PaivitaTila [polku f])
(defrecord LaajennaSoluaKlikattu [laajenna-osa auki?])

(defn ylempi-taso?
  [ylempi-taso alempi-taso]
  (case ylempi-taso
    "ylataso" (not= "ylataso" alempi-taso)
    "valitaso" (= "alataso" alempi-taso)
    "alataso" false))

(extend-protocol tuck/Event
  MuutaTila
  (process-event [{:keys [polku arvo]} app]
    (assoc-in app polku arvo))

  PaivitaTila
  (process-event [{:keys [polku f]} app]
    (update-in app polku f))

  LaajennaSoluaKlikattu
  (process-event [{:keys [this auki?]} app]
    (update-in app [:suunnittelu :tehtavat :tehtavat-taulukko]
               (fn [tila]
                 (map (fn [rivi]
                        (let [{:keys [vanhempi]} (meta rivi)
                              rivi-alemmalla-tasolla? (= (p/osan-janan-id this) vanhempi)]
                          (cond
                            (and (= auki? true) rivi-alemmalla-tasolla?) (vary-meta (update rivi :class conj "piillota")
                                                                                    assoc :piillotettu? false)
                            (and (= auki? false) rivi-alemmalla-tasolla?) (vary-meta (update rivi :class disj "piillota")
                                                                                     assoc :piillotettu? true)
                            :else rivi)))
                      tila)))))