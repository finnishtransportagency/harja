(ns harja.kyselyt.vesivaylat.toimenpiteet
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            
            [clj-time.core :as t]
            [namespacefy.core :as namespacefy]
            [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert! upsert!]]
            [specql.op :as op]
            [specql.rel :as rel]

            [taoensso.timbre :as log]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt.vesivaylat.tyot :as tyot-q]

            [harja.tyokalut.functor :refer [fmap]]
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
            [harja.domain.vesivaylat.komponentin-tilamuutos :as komp-tila]
            [harja.domain.urakka :as ur]
            [harja.pvm :as pvm]))

;; Näitä kyselyitä käyttävien funktioiden pitäis ehkä ennemminkin olla kyselyt/hinnoittelussa,
;; mutta tätä tarvittiin myös toimenpiteitä haettaessa, ja hinnoitteluista jo viitataan tähän namespaceen
(defqueries "harja/kyselyt/vesivaylat/hinnoittelut.sql")

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
                          ::vv-toimenpide/vikailmoitukset
                          ::vv-toimenpide/reimari-urakoitsija
                          ::vv-toimenpide/reimari-sopimus
                          ::vv-toimenpide/sopimus
                          ::vv-toimenpide/lisatieto
                          ::vv-toimenpide/harjassa-luotu
                          ::vv-toimenpide/liitteet
                          ::vv-toimenpide/komponentit
                          ::vv-toimenpide/reimari-henkilo-lkm
                          ::vv-toimenpide/hintatyyppi]))))

(defn- vaadi-toimenpiteet-kuuluvat-urakkaan* [toimenpiteet-kannassa toimenpide-idt urakka-id]
  (when (or
          (nil? urakka-id)
          (not (->> toimenpiteet-kannassa
                   (map ::vv-toimenpide/urakka-id)
                   (every? (partial = urakka-id)))))
    (throw (SecurityException. (str "Kaikki toimenpiteet " toimenpide-idt " eivät kuulu urakkaan " urakka-id)))))

(defn vaadi-toimenpiteet-kuuluvat-urakkaan [db toimenpide-idt urakka-id]
  (vaadi-toimenpiteet-kuuluvat-urakkaan*
    (fetch
     db
     ::vv-toimenpide/reimari-toimenpide
     (set/union vv-toimenpide/perustiedot vv-toimenpide/viittaus-idt)
     {::vv-toimenpide/id (op/in toimenpide-idt)})
    toimenpide-idt
    urakka-id))

(defn laskutuspvm-nyt-tai-tulevaisuudessa? [nyt pvm]
  (let [kuukauden-alkuun #(-> %
                              pvm/joda-timeksi
                              pvm/suomen-aikavyohykkeeseen
                              t/first-day-of-the-month
                              t/with-time-at-start-of-day)]
    (let [pvm (kuukauden-alkuun pvm)
          nyt (kuukauden-alkuun nyt)]
      (boolean
        (or (t/after? pvm nyt)
            (t/equal? pvm nyt))))))

;; Pitäis ehkä ennemminkin olla kyselyt/hinnoittelussa,
;; mutta tätä tarvittiin myös toimenpiteitä haettaessa, ja hinnoitteluista jo viitataan tähän namespaceen
(defn- laskutetut-laskutusluvat
  ([laskutusluvat]
   (laskutetut-laskutusluvat laskutusluvat (t/now)))
  ([laskutusluvat nyt]
   (set (keep
          (fn [{:keys [id laskutus-pvm]}]
            (when (and laskutus-pvm (not (laskutuspvm-nyt-tai-tulevaisuudessa? nyt laskutus-pvm)))
              id))
          laskutusluvat))))

;; Pitäis ehkä ennemminkin olla kyselyt/hinnoittelussa,
;; mutta tätä tarvittiin myös toimenpiteitä haettaessa, ja hinnoitteluista jo viitataan tähän namespaceen
(defn hinnoittelu-laskutettu? [db hinnoittelu-id]
  (let [laskutusluvat (laskutusluvalliset-hintaryhmat db)
        laskutetut (laskutetut-laskutusluvat laskutusluvat)]
    (boolean (laskutetut hinnoittelu-id))))

