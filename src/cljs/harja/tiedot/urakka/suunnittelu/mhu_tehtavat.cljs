(ns harja.tiedot.urakka.suunnittelu.mhu-tehtavat
  (:require [tuck.core :refer [process-event] :as tuck]
            [clojure.string :as clj-str]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.osa :as osa]))

(defrecord MuutaTila [polku arvo])
(defrecord PaivitaTila [polku f])
(defrecord LaajennaSoluaKlikattu [laajenna-osa auki?])

(defn ylempi-taso?
  [ylempi-taso alempi-taso]
  (case ylempi-taso
    "ylataso" (not= "ylataso" alempi-taso)
    "valitaso" (= "alataso" alempi-taso)
    "alataso" false))

(defn klikatun-rivin-lapsenlapsi?
  [janan-id klikatun-rivin-id taulukon-rivit]
  (let [klikatun-rivin-lapset (get (group-by #(-> % meta :vanhempi)
                                             taulukon-rivit)
                                   klikatun-rivin-id)
        on-lapsen-lapsi? (some #(= janan-id (p/janan-id %))
                               klikatun-rivin-lapset)
        recursion-vastaus (cond
                            (nil? klikatun-rivin-lapset) false
                            on-lapsen-lapsi? true
                            :else (map #(klikatun-rivin-lapsenlapsi? janan-id (p/janan-id %) taulukon-rivit)
                                       klikatun-rivin-lapset))]
    (if (boolean? recursion-vastaus)
      recursion-vastaus
      (some true? recursion-vastaus))))

(extend-protocol tuck/Event
  MuutaTila
  (process-event [{:keys [polku arvo]} app]
    (assoc-in app polku arvo))

  PaivitaTila
  (process-event [{:keys [polku f]} app]
    (update-in app polku f))

  LaajennaSoluaKlikattu
  (process-event [{:keys [laajenna-osa auki?]} app]
    (update app :tehtavat-taulukko
               (fn [taulukon-rivit]
                 (map (fn [{:keys [janan-id] :as rivi}]
                        (let [{:keys [vanhempi]} (meta rivi)
                              klikatun-rivin-id (p/osan-janan-id laajenna-osa)
                              klikatun-rivin-lapsi? (= klikatun-rivin-id vanhempi)
                              klikatun-rivin-lapsenlapsi? (klikatun-rivin-lapsenlapsi? janan-id klikatun-rivin-id taulukon-rivit)]
                          ;; Jos joku rivi on kiinnitetty, halutaan sulkea myös kaikki lapset ja lasten lapset.
                          ;; Kumminkin lapsirivien Laajenna osan sisäinen tila jää väärään tilaan, ellei sitä säädä ulko käsin.
                          ;; Tässä otetaan ja muutetaan se oikeaksi.
                          (when (and (not auki?) klikatun-rivin-lapsenlapsi?)
                            (when-let [rivin-laajenna-osa (some #(when (instance? osa/Laajenna %)
                                                                   %)
                                                                (:solut rivi))]
                              (reset! (p/osan-tila rivin-laajenna-osa) false)))
                          (cond
                            ;; Jos riviä klikataan, piilotetaan lapset
                            (and auki? klikatun-rivin-lapsi?) (vary-meta (update rivi :luokat disj "piillotettu")
                                                                         assoc :piillotettu? false)
                            ;; Jos rivillä on lapsen lapsia, piillotetaan myös ne
                            (and (not auki?) klikatun-rivin-lapsenlapsi?) (vary-meta (update rivi :luokat conj "piillotettu")
                                                                                     assoc :piillotettu? true)
                            :else rivi)))
                      taulukon-rivit)))))