(ns harja.tiedot.urakka.tiemerkinnan-yksikkohintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.tiemerkinta-toteumat :as d])
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

(defn- tallenna-tiemerkinnan-toteumat [urakka-id toteumat]
  (k/post! :tallenna-tiemerkinnan-yksikkohintaiset-tyot
           {:urakka-id urakka-id
            :toteumat toteumat}))



(defn toteuman-hinnan-kohde-muuttunut? [{:keys [hinta-kohteelle] :as toteuma} kohde]
  (let [hinnan-kohde-eri-kuin-nykyinen-osoite? (and hinta-kohteelle kohde
                                                    (not= (d/maarittele-hinnan-kohde kohde)
                                                          hinta-kohteelle))]
    (boolean hinnan-kohde-eri-kuin-nykyinen-osoite?)))

(defn tallenna-toteumat-grid [{:keys [toteumat urakka-id tiemerkinnan-toteumat-atom
                                      paallystysurakan-kohteet epaonnistui-fn]}]
  (go (let [kasitellyt-toteumat (->> toteumat
                                     (map ;; Lisää :hinta-kohteelle jos linkitetty ylläpitokohteeseen
                                       #(if-let [kohde (paallystysurakan-kohde-idlla paallystysurakan-kohteet
                                                                                     (:yllapitokohde-id %))]
                                          (assoc % :hinta-kohteelle (d/maarittele-hinnan-kohde kohde))
                                          %))
                                     (map spec-apurit/poista-nil-avaimet))
            vastaus (<! (tallenna-tiemerkinnan-toteumat urakka-id kasitellyt-toteumat))]
        (if (k/virhe? vastaus)
          (epaonnistui-fn)
          (reset! tiemerkinnan-toteumat-atom vastaus)))))