;; Pitäis ehkä ennemminkin olla kyselyt/hinnoittelussa,
;; mutta tätä tarvittiin myös toimenpiteitä haettaessa, ja hinnoitteluista jo viitataan tähän namespaceen
(defn liita-laskutuslupatiedot-hinnoitteluihin
  ([db hinnoittelut] (liita-laskutuslupatiedot-hinnoitteluihin db hinnoittelut nil))
  ([db hinnoittelut polku]
   (let [laskutusluvat (laskutusluvalliset-hintaryhmat db)
         laskutus-pvmt (into {} (map (juxt :id :laskutus-pvm) laskutusluvat))
         laskutetut (laskutetut-laskutusluvat laskutusluvat)]
     (mapv
       (fn [h]
         (let [id (if polku
                    (get-in h (conj polku ::vv-hinnoittelu/id))
                    (::vv-hinnoittelu/id h))]
           (-> h
               (assoc-in
                 ;; Toimenpiteen tapauksessa hinnoittelutieto menee hieman eri polkuun,
                 ;; kuin suoraan hintaryhmää käsiteltäessä
                 (if polku
                   (conj polku ::vv-hinnoittelu/laskutus-pvm)
                   [::vv-hinnoittelu/laskutus-pvm])
                 (laskutus-pvmt id))

               (assoc-in
                 (if polku
                   (conj polku ::vv-hinnoittelu/laskutettu?)
                   [::vv-hinnoittelu/laskutettu?])
                 (boolean (laskutetut id))))))
       hinnoittelut))))

(defn liita-laskutuslupatiedot-toimenpiteisiin [db toimenpiteet]
  (liita-laskutuslupatiedot-hinnoitteluihin db toimenpiteet [::vv-toimenpide/oma-hinnoittelu]))

