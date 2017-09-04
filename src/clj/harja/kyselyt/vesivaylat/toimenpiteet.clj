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

            [harja.kyselyt.vesivaylat.tyot :as tyot-q]

            [harja.domain.muokkaustiedot :as m]
            [harja.domain.liite :as liite]
            [harja.domain.vesivaylat.urakoitsija :as vv-urakoitsija]
            [harja.domain.vesivaylat.toimenpide :as vv-toimenpide]
            [harja.domain.vesivaylat.vayla :as vv-vayla]
            [harja.domain.vesivaylat.tyo :as vv-tyo]
            [harja.domain.vesivaylat.turvalaitekomponentti :as tkomp]
            [harja.domain.vesivaylat.turvalaite :as vv-turvalaite]
            [harja.domain.vesivaylat.hinnoittelu :as vv-hinnoittelu]
            [harja.domain.vesivaylat.hinta :as vv-hinta]
            [harja.domain.urakka :as ur]
            [harja.tyokalut.functor :refer [fmap]]))

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
                          ::vv-toimenpide/turvalaitekomponentit
                          ::vv-toimenpide/reimari-henkilo-lkm
                          ::vv-toimenpide/komponenttien-tilat]))))

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

(defn- toimenpiteet-tyotiedoilla
  "Liittää toimenpiteiden omiin hinnoittelutietoihin mukaan työt."
  [db toimenpiteet]
  (let [hinnoittelu-idt (set (map #(get-in % [::vv-toimenpide/oma-hinnoittelu ::vv-hinnoittelu/id]) toimenpiteet))
        tyot (tyot-q/hae-hinnoittelujen-tyot db hinnoittelu-idt)]
    (map
      (fn [toimenpide]
        (let [toimenpiteen-hinnoittelu-id (get-in toimenpide [::vv-toimenpide/oma-hinnoittelu ::vv-hinnoittelu/id])
              hinnoittelun-tyot (filter #(= (::vv-tyo/hinnoittelu-id %) toimenpiteen-hinnoittelu-id) tyot)]
          (assoc-in toimenpide [::vv-toimenpide/oma-hinnoittelu ::vv-hinnoittelu/tyot] hinnoittelun-tyot)))
      toimenpiteet)))

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
       (map #(dissoc % ::vv-toimenpide/hintaryhma))
       ;; Liitetään vielä mukaan työt (specql ei osannut joinia näitä suoraan)
       (toimenpiteet-tyotiedoilla db)))

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
        (filter #(not (empty? (::vv-toimenpide/reimari-viat %))) toimenpiteet)
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

(defn- zip2 [a b]
  (map vector a b))

(defn- tpk-mapeiksi [kv-parit]
  (loop [parit kv-parit
         tulos-map nil]
    (let [[id m] (first parit)]
      (if m
        (recur (rest parit)
               (assoc tulos-map id (conj (get tulos-map id) m)))
        tulos-map))))


(defn- lisaa-komponenttikohtaiset-tilat [toimenpiteet db]
  (let [tpk-tilat-seq (fetch db ::vv-toimenpide/tpk-tilat
                             #{::vv-toimenpide/toimenpide-id
                               ::vv-toimenpide/komponentti-id ::vv-toimenpide/tilakoodi}
                             {::vv-toimenpide/toimenpide-id
                              (op/in (set (map ::vv-toimenpide/id toimenpiteet)))})
        tpk-tilat-map (tpk-mapeiksi (zip2 (map ::vv-toimenpide/toimenpide-id tpk-tilat-seq)
                                          tpk-tilat-seq))
        ;; _ (println "tpk-tilat-map:" tpk-tilat-map)
        tilat-toimenpiteelle #(get tpk-tilat-map (::vv-toimenpide/id %))]
    ;; (println "kk-tilat: palautetaan tilat (map )")
    (map #(assoc % ::vv-toimenpide/komponenttien-tilat (tilat-toimenpiteelle %)) toimenpiteet)))

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



(defn- toimenpiteiden-liite-idt
  "Hakee annetuille toimenpiteille liitteet, jotka eivät ole poistettuja.
  Palauttaa mäpin toimenpide id:stä listaan liite id:tä."
  [db toimenpiteet]
  (fmap #(map ::vv-toimenpide/liite-id %)
        (group-by ::vv-toimenpide/toimenpide-id
                  (fetch db ::vv-toimenpide/toimenpide<->liite

                         #{::vv-toimenpide/liite-id ::vv-toimenpide/toimenpide-id}

                         ;; Haetaan liitelinkit kaikille toimenpiteille
                         {::vv-toimenpide/toimenpide-id
                          (op/in (map ::vv-toimenpide/id toimenpiteet))
                          ::m/poistettu? false}))))

(defn- lisaa-liitteet [toimenpiteet db]
  (let [;; Hae kaikki liite id:t reimari toimenpiteille
        liite-idt-toimenpiteelle (toimenpiteiden-liite-idt db toimenpiteet)

        ;; Listataan IN listaa varten kaikki liitteet
        liite-idt (mapcat val liite-idt-toimenpiteelle)

        ;; Haetaan liitteet {liiteid liitteen-tiedot} mäppiin
        liitteet (into {}
                       (map (juxt ::liite/id identity))
                       (fetch db ::liite/liite
                              liite/perustiedot
                              {::liite/id (op/in liite-idt)}))]
    (for [{id ::vv-toimenpide/id :as toimenpide} toimenpiteet
          :let [toimenpiteen-liitteet (liite-idt-toimenpiteelle id)]]
      (assoc toimenpide
        ::vv-toimenpide/liitteet
        (map (comp namespacefy/unnamespacefy liitteet) toimenpiteen-liitteet)))))

(defn hae-toimenpiteet [db {:keys [alku loppu vikailmoitukset?
                                   tyyppi urakoitsija-id] :as tiedot}]
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

                               ;; Haetaan liitteet erikseen,
                               ;; specql 0.6 versio ei osaa hakea 2 has-many
                               ;; joukkoa samalla tasolla
                               ;;vv-toimenpide/liitteet
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
                               (when urakoitsija-id
                                 {::vv-toimenpide/reimari-urakoitsija {::vv-urakoitsija/r-id urakoitsija-id}})
                               (when kokonaishintaiset?
                                 {::vv-toimenpide/hintatyyppi :kokonaishintainen})
                               (when yksikkohintaiset?
                                 {::vv-toimenpide/hintatyyppi :yksikkohintainen})
                               (when sopimus-id
                                 {::vv-toimenpide/sopimus-id sopimus-id})
                               (when (and alku loppu)
                                 {::vv-toimenpide/suoritettu (op/between alku loppu)})
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
                      (lisaa-komponenttikohtaiset-tilat db)
                      (lisaa-liitteet db))
        toimenpiteet (into [] toimenpiteet-xf fetchattu)]
    (cond
      yksikkohintaiset?
      (toimenpiteet-hintatiedoilla db toimenpiteet)

      kokonaishintaiset?
      toimenpiteet)))
