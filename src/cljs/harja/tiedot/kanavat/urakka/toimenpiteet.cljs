(ns harja.tiedot.kanavat.urakka.toimenpiteet
  (:require [reagent.core :refer [atom]]
            [harja.id :refer [id-olemassa?]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavatoimenpide]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn muodosta-hakuargumentit [valinnat tyyppi]
  {::kanavatoimenpide/urakka-id (:id (:urakka valinnat))
   ::kanavatoimenpide/sopimus-id (:sopimus-id valinnat)
   ::toimenpidekoodi/id (get-in valinnat [:toimenpide :id])
   ::kanavatoimenpide/kanava-toimenpidetyyppi tyyppi
   :alkupvm (first (:aikavali valinnat))
   :loppupvm (second (:aikavali valinnat))})

(defn valittu-tehtava-muu? [tehtava-id tehtavat]
  (and
    tehtavat
    (some #(= % tehtava-id)
          (map :id
               (filter #(and
                          (:nimi %)
                          (not= -1 (.indexOf (str/upper-case (:nimi %)) "MUU"))) tehtavat)))))