(ns harja.kyselyt.kanavat.kanavan-toimenpide
  "Kyselyt kanavatoimenpiteille"
  (:require [specql.core :refer [fetch insert! update!]]
            [harja.domain.kanavat.kanavan-toimenpide :as toimenpide]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.toimenpidekoodi :as toimenpidekoodi]
            [harja.domain.kanavat.hinta :as hinta]
            [harja.domain.kanavat.tyo :as tyo]
            [harja.domain.kanavat.kommentti :as kommentti]
            [harja.id :refer [id-olemassa?]]
            [harja.pvm :as pvm]
            [harja.geo :as geo]
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [specql.op :as op]
            [clojure.set :as set]))

(defn hae-toimenpiteen-hinnat [db toimenpide-id]
  (fetch db ::hinta/toimenpiteen-hinta hinta/perustiedot-viittauksineen {::hinta/toimenpide-id toimenpide-id
                                                                         ::muokkaustiedot/poistettu? false}))

(defn hae-toimenpiteen-tyot [db toimenpide-id]
  (fetch db ::tyo/toimenpiteen-tyo tyo/perustiedot {::tyo/toimenpide-id toimenpide-id
                                                    ::muokkaustiedot/poistettu? false}))

(defn hae-kanavatoimenpiteet-specql [db hakuehdot]
  (let [toimenpiteet (fetch db ::toimenpide/kanava-toimenpide toimenpide/perustiedot-viittauksineen hakuehdot)
        kommentit #(fetch db ::kommentti/toimenpiteen-kommentti
                          (set/union kommentti/perustiedot kommentti/kayttajan-tiedot)
                          {::kommentti/toimenpide-id %})]
    (for [tp toimenpiteet
          :let [tp-id (::toimenpide/id tp)]]
      (merge tp {::toimenpide/hinnat (hae-toimenpiteen-hinnat db tp-id)
                 ::toimenpide/tyot (hae-toimenpiteen-tyot db tp-id)
                 ::toimenpide/kommentit (kommentit tp-id)}))))

(defqueries "harja/kyselyt/kanavat/kanavan_toimenpide.sql")

(defn hae-kanavatomenpiteet-jeesql [db hakuehdot]
  (if-let [idt (seq (hae-kanavatoimenpiteet-aikavalilta db hakuehdot))]
    (sort-by ::toimenpide/alkupvm
             (hae-kanavatoimenpiteet-specql
              db
              (op/and
               (op/or {::muokkaustiedot/poistettu? op/null?} {::muokkaustiedot/poistettu? false})
               {::toimenpide/id (op/in (into #{} (map :id idt)))})))
    []))

(defn- vaadi-toimenpiteet-kuuluvat-urakkaan* [toimenpiteet-kannassa toimenpide-idt urakka-id]
  (when (or
          (nil? urakka-id)
          (not (->> toimenpiteet-kannassa
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
            (dissoc m ::muokkaustiedot/luoja-id ::muokkaustiedot/luotu)
            (assoc m ::muokkaustiedot/luoja-id (:id user) ::muokkaustiedot/luotu (pvm/nyt)))
        m (merge m
                 (if (::muokkaustiedot/poistettu? m)
                   {::muokkaustiedot/poistettu? true
                    ::muokkaustiedot/poistaja-id (:id user)}
                   {::muokkaustiedot/muokattu (pvm/nyt)
                    ::muokkaustiedot/muokkaaja-id (:id user)}))]
    m))

(defn tallenna-toimenpiteen-omat-hinnat! [{:keys [db user hinnat toimenpide-id]}]
  (doseq [hinta (map #(poista-frontin-keksima-id % ::hinta/id) hinnat)]

    (specql/upsert! db
                    ::hinta/toimenpiteen-hinta
                    (kasittele-muokkaustiedot user hinta ::hinta/id)
                    {::muokkaustiedot/poistettu? (op/not= true)})))

(defn tallenna-toimenpiteen-tyot! [{:keys [db user tyot toimenpide-id]}]
  (doseq [tyo (map #(poista-frontin-keksima-id % ::tyo/id) tyot)]
    (specql/upsert! db
                    ::tyo/toimenpiteen-tyo
                    (kasittele-muokkaustiedot user tyo ::tyo/id)
                    {::muokkaustiedot/poistettu? (op/not= true)})))

(defn tallenna-toimenpide [db kayttaja-id kanavatoimenpide]
  (let [id? (id-olemassa? (::toimenpide/id kanavatoimenpide))
        kanavatoimenpide (cond-> kanavatoimenpide
                                 (::toimenpide/sijainti kanavatoimenpide) (update ::toimenpide/sijainti #(geo/geometry (geo/clj->pg %)))
                                 id? (assoc ::muokkaustiedot/muokattu (pvm/nyt)
                                            ::muokkaustiedot/muokkaaja-id kayttaja-id)
                                 (not id?) (assoc ::toimenpide/kuittaaja-id kayttaja-id
                                                  ::muokkaustiedot/luotu (pvm/nyt)
                                                  ::muokkaustiedot/luoja-id kayttaja-id))]
    (if id?
      (when (pos? (update! db ::toimenpide/kanava-toimenpide kanavatoimenpide {::toimenpide/id (::toimenpide/id kanavatoimenpide)}))
        {::toimenpide/id (::toimenpide/id kanavatoimenpide)})
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

(defn lisaa-kommentti! [db user tila kommentti toimenpide-id]
  (insert! db
           ::kommentti/toimenpiteen-kommentti
           {::kommentti/aika (pvm/nyt)
            ::kommentti/kommentti kommentti
            ::kommentti/tila tila
            ::kommentti/kayttaja-id (:id user)
            ::kommentti/toimenpide-id toimenpide-id}))
