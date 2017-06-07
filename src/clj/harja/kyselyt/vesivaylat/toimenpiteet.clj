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

(defn toimenpiteet-kuuluvat-urakkaan? [db toimenpide-idt urakka-id]
  (boolean
    (->> (fetch
           db
           ::vv-toimenpide/reimari-toimenpide
           (set/union vv-toimenpide/perustiedot)
           {::vv-toimenpide/id (op/in toimenpide-idt)})
         (keep ::vv-toimenpide/urakka-id)
         (every? (partial = urakka-id)))))

(defn- hinnoittelu-ilman-poistettuja-hintoja [hinnoittelu]
  (assoc hinnoittelu ::vv-hinnoittelu/hinnat
                     (vec (remove ::m/poistettu? (::vv-hinnoittelu/hinnat hinnoittelu)))))

(defn hae-hinnoittelut [hinnoittelu-linkit hintaryhma?]
  (let [sopivat-hintaryhmat
        (filter
          #(= (get-in % [::vv-hinnoittelu/hinnoittelut
                         ::vv-hinnoittelu/hintaryhma?])
              hintaryhma?)
          hinnoittelu-linkit)]
    (->> (map #(hinnoittelu-ilman-poistettuja-hintoja
                 (::vv-hinnoittelu/hinnoittelut %))
              sopivat-hintaryhmat)
         (remove ::m/poistettu?))))

(defn toimenpide-siistitylla-hintatiedolla [hintaryhma? avain toimenpiteet]
  (map #(if-let [h (first (hae-hinnoittelut (::vv-toimenpide/hinnoittelu-linkit %) hintaryhma?))]
          (assoc % avain h)
          (identity %))
       toimenpiteet))

(def toimenpiteet-omalla-hinnoittelulla (partial toimenpide-siistitylla-hintatiedolla
                                                 false
                                                 ::vv-toimenpide/oma-hinnoittelu))
(def toimenpiteet-hintaryhmalla (partial toimenpide-siistitylla-hintatiedolla
                                         true
                                         ::vv-toimenpide/hintaryhma))

(defn hae-hinnoittelutiedot-toimenpiteille [db toimenpide-idt]
  (->> (fetch db
              ::vv-toimenpide/reimari-toimenpide
              (set/union vv-toimenpide/perustiedot vv-toimenpide/hinnoittelu)
              (op/and
                {::vv-toimenpide/id (op/in toimenpide-idt)}))

       (toimenpiteet-omalla-hinnoittelulla)
       (toimenpiteet-hintaryhmalla)
       ;; Poistetaan turha hinnoittelu-linkit avain
       (map #(dissoc % ::vv-toimenpide/hinnoittelu-linkit))

       ;; Groupataan ja yhdistetään toimenpiteen tiedot
       (group-by ::vv-toimenpide/id)
       vals
       (map (partial apply merge))))

(defn suodata-vikakorjaukset [toimenpiteet vikailmoitukset?]
  (cond (true? vikailmoitukset?)
        (filter #(not (empty? (::vv-toimenpide/vikailmoitukset %))) toimenpiteet)
        :default toimenpiteet))

(defn paivita-toimenpiteiden-tyyppi [db toimenpide-idt uusi-tyyppi]
  (jdbc/with-db-transaction [db db]
    (update! db ::vv-toimenpide/reimari-toimenpide
             {::vv-toimenpide/hintatyyppi (name uusi-tyyppi)}
             {::vv-toimenpide/id (op/in toimenpide-idt)})))

(defn- toimenpiteet-hintatiedoilla [db toimenpiteet]
  (let [ ;; {1 [{:toimenpide-id 1 :oma-hinta {:hinnoittelu-id 2} :hintaryhma {:hinnoittelu-id 3}}]}
        hintatiedot (group-by ::vv-toimenpide/id
                            (hae-hinnoittelutiedot-toimenpiteille
                              db
                              (into #{} (map ::vv-toimenpide/id toimenpiteet))))]
    (map
      (fn [toimenpide]
        (merge toimenpide (first (hintatiedot (::vv-toimenpide/id toimenpide)))))
      toimenpiteet)))

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
             yksikkohintaiset? (toimenpiteet-hintatiedoilla db)
             kokonaishintaiset? (identity))))
