(ns harja.tiedot.urakka.urakan-tyotunnit
  "Urakan toimenpiteet"
  (:require [reagent.core :refer [atom] :as r]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.loki :refer [log]]
            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn urakan-tyotunnit-vuosikolmanneksella [urakka-id vuosi kolmannes tunnit]
  {::urakan-tyotunnit/urakka-id urakka-id
   ::urakan-tyotunnit/vuosi vuosi
   ::urakan-tyotunnit/vuosikolmannes kolmannes
   ::urakan-tyotunnit/tyotunnit tunnit})

(defn vuosikolmanneksen-tunnit [vuosi kolmannes tyotunnit]
  (::urakan-tyotunnit/tyotunnit
    (first (filter #(and (= vuosi (::urakan-tyotunnit/vuosi %))
                         (= kolmannes (::urakan-tyotunnit/vuosikolmannes %)))
                   tyotunnit))))

(defn tyotunnit-tallennettavana [urakka-id tyotunnit-vuosittain]
  (mapcat (fn [{:keys [vuosi
                       ensimmainen-vuosikolmannes
                       toinen-vuosikolmannes
                       kolmas-vuosikolmannes]}]
            (remove
              nil?
              [(when ensimmainen-vuosikolmannes
                 (urakan-tyotunnit-vuosikolmanneksella urakka-id vuosi 1 ensimmainen-vuosikolmannes))
               (when toinen-vuosikolmannes
                 (urakan-tyotunnit-vuosikolmanneksella urakka-id vuosi 2 toinen-vuosikolmannes))
               (when kolmas-vuosikolmannes
                 (urakan-tyotunnit-vuosikolmanneksella urakka-id vuosi 3 toinen-vuosikolmannes))]))
          tyotunnit-vuosittain))

(defn tyotunnit-naytettavana [vuodet tyotunnit]
  (map #(assoc %
          :ensimmainen-vuosikolmannes (vuosikolmanneksen-tunnit (:vuosi %) 1 tyotunnit)
          :toinen-vuosikolmannes (vuosikolmanneksen-tunnit (:vuosi %) 2 tyotunnit)
          :kolmas-vuosikolmannes (vuosikolmanneksen-tunnit (:vuosi %) 3 tyotunnit))
       vuodet))

(defn hae-urakan-tyotunnit [urakka-id]
  (k/post! :hae-urakan-tyotunnit {::urakan-tyotunnit/urakka-id urakka-id}))

(defn tallenna-urakan-tyotunnit [urakka-id tyotunnit-vuosittain]
  (let [urakan-tyotunnit (tyotunnit-tallennettavana urakka-id tyotunnit-vuosittain)]
    (k/post! :tallenna-urakan-tyotunnit {::urakan-tyotunnit/urakka-id urakka-id
                                         ::urakan-tyotunnit/urakan-tyotunnit-vuosikolmanneksittain urakan-tyotunnit})))


