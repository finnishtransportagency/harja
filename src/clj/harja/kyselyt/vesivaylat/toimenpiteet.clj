(ns harja.kyselyt.vesivaylat.toimenpiteet
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! upsert!]]
            [specql.op :as op]
            [specql.rel :as rel]
            [taoensso.timbre :as log]

            [harja.kyselyt.vesivaylat.hinnoittelut :as h-q]

            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.urakoitsija :as vv-urakoitsija]
            [harja.domain.vesivaylat.toimenpide :as vv-toimenpide]
            [harja.domain.vesivaylat.vayla :as vv-vayla]
            [harja.domain.vesivaylat.turvalaite :as vv-turvalaite]
            [harja.domain.vesivaylat.hinnoittelu :as vv-hinnoittelu]
            [harja.domain.urakka :as ur]))

(def toimenpiteet-xf
  (comp
    (map #(set/rename-keys % {::vv-toimenpide/reimari-tyolaji ::vv-toimenpide/tyolaji
                              ::vv-toimenpide/reimari-tyoluokka ::vv-toimenpide/tyoluokka
                              ::vv-toimenpide/suoritettu ::vv-toimenpide/pvm
                              ::vv-toimenpide/reimari-toimenpidetyyppi ::vv-toimenpide/toimenpide
                              ::vv-toimenpide/reimari-turvalaite ::vv-toimenpide/turvalaite}))
    (map #(assoc % ::vv-toimenpide/tyolaji (get vv-toimenpide/reimari-tyolajit (::vv-toimenpide/tyolaji %))
                   ::vv-toimenpide/tyoluokka (get vv-toimenpide/reimari-tyoluokat (::vv-toimenpide/tyoluokka %))
                   ::vv-toimenpide/toimenpide (get vv-toimenpide/reimari-toimenpidetyypit (::vv-toimenpide/toimenpide %))
                   ::vv-toimenpide/turvalaite {::vv-turvalaite/nimi (get-in % [::vv-toimenpide/turvalaite ::vv-turvalaite/r-nimi])}
                   ::vv-toimenpide/vikakorjauksia? (not (empty? (::vv-toimenpide/vikailmoitukset %)))))
    (map #(select-keys % [::vv-toimenpide/id
                          ::vv-toimenpide/tyolaji
                          ::vv-toimenpide/vayla
                          ::vv-toimenpide/tyoluokka
                          ::vv-toimenpide/pvm
                          ::vv-toimenpide/toimenpide
                          ::vv-toimenpide/turvalaite
                          ::vv-toimenpide/vikakorjauksia?]))))

