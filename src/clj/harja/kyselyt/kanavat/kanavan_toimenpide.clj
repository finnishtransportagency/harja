(ns harja.kyselyt.kanavat.kanavan-toimenpide
  "Kyselyt kanavatoimenpiteille"
  (:require [specql.core :refer [fetch update!]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [jeesql.core :refer [defqueries]]
            [specql.op :as op]))

(defn hae-kanavatoimenpiteet [db hakuehdot]
  (fetch db ::toimenpide/kanava-toimenpide toimenpide/kaikki-kentat hakuehdot))

(defqueries "harja/kyselyt/kanavat/kanavan_toimenpide.sql")

(defn hae-sopimuksen-toimenpiteet-aikavalilta [db hakuehdot]
  (let [idt (harja.kyselyt.kanavat.kanavan-toimenpide/hae-sopimuksen-kanavatoimenpiteet-aikavalilta db hakuehdot)]
    (if (not (empty? idt))
      (sort-by ::toimenpide/alkupvm
               (hae-kanavatoimenpiteet db
                                       {::toimenpide/id (op/in (into #{} (map :id idt)))}))
      [])))

(defn- vaadi-toimenpiteet-kuuluvat-urakkaan* [toimenpiteet toimenpide-idt urakka-id]
  (when (or
          (nil? urakka-id)
          (not (->> toimenpiteet
                    (map ::toimenpide/urakka-id)
                    (every? (partial = urakka-id)))))
    (throw (SecurityException. (str "Toimenpiteet " toimenpide-idt " eivät kuulu urakkaan " urakka-id)))))

(defn vaadi-toimenpiteet-kuuluvat-urakkaan [db toimenpide-idt urakka-id]
  (vaadi-toimenpiteet-kuuluvat-urakkaan*
    (fetch
      db
      ::toimenpide/kanava-toimenpide
      #{::toimenpide/urakka-id}
      {::toimenpide/id (op/in toimenpide-idt)})
    toimenpide-idt
    urakka-id))

(defn paivita-toimenpiteiden-tyyppi [db toimenpide-idt uusi-tyyppi]
  (update! db ::toimenpide/kanava-toimenpide
           {::toimenpide/tyyppi (name uusi-tyyppi)}
           {::toimenpide/id (op/in toimenpide-idt)}))