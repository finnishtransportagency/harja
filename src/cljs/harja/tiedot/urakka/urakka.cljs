(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :refer [atom cursor]]
            [harja.tiedot.navigaatio :as nav]))

(defn hankinnat-testidata [maara]
  (into []
        (drop 9
              (drop-last 3
                         (mapcat (fn [vuosi]
                                   (map #(identity
                                           {:pvm (harja.pvm/luo-pvm vuosi % 15)
                                            :maara maara})
                                        (range 0 12)))
                                 (range (harja.pvm/vuosi (harja.pvm/nyt)) (+ (harja.pvm/vuosi (harja.pvm/nyt)) 6)))))))

(defonce tila (atom {:yleiset {:urakka {}}
                     :suunnittelu {:tehtavat {:valinnat {:toimenpide nil
                                                         :valitaso nil}}
                                   :kustannussuunnitelma {:hankintakustannukset {:valinnat {:toimenpide :talvihoito
                                                                                            :maksetaan :talvikausi
                                                                                            :kopioidaan-tuleville-vuosille? true
                                                                                            :laskutukseen-perustuen-valinta #{}}}
                                                          :hallinnolliset-toimenpiteet {:valinnat {:maksetaan :molemmat}}}}}))

(defonce yleiset (cursor tila [:yleiset]))

(defonce suunnittelu-tehtavat (cursor tila [:suunnittelu :tehtavat]))

(defonce suunnittelu-kustannussuunnitelma (cursor tila [:suunnittelu :kustannussuunnitelma]))

(add-watch nav/valittu-urakka :urakan-id-watch
           (fn [_ _ _ uusi-urakka]
             (swap! tila assoc-in [:yleiset :urakka] (dissoc uusi-urakka :alue))))