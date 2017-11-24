(ns harja.kyselyt.kanavat.kanavan-toimenpide
  "Kyselyt kanavatoimenpiteille"
  (:require [specql.core :refer [fetch insert! update!]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [jeesql.core :refer [defqueries]]
            [specql.op :as op]
            [harja.pvm :as pvm]
            [harja.id :as id]))

(defn hae-kanavatoimenpiteet [db hakuehdot]
  (fetch db ::toimenpide/kanava-toimenpide toimenpide/perustiedot-viittauksineen hakuehdot))

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

(defn tallenna-toimenpide [db kayttaja-id kanavatoimenpide]
  (if (id/id-olemassa? (::toimenpide/id kanavatoimenpide))
    (let [kanavatoimenpide (assoc kanavatoimenpide
                             ::muokkaustiedot/muokattu (pvm/nyt)
                             ::muokkaustiedot/muokkaaja-id kayttaja-id)]
      (update! db ::toimenpide/kanava-toimenpide kanavatoimenpide {::toimenpide/id (::toimenpide/id kanavatoimenpide)}))
    (let [kanavatoimenpide (assoc kanavatoimenpide
                             ::muokkaustiedot/luotu (pvm/nyt)
                             ::muokkaustiedot/luoja-id kayttaja-id)]
      (insert! db ::toimenpide/kanava-toimenpide kanavatoimenpide))))

(defn hae-toimenpiteiden-tehtavan-hinnoittelu [db toimenpide-idt]
  (fetch db
         ::toimenpide/kanava-toimenpide
         #{::toimenpide/id
           [::toimenpide/toimenpidekoodi #{::toimenpidekoodi/hinnoittelu}]}
         {::toimenpide/id (op/in toimenpide-idt)}))

(defn paivita-toimenpiteiden-tehtava [db paivitettavat-tehtava-idt tehtava-id]
  (update! db ::toimenpide/kanava-toimenpide
           {::toimenpide/toimenpidekoodi-id tehtava-id}
           {::toimenpide/id (op/in paivitettavat-tehtava-idt)}))