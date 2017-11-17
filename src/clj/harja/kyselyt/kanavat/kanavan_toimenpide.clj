(ns harja.kyselyt.kanavat.kanavan-toimenpide
  "Kyselyt kanavatoimenpiteille"
  (:require [specql.core :refer [fetch]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.hinta :as tyo]
            [harja.domain.muokkaustiedot :as m]
            [harja.id :refer [id-olemassa?]]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [specql.rel :as rel]
            [specql.op :as op]))

(defn hae-kanavatoimenpiteet [db hakuehdot]
  (fetch db ::toimenpide/kanava-toimenpide toimenpide/kaikki-kentat hakuehdot))

(defn hae-hinnat [db hakuehdot]
  (fetch db ::hinta/toimenpiteen-hinta (specql/columns ::hinta/toimenpiteen-hinta) hakuehdot))

(defqueries "harja/kyselyt/kanavat/kanavan_toimenpide.sql")

(defn hae-sopimuksen-toimenpiteet-aikavalilta [db hakuehdot]
  (let [idt (hae-sopimuksen-kanavatoimenpiteet-aikavalilta db hakuehdot)]
    (if (not (empty? idt))
      (sort-by ::toimenpide/alkupvm
               (hae-kanavatoimenpiteet db
                                       {::toimenpide/id (op/in (into #{} (map :id idt)))}))
      [])))


(defn tallenna-toimenpiteen-omat-hinnat! [{:keys [db user hinnoittelu-id hinnat]}]
  (doseq [hinta hinnat]
    (if (id-olemassa? (::hinta/id hinta))
      (specql/update! db
                      ::hinta/hinta
                      (merge
                        hinta
                        (if (::m/poistettu? hinta)
                          {::m/poistettu? true
                           ::m/poistaja-id (:id user)}
                          {::m/muokattu (pvm/nyt)
                           ::m/muokkaaja-id (:id user)}))
                      {::hinta/id (::hinta/id hinta)})

      (specql/insert! db
                      ::hinta/hinta
                      (merge
                        (dissoc hinta ::hinta/id)
                        {::m/luotu (pvm/nyt)
                         ::m/luoja-id (:id user)})))))

(defn tallenna-toimenpiteen-tyot! [{:keys [db user hinnoittelu-id tyot]}]
  (doseq [tyo tyot]
    (if (id-olemassa? (::tyo/id tyo))
      (specql/update! db
                      ::tyo/tyo
                      (merge
                        {::tyo/toimenpidekoodi-id (::tyo/toimenpidekoodi-id tyo)
                         ::tyo/maara (::tyo/maara tyo)}
                        (if (::m/poistettu? tyo)
                          {::m/poistettu? true
                           ::m/poistaja-id (:id user)}
                          {::m/muokattu (pvm/nyt)
                           ::m/muokkaaja-id (:id user)}))
                      {::tyo/id (::tyo/id tyo)})
      (specql/insert! db
                      ::tyo/tyo
                      (merge
                        (dissoc tyo ::tyo/id)
                        {::m/luotu (pvm/nyt)
                         ::m/luoja-id (:id user)})))))

(defn hae-hinnoittelutiedot-toimenpiteille [db toimenpide-id-seq]
  (let [tp-hinnat (specql/fetch db ::hinta/toimenpide<->hinta
                                #{::hinta/toimenpide ::hinta/hinta}
                                {::hinta/toimenpide (op/in toimenpide-id-seq)})
        hae-toimenpide-idlla #(first (hae-kanavatoimenpiteet db {::toimenpide/id %} ))
        hae-hinta-idlla #(first (hae-hinnat db {::hinta/id %}))]
    (for [tp-hinta tp-hinnat
          :let [tp (hae-toimenpide-idlla (::hinta/toimenpide tp-hinta))
                hinta (hae-hinta-idlla (::hinta/hinta tp-hinta))]]
      (merge tp hinta))))

(defn hae-toimenpiteen-oma-hinnoittelu [db toimenpide-id]
  {:pre [(integer? toimenpide-id)]}
  (first (hae-hinnoittelutiedot-toimenpiteille db #{toimenpide-id})))
