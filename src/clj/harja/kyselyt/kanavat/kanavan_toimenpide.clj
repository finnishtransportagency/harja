(ns harja.kyselyt.kanavat.kanavan-toimenpide
  (:require [specql.core :refer [fetch]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [jeesql.core :refer [defqueries]]
            [specql.op :as op]))

(defn hae-kanavatoimenpiteet [db hakuehdot]
  (fetch db ::toimenpide/kanava-toimenpide toimenpide/kaikki-kentat hakuehdot))

(defqueries "harja/kyselyt/kanavat/kanavan_toimenpide.sql")

(defn hae-sopimuksen-toimenpiteet-aikavalilta [db sopimus alkupvm loppupvm toimenpidekoodi tyyppi]
  (let [idt (harja.kyselyt.kanavat.kanavan-toimenpide/hae-sopimuksen-kanavatoimenpiteet-aikavalilta
              db
              {:sopimus sopimus
               :alkupvm alkupvm
               :loppupvm loppupvm
               :toimenpidekoodi toimenpidekoodi
               :tyyppi tyyppi})]
    (when (not (empty? idt))
      (hae-kanavatoimenpiteet db {::toimenpide/id (op/in (into #{} (map :id idt)))}))))