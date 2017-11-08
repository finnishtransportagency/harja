(ns harja.kyselyt.kanavat.kanavan-toimenpide
  (:require [specql.core :refer [fetch]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [jeesql.core :refer [defqueries]]
            [specql.op :as op]))

(defn hae-kanavatoimenpiteet [db hakuehdot]
  (fetch db ::toimenpide/kanava-toimenpide toimenpide/kaikki-kentat hakuehdot))

(defqueries "harja/kyselyt/kanavat/kanavan_toimenpide.sql")

(defn hae-sopimuksen-toimenpiteet-aikavalilta [db hakuehdot]
  (let [idt (harja.kyselyt.kanavat.kanavan-toimenpide/hae-sopimuksen-kanavatoimenpiteet-aikavalilta db hakuehdot)]
    (if (not (empty? idt))
      (reverse (sort-by ::toimenpide/alkupvm
                        (hae-kanavatoimenpiteet db
                                                {::toimenpide/id (op/in (into #{} (map :id idt)))})))
      [])))