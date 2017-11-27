(ns harja.kyselyt.kanavat.kanavan-toimenpide
  "Kyselyt kanavatoimenpiteille"
  (:require [specql.core :refer [fetch insert! update!]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.tyo :as tyo]
            [harja.domain.muokkaustiedot :as m]
            [harja.id :refer [id-olemassa?]]
            [harja.pvm :as pvm]
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [specql.op :as op]))

(defn hae-kanavatoimenpiteet [db hakuehdot]
  (let [toimenpiteet (fetch db ::toimenpide/kanava-toimenpide toimenpide/perustiedot-viittauksineen hakuehdot)
        tp-hinnat #(fetch db ::hinta/toimenpiteen-hinta hinta/perustiedot-viittauksineen {::hinta/toimenpide-id %
                                                                                          ::m/poistettu? false})
        tp-tyot #(fetch db ::tyo/toimenpiteen-tyo tyo/perustiedot {::tyo/toimenpide-id %
                                                                   ::m/poistettu? false})]
    (for [tp toimenpiteet
          :let [tp-id (::toimenpide/id tp)]]
      (merge tp {::toimenpide/hinnat (tp-hinnat tp-id)
                 ::toimenpide/tyot (tp-tyot tp-id)}))))

(defqueries "harja/kyselyt/kanavat/kanavan_toimenpide.sql")

(defn hae-sopimuksen-toimenpiteet-aikavalilta [db hakuehdot]
  (if-let [idt (seq (hae-sopimuksen-kanavatoimenpiteet-aikavalilta db hakuehdot))]
    (sort-by ::toimenpide/alkupvm
             (hae-kanavatoimenpiteet
              db
              (op/and
               (op/or {::m/poistettu? op/null?} {::m/poistettu? false})
               {::toimenpide/id (op/in (into #{} (map :id idt)))})))
    []))

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

(defn tallenna-toimenpiteen-omat-hinnat! [{:keys [db user hinnat toimenpide-id]}]
  (println "tallenna-omat-hinnat: saatiin" (pr-str hinnat))
  (doseq [hinta (map #(poista-frontin-keksima-id % ::hinta/id) hinnat)]

    (specql/upsert! db
                    ::hinta/toimenpiteen-hinta
                    (kasittele-muokkaustiedot user hinta ::hinta/id)
                    {::m/poistettu? (op/not= true)})))

(defn tallenna-toimenpiteen-tyot! [{:keys [db user tyot toimenpide-id]}]
  (doseq [tyo (map #(poista-frontin-keksima-id % ::tyo/id) tyot)]
    (println "upsertoidaan" (pr-str (kasittele-muokkaustiedot user tyo ::tyo/id)))
    (specql/upsert! db
                    ::tyo/toimenpiteen-tyo
                    (kasittele-muokkaustiedot user tyo ::tyo/id)
                    {::m/poistettu? (op/not= true)})))


(defn tallenna-toimenpide [db kayttaja-id kanavatoimenpide]
  (if (id-olemassa? (::toimenpide/id kanavatoimenpide))
    (let [kanavatoimenpide (assoc kanavatoimenpide
                             ::m/muokattu (pvm/nyt)
                             ::m/muokkaaja-id kayttaja-id)]
      (update! db ::toimenpide/kanava-toimenpide kanavatoimenpide {::toimenpide/id (::toimenpide/id kanavatoimenpide)}))
    (let [kanavatoimenpide (assoc kanavatoimenpide
                             ::m/luotu (pvm/nyt)
                             ::m/luoja-id kayttaja-id)]
      (insert! db ::toimenpide/kanava-toimenpide kanavatoimenpide))))
