(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:require [reagent.core :refer [atom cursor]]
            [clojure.core.async :refer [chan]]
            [harja.tiedot.navigaatio :as nav]))

(def suunnittelu-default-arvot {:tehtavat {:valinnat {:samat-kaikille false
                                                      :toimenpide nil
                                                      :valitaso nil
                                                      :noudetaan 0}}
                                :kustannussuunnitelma {:hankintakustannukset {:valinnat {:toimenpide :talvihoito
                                                                                         :maksetaan :molemmat
                                                                                         :kopioidaan-tuleville-vuosille? true
                                                                                         :laskutukseen-perustuen-valinta #{}}}
                                                       :suodattimet {:hankinnat {:toimenpide :talvihoito
                                                                                 :maksetaan :molemmat
                                                                                 :kopioidaan-tuleville-vuosille? true
                                                                                 :laskutukseen-perustuen-valinta #{}}
                                                                     :kopioidaan-tuleville-vuosille? true}}})

(defonce urakan-vaihto-triggerit (cljs.core/atom []))

(defn lisaa-urakan-vaihto-trigger!
  "Tämä funktio avulla voi lisätä funktion listaan, jonka kaikki funktiot ajetaan kun urakka vaihdetaan.
   Tässä voi siis tehdä jonkin tapaista siivousta."
  [f!]
  (swap! urakan-vaihto-triggerit conj f!))

(defonce tila (atom {:yleiset {:urakka {}}
                     :suunnittelu suunnittelu-default-arvot}))

(defonce yleiset (cursor tila [:yleiset]))

(defonce suunnittelu-tehtavat (cursor tila [:suunnittelu :tehtavat]))

(defonce suunnittelu-kustannussuunnitelma (cursor tila [:suunnittelu :kustannussuunnitelma]))

(add-watch nav/valittu-urakka :urakan-id-watch
           (fn [_ _ _ uusi-urakka]
             (doseq [f! @urakan-vaihto-triggerit]
               (f!))
             (when-not (= 0 (count @urakan-vaihto-triggerit))
               (reset! urakan-vaihto-triggerit []))
             (swap! tila (fn [tila]
                           (-> tila
                               (assoc-in [:yleiset :urakka] (dissoc uusi-urakka :alue))
                               (assoc :suunnittelu suunnittelu-default-arvot))))
             ;dereffataan kursorit, koska ne on laiskoja
             @suunnittelu-kustannussuunnitelma))