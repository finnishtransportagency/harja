(ns harja.kyselyt.kanavat.kanavan-toimenpide
  "Kyselyt kanavatoimenpiteille"
  (:require [specql.core :refer [fetch insert! update!]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [jeesql.core :refer [defqueries]]
            [specql.op :as op]
            [harja.pvm :as pvm]))

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

(defn tallenna-toimenpide [db kayttaja-id kanavatoimenpide]
  (if (::toimenpide/id kanavatoimenpide)
    (let [kanavatoimenpide (assoc kanavatoimenpide
                             ::muokkaustiedot/muokattu (pvm/nyt)
                             ::muokkaustiedot/muokkaaja-id kayttaja-id)]
      (update! db ::toimenpide/kanava-toimenpide kanavatoimenpide {::toimenpide/id (::toimenpide/id kanavatoimenpide)}))
    (let [kanavatoimenpide (assoc kanavatoimenpide
                             ::muokkaustiedot/luotu (pvm/nyt)
                             ::muokkaustiedot/luoja-id kayttaja-id)]
      (insert! db ::toimenpide/kanava-toimenpide kanavatoimenpide))))