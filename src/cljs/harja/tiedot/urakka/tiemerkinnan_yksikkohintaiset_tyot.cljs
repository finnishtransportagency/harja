(ns harja.tiedot.urakka.tiemerkinnan-yksikkohintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))

(defn hae-yksikkohintaiset-tyot [urakka-id]
  (k/post! :hae-tiemerkinnan-yksikkohintaiset-tyot {:urakka-id urakka-id}))

(defn hae-paallystysurakan-kohteet [urakka-id]
  (k/post! :tiemerkintaurakalle-osoitetut-yllapitokohteet {:urakka-id urakka-id}))

(def tiemerkinnan-toteumat
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id nakymassa?)
                (hae-yksikkohintaiset-tyot valittu-urakka-id))))

(def paallystysurakan-kohteet
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id nakymassa?)
                (hae-paallystysurakan-kohteet valittu-urakka-id))))

(defn paallystysurakan-kohde-idlla [kohteet id]
  (when id
    (first (filter (fn [kohde] (= (:id kohde) id)) kohteet))))

(defn tallenna-tiemerkinnan-toteumat [urakka-id toteumat paallystysurakan-yllapitokohteet]
  (k/post! :tallenna-tiemerkinnan-yksikkohintaiset-tyot
           {:urakka-id urakka-id
            :toteumat toteumat
            :paallystysurakan-yllapitokohteet paallystysurakan-yllapitokohteet}))

