(ns harja.tiedot.urakka.urakan-tyotunnit
  "Urakan toimenpiteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn hae-urakan-tyotunnit [urakka-id]
  (k/post! :hae-urakan-tyotunnit {::urakan-tyotunnit/urakka-id urakka-id}))

(defn tallenna-urakan-tyotunnit [urakka-id tyotunnit-vuosittain]
  (let [urakan-tyotunnit-vuosikolmanneksella (fn [vuosi kolmannes tunnit]
                                               {::urakan-tyotunnit/urakka-id urakka-id
                                                ::urakan-tyotunnit/vuosi vuosi
                                                ::urakan-tyotunnit/vuosikolmannes kolmannes
                                                ::urakan-tyotunnit/tyotunnit tunnit})

        urakan-tyotunnit (mapcat (fn [{:keys [vuosi
                                              ensimmainen-vuosikolmannes
                                              toinen-vuosikolmannes
                                              kolmas-vuosikolmannes]}]
                                   (remove
                                     nil?
                                     [(when ensimmainen-vuosikolmannes
                                        (urakan-tyotunnit-vuosikolmanneksella vuosi 1 ensimmainen-vuosikolmannes))
                                      (when toinen-vuosikolmannes
                                        (urakan-tyotunnit-vuosikolmanneksella vuosi 2 toinen-vuosikolmannes))
                                      (when kolmas-vuosikolmannes
                                        (urakan-tyotunnit-vuosikolmanneksella vuosi 2 toinen-vuosikolmannes))]))
                                 tyotunnit-vuosittain)]
    (k/post! :tallenna-urakan-tyotunnit {::urakan-tyotunnit/urakka-id urakka-id
                                         ::urakan-tyotunnit/urakan-tyotunnit-vuosikolmanneksittain urakan-tyotunnit})))