(defn- hinnoittelu-ilman-poistettuja-hintoja [hinnoittelu]
  (update hinnoittelu ::vv-hinnoittelu/hinnat #(remove ::m/poistettu? %)))

(defn hae-hinnoittelut [hinnoittelu-linkit hintaryhma?]
  (let [sopivat-hintaryhmat
        (filter
          #(= (get-in % [::vv-hinnoittelu/hinnoittelut
                         ::vv-hinnoittelu/hintaryhma?])
              hintaryhma?)
          hinnoittelu-linkit)]
    (->> sopivat-hintaryhmat
         (map #(hinnoittelu-ilman-poistettuja-hintoja
                 (::vv-hinnoittelu/hinnoittelut %)))
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

(defn- toimenpiteet-tyotiedoilla* [tyot toimenpiteet]
  (map
    (fn [toimenpide]
      (let [toimenpiteen-hinnoittelu-id (get-in toimenpide [::vv-toimenpide/oma-hinnoittelu ::vv-hinnoittelu/id])
            hinnoittelun-tyot (filter #(= (::vv-tyo/hinnoittelu-id %) toimenpiteen-hinnoittelu-id) tyot)]
        (assoc-in toimenpide [::vv-toimenpide/oma-hinnoittelu ::vv-hinnoittelu/tyot] hinnoittelun-tyot)))
    toimenpiteet))

(defn- toimenpiteet-tyotiedoilla
  "Liittää toimenpiteiden omiin hinnoittelutietoihin mukaan työt."
  [db toimenpiteet]
  (let [hinnoittelu-idt (set (map #(get-in % [::vv-toimenpide/oma-hinnoittelu ::vv-hinnoittelu/id]) toimenpiteet))
        tyot (tyot-q/hae-hinnoittelujen-tyot db hinnoittelu-idt)]
    (toimenpiteet-tyotiedoilla* tyot toimenpiteet)))

(defn- hae-hinnoittelutiedot-toimenpiteille* [toimenpiteet]
  (->> toimenpiteet
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

(defn hae-hinnoittelutiedot-toimenpiteille [db toimenpide-idt]
  (->> (fetch db
              ::vv-toimenpide/reimari-toimenpide
              (set/union vv-toimenpide/perustiedot vv-toimenpide/hinnoittelu)
              (op/and
                {::vv-toimenpide/id (op/in toimenpide-idt)}))
       hae-hinnoittelutiedot-toimenpiteille*
       (liita-laskutuslupatiedot-toimenpiteisiin db)
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
        (remove #(empty? (::vv-toimenpide/vikailmoitukset %)) toimenpiteet)
        :default toimenpiteet))

(defn- toimenpiteet-hintatiedoilla* [hinnoittelutiedot toimenpiteet]
  (let [;; Esim. {1 [{:toimenpide-id 1 :oma-hinta {:hinnoittelu-id 2} :hintaryhma {:hinnoittelu-id 3}}]}
        hintatiedot (group-by ::vv-toimenpide/id hinnoittelutiedot)]
    (map
      (fn [toimenpide]
        (merge toimenpide (first (hintatiedot (::vv-toimenpide/id toimenpide)))))
      toimenpiteet)))

(defn- toimenpiteet-hintatiedoilla [db toimenpiteet]
  (toimenpiteet-hintatiedoilla*
    (hae-hinnoittelutiedot-toimenpiteille
     db
     (into #{} (map ::vv-toimenpide/id toimenpiteet)))
    toimenpiteet))

(defn- lisaa-toimenpiteen-komponentit* [toimenpiteet tilat komponentit]
  (let [komponentit (group-by ::tkomp/id komponentit)
        tilat (group-by ::komp-tila/toimenpide-id tilat)]
    (for [tp toimenpiteet]
      (assoc tp ::vv-toimenpide/komponentit
                (mapcat
                  (fn [tila]
                    (map
                      #(select-keys
                         (merge tila %)
                         [::komp-tila/tilakoodi
                          ::tkomp/sarjanumero
                          ::tkomp/valiaikainen
                          ::tkomp/id
                          ::tkomp/lisatiedot
                          ::tkomp/komponenttityyppi
                          ::tkomp/turvalaitenro])
                      (get komponentit (::komp-tila/komponentti-id tila))))
                  (get tilat (::vv-toimenpide/id tp)))))))

(defn- lisaa-toimenpiteen-komponentit [toimenpiteet db]
  (let [tilat (fetch db
                     ::komp-tila/tpk-tilat
                     #{::komp-tila/toimenpide-id
                       ::komp-tila/komponentti-id
                       ::komp-tila/tilakoodi}
                     {::komp-tila/toimenpide-id
                      (op/in (set (map ::vv-toimenpide/id toimenpiteet)))})
        komponentit (fetch db
                           ::tkomp/turvalaitekomponentti
                           (set/union #{::tkomp/id
                                        ::tkomp/lisatiedot
                                        ::tkomp/turvalaitenro
                                        ::tkomp/sarjanumero
                                        ::tkomp/valiaikainen}
                                      tkomp/komponenttityyppi)
                           {::tkomp/id
                            (op/in (set (map ::komp-tila/komponentti-id tilat)))})]
    (lisaa-toimenpiteen-komponentit* toimenpiteet tilat komponentit)))

(defn- toimenpiteiden-liite-idt* [liite-linkit]
  (fmap #(map ::vv-toimenpide/liite-id %)
        (group-by ::vv-toimenpide/toimenpide-id
                  liite-linkit)))

(defn- toimenpiteiden-liite-idt
  "Hakee annetuille toimenpiteille liitteet, jotka eivät ole poistettuja.
  Palauttaa mäpin toimenpide id:stä listaan liite id:tä."
  [db toimenpiteet]
  (toimenpiteiden-liite-idt*
    (fetch db ::vv-toimenpide/toimenpide<->liite

          #{::vv-toimenpide/liite-id ::vv-toimenpide/toimenpide-id}

          ;; Haetaan liitelinkit kaikille toimenpiteille
          {::vv-toimenpide/toimenpide-id
           (op/in (map ::vv-toimenpide/id toimenpiteet))
           ::m/poistettu? false})))

(defn- lisaa-liitteet* [toimenpiteet liite-idt-toimenpiteille liitteet]
  (let [;; Haetaan liitteet {liiteid liitteen-tiedot} mäppiin
        liitteet (into {}
                       (map (juxt ::liite/id identity))
                       liitteet)]
    (for [{id ::vv-toimenpide/id :as toimenpide} toimenpiteet
          :let [toimenpiteen-liitteet (liite-idt-toimenpiteille id)]]
      (assoc toimenpide
        ::vv-toimenpide/liitteet
        (map (comp namespacefy/unnamespacefy liitteet) toimenpiteen-liitteet)))))

(defn- lisaa-liitteet [toimenpiteet db]
  (let [;; Hae kaikki liite id:t reimari toimenpiteille
        liite-idt-toimenpiteille (toimenpiteiden-liite-idt db toimenpiteet)

        ;; Listataan IN listaa varten kaikki liitteet
        liite-idt (mapcat val liite-idt-toimenpiteille)]
    (lisaa-liitteet* toimenpiteet
                     liite-idt-toimenpiteille
                     (fetch db ::liite/liite
                            liite/perustiedot
                            {::liite/id (op/in liite-idt)}))))

(defn hae-toimenpiteet [db {:keys [alku loppu vikailmoitukset?
                                   tyyppi urakoitsija-id] :as tiedot}]
  (let [yksikkohintaiset? (= :yksikkohintainen tyyppi)
        kokonaishintaiset? (= :kokonaishintainen tyyppi)
        urakka-id (::vv-toimenpide/urakka-id tiedot)
        sopimus-id (::vv-toimenpide/sopimus-id tiedot)
        vaylatyyppi (::vv-vayla/vaylatyyppi tiedot)
        vaylanro (::vv-toimenpide/vaylanro tiedot)
        turvalaitenro (::vv-toimenpide/turvalaitenro tiedot)
        tyolaji (::vv-toimenpide/reimari-tyolaji tiedot)
        tyoluokat (::vv-toimenpide/reimari-tyoluokat tiedot)
        toimenpiteet (::vv-toimenpide/reimari-toimenpidetyypit tiedot)
        fetchattu (fetch db ::vv-toimenpide/reimari-toimenpide
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
                               vv-toimenpide/vikailmoitus
                               vv-toimenpide/kiintio
                               vv-toimenpide/reimari-kentat
                               vv-toimenpide/metatiedot
                               ;; Myös hinnoittelut pitää hakea erikseen, eli hinnoittelutietojen
                               ;; täydentäminen aiheuttaa ylimääräisen haun samaan tauluun
                               ;; vv-toimenpide/hinnoittelu
                               )
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
                               (when vaylanro
                                 {::vv-toimenpide/vaylanro vaylanro})
                               (when turvalaitenro
                                 {::vv-toimenpide/turvalaitenro turvalaitenro})
                               (when tyolaji
                                 {::vv-toimenpide/reimari-tyolaji tyolaji})
                               (when tyoluokat
                                 {::vv-toimenpide/reimari-tyoluokka (op/in tyoluokat)})
                               (when toimenpiteet
                                 {::vv-toimenpide/reimari-toimenpidetyyppi (op/in toimenpiteet)})))
        fetchattu (-> fetchattu
                      (suodata-vikakorjaukset vikailmoitukset?)
                      (lisaa-toimenpiteen-komponentit db)
                      (lisaa-liitteet db))
        toimenpiteet (into [] toimenpiteet-xf fetchattu)]
    (cond
      yksikkohintaiset?
      (toimenpiteet-hintatiedoilla db toimenpiteet)

      kokonaishintaiset?
      toimenpiteet)))

(defn tallenna-toimenpide! [db user toimenpide]
  (jdbc/with-db-transaction [db db]
    (if (id-olemassa? (::vv-toimenpide/id toimenpide))
      (update! db
               ::vv-toimenpide/reimari-toimenpide
               (-> toimenpide
                   (assoc ::vv-toimenpide/harjassa-luotu true)
                   (m/lisaa-muokkaustiedot ::vv-toimenpide/id user))
               {::vv-toimenpide/id (::vv-toimenpide/id toimenpide)})

      (insert! db
               ::vv-toimenpide/reimari-toimenpide
               (-> toimenpide
                   (assoc ::vv-toimenpide/harjassa-luotu true)
                   (m/lisaa-muokkaustiedot ::vv-toimenpide/id user))))))
