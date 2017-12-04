(ns harja.tiedot.kanavat.urakka.toimenpiteet
  "Kanavatoimenpiteiden yhteiset asiat"
  (:require [reagent.core :refer [atom]]
            [harja.id :refer [id-olemassa?]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.kanavat.kanavan-toimenpide :as kanavatoimenpide]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn muodosta-kohteiden-hakuargumentit [valinnat tyyppi]
  {::kanavatoimenpide/urakka-id (:id (:urakka valinnat))
   ::kanavatoimenpide/sopimus-id (:sopimus-id valinnat)
   ::toimenpidekoodi/id (get-in valinnat [:toimenpide :id])
   ::kanavatoimenpide/kanava-toimenpidetyyppi tyyppi
   :alkupvm (first (:aikavali valinnat))
   :loppupvm (second (:aikavali valinnat))
   ::kanavatoimenpide/kohde-id (:kanava-kohde-id valinnat)})

(defn esitaytetty-toimenpidelomake [kayttaja urakka]
  {::kanavatoimenpide/sopimus-id (:paasopimus urakka)
   ::kanavatoimenpide/kuittaaja {::kayttaja/id (:id kayttaja)
                                   ::kayttaja/etunimi (:etunimi kayttaja)
                                   ::kayttaja/sukunimi (:sukunimi kayttaja)}})

(defn valittu-tehtava-muu? [tehtava-id tehtavat]
  (and
    tehtavat
    (some #(= % tehtava-id)
          (map :id
               (filter #(and
                          (:nimi %)
                          (not= -1 (.indexOf (str/upper-case (:nimi %)) "MUU"))) tehtavat)))))