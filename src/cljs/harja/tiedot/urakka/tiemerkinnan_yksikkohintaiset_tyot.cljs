(ns harja.tiedot.urakka.tiemerkinnan-yksikkohintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.domain.tiemerkinta-toteumat :as d])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))

(defn hae-yksikkohintaiset-tyot [urakka-id vuosi]
  (k/post! :hae-tiemerkinnan-yksikkohintaiset-tyot {:urakka-id urakka-id
                                                    :vuosi vuosi}))

(defn hae-paallystysurakan-kohteet [urakka-id vuosi]
  (k/post! :tiemerkintaurakalle-osoitetut-yllapitokohteet {:urakka-id urakka-id
                                                           :vuosi vuosi}))

(def tiemerkinnan-toteumat
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @u/valittu-urakan-vuosi
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id vuosi nakymassa? )
                (hae-yksikkohintaiset-tyot valittu-urakka-id vuosi))))

(def paallystysurakan-kohteet
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               vuosi @u/valittu-urakan-vuosi
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id vuosi nakymassa?)
                (hae-paallystysurakan-kohteet valittu-urakka-id vuosi))))

(defn paallystysurakan-kohde-idlla [kohteet id]
  (when id
    (first (filter (fn [kohde] (= (:id kohde) id)) kohteet))))

(defn- tallenna-tiemerkinnan-toteumat [urakka-id vuosi toteumat]
  (k/post! :tallenna-tiemerkinnan-yksikkohintaiset-tyot
           {:urakka-id urakka-id
            :vuosi vuosi
            :toteumat toteumat}))



(defn toteuman-hinnan-kohde-muuttunut? [{:keys [hinta-kohteelle] :as toteuma} kohde]
  (let [hinnan-kohde-eri-kuin-nykyinen-osoite? (and hinta-kohteelle kohde
                                                    (not= (d/maarittele-hinnan-kohde kohde)
                                                          hinta-kohteelle))]
    (boolean hinnan-kohde-eri-kuin-nykyinen-osoite?)))

(defn tallenna-toteumat-grid [{:keys [toteumat vuosi urakka-id tiemerkinnan-toteumat-atom
                                      paallystysurakan-kohteet epaonnistui-fn]}]
  (go (let [kasitellyt-toteumat (->> toteumat
                                     (map ;; Lisää :hinta-kohteelle jos linkitetty ylläpitokohteeseen
                                       #(if-let [kohde (paallystysurakan-kohde-idlla paallystysurakan-kohteet
                                                                                     (:yllapitokohde-id %))]
                                          (assoc % :hinta-kohteelle (d/maarittele-hinnan-kohde kohde))
                                          %))
                                     (map spec-apurit/poista-nil-avaimet))
            vastaus (<! (tallenna-tiemerkinnan-toteumat urakka-id vuosi kasitellyt-toteumat))]
        (if (k/virhe? vastaus)
          (epaonnistui-fn)
          (reset! tiemerkinnan-toteumat-atom vastaus)))))
