(ns harja.kyselyt.kanavat.kanavan-toimenpide
  "Kyselyt kanavatoimenpiteille"
  (:require [specql.core :refer [fetch]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.tyo :as tyo]
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


(defn poista-frontin-keksima-id [m id-avain]
  ;; id-olemassa? katsoo onko id 0 tai negatiivinen, josta päätellään
  ;; että se on frontin generoima sijais-id
  (if-not (id-olemassa? (id-avain m))
    (dissoc m id-avain)
    m))

(defn kasittele-muokkaustiedot [user m muokkaus-id-avain]
  {:pre [(map? m) (map? user) (keyword? muokkaus-id-avain)]}
  (let [m (if (get m muokkaus-id-avain)
            ;; luomistiedoissa ei frontin kontrollia: blokataan muokkaus tai muodostetaan luomistiedot
            (dissoc m ::m/luoja-id ::m/luotu)
            (assoc m ::m/luoja-id (:id user) ::m/luotu (pvm/nyt)))
        m (merge m
                 (if (::m/poistettu? m)
                   {::m/poistettu? true
                    ::m/poistaja-id (:id user)}
                   {::m/muokattu (pvm/nyt)
                    ::m/muokkaaja-id (:id user)}))]
    m))

(defn tallenna-toimenpiteen-omat-hinnat! [{:keys [db user hinnat]}]

  (doseq [hinta (map #(poista-frontin-keksima-id % ::hinta/id) hinnat)]
    (specql/upsert! db
                    ::hinta/toimenpiteen-hinta
                    (kasittele-muokkaustiedot user hinta ::hinta/id)
                    {::m/poistettu? (op/not= true)})))

(defn tallenna-toimenpiteen-tyot! [{:keys [db user tyot]}]
  (doseq [tyo (map #(poista-frontin-keksima-id % ::tyo/id) tyot)]
    (specql/upsert! db
                    ::tyo/tyo
                    (kasittele-muokkaustiedot user tyo ::tyo/id)
                    {::m/poistettu? (op/not= true)})))

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
