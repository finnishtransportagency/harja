(ns harja.kyselyt.vesivaylat.toimenpiteet
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [namespacefy.core :as namespacefy]
            [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert! upsert!]]
            [specql.op :as op]
            [specql.rel :as rel]
            [taoensso.timbre :as log]

            [harja.domain.muokkaustiedot :as m]
            [harja.domain.liite :as liite]
            [harja.domain.vesivaylat.urakoitsija :as vv-urakoitsija]
            [harja.domain.vesivaylat.toimenpide :as vv-toimenpide]
            [harja.domain.vesivaylat.vayla :as vv-vayla]
            [harja.domain.vesivaylat.turvalaitekomponentti :as tkomp]
            [harja.domain.vesivaylat.turvalaite :as vv-turvalaite]
            [harja.domain.vesivaylat.hinnoittelu :as vv-hinnoittelu]
            [harja.domain.urakka :as ur]))

(def toimenpiteet-xf
  (comp
    ;; reimari-prefixatut ovat Reimari-dataa (monet tekstikoodeja), Harjassa käytetään
    ;; useimmiten keywordeja ja siksi nimimuunnos
    (map #(set/rename-keys % {::vv-toimenpide/reimari-tyolaji ::vv-toimenpide/tyolaji
                              ::vv-toimenpide/reimari-tyoluokka ::vv-toimenpide/tyoluokka
                              ::vv-toimenpide/suoritettu ::vv-toimenpide/pvm
                              ::vv-toimenpide/reimari-toimenpidetyyppi ::vv-toimenpide/toimenpide}))
    (map #(assoc % ::vv-toimenpide/tyolaji (get vv-toimenpide/reimari-tyolajit (::vv-toimenpide/tyolaji %))
                   ::vv-toimenpide/tyoluokka (get vv-toimenpide/reimari-tyoluokat (::vv-toimenpide/tyoluokka %))
                   ::vv-toimenpide/toimenpide (get vv-toimenpide/reimari-toimenpidetyypit (::vv-toimenpide/toimenpide %))
                   ::vv-toimenpide/vikakorjauksia? (not (empty? (::vv-toimenpide/vikailmoitukset %)))))
    (map #(select-keys % [::vv-toimenpide/id
                          ::vv-toimenpide/tyolaji
                          ::vv-toimenpide/vayla
                          ::vv-toimenpide/tyoluokka
                          ::vv-toimenpide/kiintio
                          ::vv-toimenpide/pvm
                          ::vv-toimenpide/toimenpide
                          ::vv-toimenpide/turvalaite
                          ::vv-toimenpide/vikakorjauksia?
                          ::vv-toimenpide/reimari-urakoitsija
                          ::vv-toimenpide/reimari-sopimus
                          ::vv-toimenpide/lisatieto
                          ::vv-toimenpide/liitteet
                          ::vv-toimenpide/turvalaitekomponentit]))))

(defn vaadi-toimenpiteet-kuuluvat-urakkaan [db toimenpide-idt urakka-id]
  (when-not (->> (fetch
                   db
                   ::vv-toimenpide/reimari-toimenpide
                   (set/union vv-toimenpide/perustiedot vv-toimenpide/viittaus-idt)
                   {::vv-toimenpide/id (op/in toimenpide-idt)})
                 (keep ::vv-toimenpide/urakka-id)
                 (every? (partial = urakka-id)))
    (throw (SecurityException. (str "Toimenpiteet " toimenpide-idt " eivät kuulu urakkaan " urakka-id)))))

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

(defn ilman-poistettuja-linkkeja [toimenpiteet]
  (map
    (fn [t]
      (update t
              ::vv-toimenpide/hinnoittelu-linkit
              (fn [linkit]
                (remove ::m/poistettu? linkit))))
    toimenpiteet))