(defn suodata-vikakorjaukset [toimenpiteet vikailmoitukset?]
  (cond (true? vikailmoitukset?)
        (filter #(not (empty? (::vv-toimenpide/vikailmoitukset %))) toimenpiteet)
        :default toimenpiteet))

(defn paivita-toimenpiteiden-tyyppi [db toimenpide-idt uusi-tyyppi]
  (jdbc/with-db-transaction [db db]
    (update! db ::vv-toimenpide/reimari-toimenpide
             {::vv-toimenpide/hintatyyppi (name uusi-tyyppi)}
             {::vv-toimenpide/id (op/in toimenpide-idt)})))

(defn toimenpiteiden-hinnoittelut-yhdistettyna [hintatiedot]
  (into {}
        (map
          (fn [[toimenpide-id tiedot]]
            {toimenpide-id
             (reduce (fn [hinnoittelu1 hinnoittelu2]
                       (update hinnoittelu1
                               ::vv-toimenpide/hinnoittelu-linkit
                               concat
                               (::vv-toimenpide/hinnoittelu-linkit hinnoittelu2)))
                     tiedot)})
          hintatiedot)))

(defn- toimenpiteet-hintatiedoilla [db toimenpiteet]
  (let [ ;; {1 [{:toimenpide-id 1 :hinnoittelu-linkit [{:id 2}]} {:id 1 :hinnoittelu-linkit [{:id 3}]}]}
        hintatiedot (group-by ::vv-toimenpide/id
                            (h-q/hae-hinnoittelutiedot-toimenpiteille
                              db
                              (into #{} (map ::vv-toimenpide/id toimenpiteet))))
        ;; {1 [{:toimenpide-id 1 :hinnoittelu-linkit [{:id 2} {:id 3}]}]}]}
        hintatiedot-yhdistettyna (toimenpiteiden-hinnoittelut-yhdistettyna hintatiedot)]
    (map
      (fn [toimenpide]
        (merge toimenpide (first (hintatiedot-yhdistettyna (::vv-toimenpide/id toimenpide)))))
      toimenpiteet)))

(defn- toimenpiteet-hintaryhmissa [db toimenpiteet]
  (let [hintatiedoilla (toimenpiteet-hintatiedoilla db toimenpiteet)
        hintaryhmilla-ryhmiteltyna
        (group-by
          (fn [h]
            (first (filter (comp ::vv-hinnoittelu/hintaryhma?
                                 ::vv-hinnoittelu/hinnoittelut)
                           (::vv-toimenpide/hinnoittelu-linkit h))))
          hintatiedoilla)]

    ;; Ilman redundanttia hintaryhmää toimenpiteen hinnoittelutiedoissa
    (into {}
          (map
            (fn [[hintaryhma toimenpiteet]]
              {hintaryhma
               (map
                 (fn [t]
                   (update t ::vv-toimenpide/hinnoittelu-linkit
                           #(remove
                              (comp ::vv-hinnoittelu/hintaryhma?
                                    ::vv-hinnoittelu/hinnoittelut) %)))
                 toimenpiteet)})
            hintaryhmilla-ryhmiteltyna))))

(defn hae-toimenpiteet [db {:keys [alku loppu vikailmoitukset?
                                   tyyppi luotu-alku luotu-loppu urakoitsija-id] :as tiedot}]
  (let [yksikkohintaiset? (= :yksikkohintainen tyyppi)
        kokonaishintaiset? (= :kokonaishintainen tyyppi)
        urakka-id (::vv-toimenpide/urakka-id tiedot)
        sopimus-id (::vv-toimenpide/sopimus-id tiedot)
        vaylatyyppi (::vv-vayla/vaylatyyppi tiedot)
        vayla-id (::vv-toimenpide/vayla-id tiedot)
        tyolaji (::vv-toimenpide/reimari-tyolaji tiedot)
        tyoluokat (::vv-toimenpide/reimari-tyoluokat tiedot)
        toimenpiteet (::vv-toimenpide/reimari-toimenpidetyypit tiedot)
        fetchattu (-> (fetch db ::vv-toimenpide/reimari-toimenpide
                             (clojure.set/union
                               vv-toimenpide/perustiedot
                               (disj vv-toimenpide/viittaukset vv-toimenpide/urakka)
                               vv-toimenpide/reimari-kentat
                               vv-toimenpide/metatiedot)
                             (op/and
                               {::m/poistettu? false}
                               {::vv-toimenpide/urakka-id urakka-id}
                               (when (and luotu-alku luotu-loppu)
                                 {::m/reimari-luotu (op/between luotu-alku luotu-loppu)})
                               (when urakoitsija-id
                                 {::vv-toimenpide/reimari-urakoitsija {::vv-urakoitsija/r-id urakoitsija-id}})
                               (when kokonaishintaiset?
                                 {::vv-toimenpide/hintatyyppi :kokonaishintainen})
                               (when yksikkohintaiset?
                                 {::vv-toimenpide/hintatyyppi :yksikkohintainen})
                               (when sopimus-id
                                 {::vv-toimenpide/sopimus-id sopimus-id})
                               (when (and alku loppu)
                                 {::vv-toimenpide/reimari-luotu (op/between alku loppu)})
                               (when vaylatyyppi
                                 {::vv-toimenpide/vayla {::vv-vayla/tyyppi vaylatyyppi}})
                               (when vayla-id
                                 {::vv-toimenpide/vayla {::vv-vayla/id vayla-id}})
                               (when tyolaji
                                 {::vv-toimenpide/reimari-tyolaji tyolaji})
                               (when tyoluokat
                                 {::vv-toimenpide/reimari-tyoluokka (op/in tyoluokat)})
                               (when toimenpiteet
                                 {::vv-toimenpide/reimari-toimenpidetyyppi (op/in toimenpiteet)})))
                      (suodata-vikakorjaukset vikailmoitukset?))]
    (cond->> (into [] toimenpiteet-xf fetchattu)
             yksikkohintaiset? (toimenpiteet-hintaryhmissa db)
             kokonaishintaiset? (identity))))
