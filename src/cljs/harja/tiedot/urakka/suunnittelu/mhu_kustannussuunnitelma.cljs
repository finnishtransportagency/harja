(ns harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
  (:require [tuck.core :as tuck]
            [harja.loki :refer [log]]))


(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(def talvikausi [10 11 12 1 2 3 4])
(def kesakausi (into [] (range 5 10)))
(def hoitokausi (concat talvikausi kesakausi))

(def kaudet {:kesa kesakausi
             :talvi talvikausi
             :kaikki hoitokausi})

(defn tila
  [a & [kausi]]
  (assoc {} a
            (into {:auki? false}
                  (map #(assoc {} % 0) (get kaudet kausi talvikausi)))))
(defn tila-vuodet
  [a & [kausi]]
  (log "a " a " kausi " kausi)
  (assoc {} a (into {:lahetyspaiva :kuukauden-15
                     :maksukausi   (or kausi :talvi)}
                    (map #(tila % kausi) (range 1 6)))))

(defn tilat-proto
  [& [kausi]]
  (into {}
        (map #(tila-vuodet % kausi)
             toimenpiteet)))

(defn f
  [data & [not?]]
  (into {}
        (map (fn [a]
               {(first a)
                (if (map? (second a))
                  (into {}
                        (filter (fn [b]
                                  (if not?
                                    (not (keyword? (first b)))
                                    (keyword? (first b))))
                                (second a)))
                  (second a))})
             data)))


(defrecord AsetaKustannussuunnitelmassa [polku arvo])
(defrecord AsetaMaksukausi [polku arvo])

(extend-protocol tuck/Event
  AsetaMaksukausi
  (process-event
    [{:keys [polku arvo]} app]
    (let [uudet (f (get (tilat-proto arvo) (second polku)) true)
          vanhat (f (get-in app [:suunnitellut-hankinnat (second polku)]))
          mergattu (merge vanhat uudet)]
      (log polku arvo uudet vanhat)
      (assoc-in (assoc-in app (take 2 polku) mergattu) polku arvo)))
  AsetaKustannussuunnitelmassa
  (process-event
    [{:keys [polku arvo]} app]
    (assoc-in app polku arvo)))