(defn hae-hinnoittelutiedot-toimenpiteille [db toimenpide-idt]
  (->> (fetch db
              ::vv-toimenpide/reimari-toimenpide
              (set/union vv-toimenpide/perustiedot vv-toimenpide/hinnoittelu)
              (op/and
                {::vv-toimenpide/id (op/in toimenpide-idt)}))
       (ilman-poistettuja-linkkeja)
       (toimenpiteet-omalla-hinnoittelulla)
       (toimenpiteet-hintaryhmalla)
       ;; Poistetaan turha hinnoittelu-linkit avain
       (map #(dissoc % ::vv-toimenpide/hinnoittelu-linkit))
       ;; Groupataan ja yhdistetään toimenpiteen tiedot
       (group-by ::vv-toimenpide/id)
       vals
       (map (partial apply merge))
       ;; Säilytetään hintaryhmästä vain hinnoittelu-id
       (map #(if-let [hinnoitteluryhma-id (get-in % [::vv-toimenpide/hintaryhma ::vv-hinnoittelu/id])]
               (assoc % ::vv-toimenpide/hintaryhma-id hinnoitteluryhma-id)
               %))
       (map #(dissoc % ::vv-toimenpide/hintaryhma))))

(defn paivita-toimenpiteiden-tyyppi [db toimenpide-idt uusi-tyyppi]
  (update! db ::vv-toimenpide/reimari-toimenpide
           {::vv-toimenpide/hintatyyppi (name uusi-tyyppi)}
           {::vv-toimenpide/id (op/in toimenpide-idt)}))

(defn lisaa-toimenpiteelle-liite [db toimenpide-id liite-id]
  (insert! db ::vv-toimenpide/toimenpide<->liite
           {::vv-toimenpide/toimenpide-id toimenpide-id
            ::vv-toimenpide/liite-id liite-id}))

(defn poista-toimenpiteen-liite [db toimenpide-id liite-id]
  (update! db ::vv-toimenpide/toimenpide<->liite
           {::m/poistettu? true}
           {::vv-toimenpide/toimenpide-id toimenpide-id
            ::vv-toimenpide/liite-id liite-id}))

(defn- suodata-vikakorjaukset [toimenpiteet vikailmoitukset?]
  (cond (true? vikailmoitukset?)
        (filter #(not (empty? (::vv-toimenpide/vikailmoitukset %))) toimenpiteet)
        :default toimenpiteet))

(defn- toimenpiteet-hintatiedoilla [db toimenpiteet]
  (let [;; Esim. {1 [{:toimenpide-id 1 :oma-hinta {:hinnoittelu-id 2} :hintaryhma {:hinnoittelu-id 3}}]}
        hintatiedot (group-by ::vv-toimenpide/id
                              (hae-hinnoittelutiedot-toimenpiteille
                                db
                                (into #{} (map ::vv-toimenpide/id toimenpiteet))))]
    (map
      (fn [toimenpide]
        (merge toimenpide (first (hintatiedot (::vv-toimenpide/id toimenpide)))))
      toimenpiteet)))

(defn- lisaa-turvalaitekomponentit [toimenpiteet db]
  (let [turvalaitekomponentit (fetch
                                db
                                ::tkomp/turvalaitekomponentti
                                (set/union #{::tkomp/sarjanumero ::tkomp/turvalaitenro}
                                           tkomp/komponenttityyppi)
                                {::tkomp/turvalaitenro
                                 (op/in (set (map
                                               #(get-in % [::vv-toimenpide/reimari-turvalaite
                                                           ::vv-turvalaite/r-nro])
                                               toimenpiteet)))})
        toimenpiteet-turvalaitekomponenteilla
        (map #(assoc % ::vv-toimenpide/turvalaitekomponentit
                       (tkomp/turvalaitekomponentit-turvalaitenumerolla
                         turvalaitekomponentit
                         (get-in % [::vv-toimenpide/reimari-turvalaite ::vv-turvalaite/r-nro])))
             toimenpiteet)]
    toimenpiteet-turvalaitekomponenteilla))

(defn- lisaa-liitteet [toimenpiteet db]
  (let [liite-idt (mapcat (fn [toimenpide]
                            (let [toimenpiteen-liite-linkit (filter (comp not ::m/poistettu?)
                                                                    (::vv-toimenpide/liite-linkit toimenpide))]
                              (keep ::vv-toimenpide/liite-id toimenpiteen-liite-linkit)))
                          toimenpiteet)
        liitteet (fetch
                   db ::liite/liite
                   liite/perustiedot
                   {::liite/id (op/in liite-idt)})
        toimenpiteet-liitteilla
        (map (fn [toimenpide]
               (let [toimenpiteen-liite-idt (set (keep ::vv-toimenpide/liite-id
                                                       (::vv-toimenpide/liite-linkit toimenpide)))
                     toimenpiteen-liitteet (filterv #(toimenpiteen-liite-idt (::liite/id %)) liitteet)]
                 (-> toimenpide
                     (assoc ::vv-toimenpide/liitteet (namespacefy/unnamespacefy toimenpiteen-liitteet))
                     (dissoc ::vv-toimenpide/liite-linkit))))
             toimenpiteet)]
    toimenpiteet-liitteilla))

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
                               vv-toimenpide/liitteet ;; FIXME Vain yksi liite!? HAR-5707
                               vv-toimenpide/vikailmoitus
                               vv-toimenpide/urakoitsija
                               vv-toimenpide/sopimus
                               vv-toimenpide/turvalaite
                               vv-toimenpide/vayla
                               vv-toimenpide/kiintio
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
                      (suodata-vikakorjaukset vikailmoitukset?)
                      (lisaa-turvalaitekomponentit db)
                      (lisaa-liitteet db))]
    (cond->> (into [] toimenpiteet-xf fetchattu)
             yksikkohintaiset? (toimenpiteet-hintatiedoilla db)
             kokonaishintaiset? (identity))))