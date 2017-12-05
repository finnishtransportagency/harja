(ns harja.kyselyt.kanavat.liikennetapahtumat
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]

            [clojure.set :as set]
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [specql.op :as op]
            [specql.rel :as rel]
            [taoensso.timbre :as log]
            [jeesql.core :refer [defqueries]]

            [harja.id :refer [id-olemassa?]]
            [harja.pvm :as pvm]

            [harja.kyselyt.kanavat.kohteet :as kohteet-q]

            [harja.domain.urakka :as ur]
            [harja.domain.sopimus :as sop]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-toiminto :as toiminto]
            [harja.domain.kanavat.kohde :as kohde]))

(defn- liita-kohteen-urakkatiedot [kohteiden-haku tapahtumat]
  (let [kohteet (group-by ::kohde/id (kohteiden-haku (map ::lt/kohde tapahtumat)))]
    (into []
          (map
            #(update % ::lt/kohde
                     (fn [kohde]
                       (if-let [kohteen-urakat (-> kohde ::kohde/id kohteet first ::kohde/urakat)]
                         (assoc kohde ::kohde/urakat kohteen-urakat)
                         (assoc kohde ::kohde/urakat []))))
            tapahtumat))))

(defn- urakat-idlla [urakka-id tapahtuma]
  (update-in tapahtuma
             [::lt/kohde ::kohde/urakat]
             (fn [urakat]
               (keep
                 #(when (= (::ur/id %) urakka-id) %)
                 urakat))))

(defn- hae-liikennetapahtumat* [tapahtumat
                                urakkatiedot-fn
                                urakka-id]
  (->>
    tapahtumat
    (liita-kohteen-urakkatiedot urakkatiedot-fn)
    (map (partial urakat-idlla urakka-id))
    (remove (comp empty? ::kohde/urakat ::lt/kohde))))

(def ilman-poistettuja-aluksia (map #(update % ::lt/alukset (partial remove ::m/poistettu?))))

(def vain-uittoniput (keep (fn [t]
                             (let [t (update t ::lt/alukset
                                             (partial remove (comp #(or (nil? %) (zero? %)) ::lt-alus/nippulkm)))]
                               (when-not (empty? (::lt/alukset t)) t)))))

(defn- hae-tapahtumien-palvelumuodot* [osien-tiedot tapahtumat]
  (let [id-ja-osat
        (->>
          osien-tiedot
          (group-by ::lt/id)
          (map (fn [[id osat]] [id (get-in osat [0 ::lt/toiminnot])]))
          (into {}))]
    (map
      (fn [tapahtuma]
        (assoc tapahtuma ::lt/toiminnot (id-ja-osat (::lt/id tapahtuma))))
      tapahtumat)))

(defn hae-tapahtumien-palvelumuodot [db tapahtumat]
  (hae-tapahtumien-palvelumuodot*
    (specql/fetch db
                 ::lt/liikennetapahtuma
                 (set/union
                   lt/perustiedot
                   lt/toimintojen-tiedot)
                 {::lt/id (op/in (map ::lt/id tapahtumat))})
    tapahtumat))

(defn- hae-tapahtumien-kohdetiedot* [kohdetiedot tapahtumat]
  (let [id-ja-kohde
        (->>
          kohdetiedot
          (group-by ::kohde/id))]
    (map
      (fn [tapahtuma]
        (assoc tapahtuma ::lt/kohde (first (id-ja-kohde (::lt/kohde-id tapahtuma)))))
      tapahtumat)))

(defn hae-tapahtumien-kohdetiedot [db tapahtumat]
  (hae-tapahtumien-kohdetiedot*
    (specql/fetch db
                 ::kohde/kohde
                 (set/union
                   kohde/perustiedot
                   kohde/kohteenosat)
                 {::kohde/id (op/in (map ::lt/kohde-id tapahtumat))
                  ::m/poistettu? false})
    tapahtumat))

(defn- hae-tapahtumien-perustiedot* [tapahtumat {:keys [niput?]}]
  (into []
        (apply comp
               (remove nil?
                       [(when niput? vain-uittoniput)
                        ;; Jos hakuehdossa otetaan pois poistetut alukset,
                        ;; niin ei palaudu tapahtumat, joiden kaikki alukset ovat poistettuja.
                        ilman-poistettuja-aluksia]))
        tapahtumat))

(defn hae-tapahtumien-perustiedot [db {:keys [aikavali] :as tiedot}]
  (let [urakka-id (::ur/id tiedot)
        sopimus-id (::sop/id tiedot)
        kohde-id (get-in tiedot [::lt/kohde ::kohde/id])
        toimenpide (::lt/sulku-toimenpide tiedot)
        aluslaji (::lt-alus/laji tiedot)
        suunta (::lt-alus/suunta tiedot)
        [alku loppu] aikavali]
    (hae-tapahtumien-perustiedot*
      (specql/fetch db
                   ::lt/liikennetapahtuma
                   (set/union
                     lt/perustiedot
                     lt/kuittaajan-tiedot
                     lt/sopimuksen-tiedot
                     lt/alusten-tiedot
                     ;; Liikennetapahtumalle tarvitaan kohde JA kohteenosat, mutta specql
                     ;; bugittaa eikä saa palautettua kaikkea dataa. Liitetään kohdetiedot erikseen.
                     #{::lt/kohde-id})
                   (op/and
                     (when (and alku loppu)
                       {::lt/aika (op/between alku loppu)})
                     (when kohde-id
                       {::lt/kohde-id kohde-id})
                     (when toimenpide
                       {::lt/sulku-toimenpide toimenpide})

                     (op/and
                       {::m/poistettu? false
                        ::lt/urakka-id urakka-id
                        ::lt/sopimus-id sopimus-id}
                       (when (or suunta aluslaji)
                         {::lt/alukset (op/and
                                         (when suunta
                                           {::lt-alus/suunta suunta})
                                         (when aluslaji
                                           {::lt-alus/laji aluslaji}))}))))
      tiedot)))

(defn hae-liikennetapahtumat [db user tiedot]
  (hae-liikennetapahtumat*
    (->> (hae-tapahtumien-perustiedot db tiedot)
         (hae-tapahtumien-palvelumuodot db)
         (hae-tapahtumien-kohdetiedot db))
    (partial kohteet-q/hae-kohteiden-urakkatiedot db user)
    (::ur/id tiedot)))

(defn- hae-kohteen-edellinen-tapahtuma* [tulokset]
  (first
    (sort-by ::lt/aika pvm/jalkeen?
             tulokset)))

(defn- hae-kohteen-edellinen-tapahtuma [db tapahtuma]
  (let [urakka-id (::lt/urakka-id tapahtuma)
        sopimus-id (::lt/sopimus-id tapahtuma)
        kohde-id (::lt/kohde-id tapahtuma)]
    (assert (and urakka-id sopimus-id kohde-id)
            "Urakka-, sopimus-, tai kohde-id puuttuu, ei voida hakea edellistä tapahtumaa.")
    (hae-kohteen-edellinen-tapahtuma*
      (specql/fetch
        db
        ::lt/liikennetapahtuma
        (set/union
          lt/perustiedot)
        {::lt/kohde-id kohde-id
         ::lt/urakka-id urakka-id
         ::lt/sopimus-id sopimus-id}))))

;; TODO Toteuta :)
(defn- hae-kuittaamattomat-alukset [db kohde suunta]
  nil)

(defn hae-edelliset-tapahtumat [db tiedot]
  ;; TODO kun kohteille tulee järjestys, niin täällä otetaan sekin huomioon
  (let [ylos (hae-kuittaamattomat-alukset db tiedot :ylos)
        alas (hae-kuittaamattomat-alukset db tiedot :alas)
        kohde (hae-kohteen-edellinen-tapahtuma db tiedot)]
    {:ylos ylos
     :alas alas
     :kohde kohde}))

(defn- alus-kuuluu-tapahtumaan? [db alus tapahtuma]
  (some?
    (first
      (specql/fetch db
                    ::lt-alus/liikennetapahtuman-alus
                    #{::lt-alus/id}
                    {::lt-alus/liikennetapahtuma-id (::lt/id tapahtuma)
                     ::lt-alus/id (::lt-alus/id alus)}))))

(defn vaadi-alus-kuuluu-tapahtumaan! [db alus tapahtuma]
  (assert (alus-kuuluu-tapahtumaan? db alus tapahtuma) "Alus ei kuulu tapahtumaan!"))

(defn tallenna-alus-tapahtumaan! [db user alus tapahtuma]
  (let [olemassa? (id-olemassa? (::lt-alus/id alus))
        alus (assoc alus ::lt-alus/liikennetapahtuma-id (::lt/id tapahtuma))]
    (if olemassa?
      (do
        (vaadi-alus-kuuluu-tapahtumaan! db alus tapahtuma)
        (specql/update! db
                        ::lt-alus/liikennetapahtuman-alus
                        (merge
                          (if (::m/poistettu? alus)
                            {::m/poistaja-id (:id user)
                             ::m/muokattu (pvm/nyt)}

                            {::m/muokkaaja-id (:id user)
                             ::m/muokattu (pvm/nyt)})
                          alus)
                        {::lt-alus/id (::lt-alus/id alus)}))

      (specql/insert! db
                      ::lt-alus/liikennetapahtuman-alus
                      (merge
                        {::m/luoja-id (:id user)}
                        alus)))))

(defn- osa-kuuluu-tapahtumaan? [db osa tapahtuma]
  (some?
    (first
      (specql/fetch db
                    ::toiminto/liikennetapahtuman-toiminto
                    #{::toiminto/id}
                    {::toiminto/liikennetapahtuma-id (::lt/id tapahtuma)
                     ::toiminto/id (::toiminto/id osa)}))))

(defn vaadi-osa-kuuluu-tapahtumaan! [db osa tapahtuma]
  (assert (osa-kuuluu-tapahtumaan? db osa tapahtuma) "Alus ei kuulu tapahtumaan!"))

(defn tallenna-osa-tapahtumaan! [db user osa tapahtuma]
  (let [olemassa? (id-olemassa? (::toiminto/id osa))
        osa (assoc osa ::toiminto/liikennetapahtuma-id (::lt/id tapahtuma))]
    (if olemassa?
      (do
        (vaadi-osa-kuuluu-tapahtumaan! db osa tapahtuma)
        (specql/update! db
                        ::toiminto/liikennetapahtuman-toiminto
                        (merge
                          (if (::m/poistettu? osa)
                            {::m/poistaja-id (:id user)
                             ::m/muokattu (pvm/nyt)}

                            {::m/muokkaaja-id (:id user)
                             ::m/muokattu (pvm/nyt)})
                          osa)
                        {::toiminto/id (::toiminto/id osa)}))

      (specql/insert! db
                      ::toiminto/liikennetapahtuman-toiminto
                      (merge
                        {::m/luoja-id (:id user)}
                        osa)))))

(defn tapahtuma-kuuluu-urakkaan? [db tapahtuma]
  (some?
    (first
      (specql/fetch db
                    ::lt/liikennetapahtuma
                    #{::lt/id}
                    {::lt/id (::lt/id tapahtuma)
                     ::lt/urakka-id (::lt/urakka-id tapahtuma)}))))

(defn vaadi-tapahtuma-kuuluu-urakkaan! [db tapahtuma]
  (assert (tapahtuma-kuuluu-urakkaan? db tapahtuma) "Tapahtuma ei kuulu urakkaan!"))

(defn tallenna-liikennetapahtuma [db user tapahtuma]
  (jdbc/with-db-transaction [db db]
    (jdbc/execute! db ["SET CONSTRAINTS ALL DEFERRED"])
    (let [olemassa? (id-olemassa? (::lt/id tapahtuma))
          uusi-tapahtuma (if olemassa?
                           (do
                             (vaadi-tapahtuma-kuuluu-urakkaan! db tapahtuma)
                             (specql/update! db
                                             ::lt/liikennetapahtuma
                                             (merge
                                               (if (::m/poistettu? tapahtuma)
                                                 {::m/poistaja-id (:id user)
                                                  ::m/muokattu (pvm/nyt)}

                                                 {::m/muokkaaja-id (:id user)
                                                  ::m/muokattu (pvm/nyt)})
                                               (dissoc tapahtuma
                                                       ::lt/alukset
                                                       ::lt/toiminnot))
                                             {::lt/id (::lt/id tapahtuma)})
                             ;; Palautetaan päivitetty tapahtuma
                             tapahtuma)

                           (specql/insert! db
                                           ::lt/liikennetapahtuma
                                           (merge
                                             {::m/luoja-id (:id user)}
                                             (dissoc tapahtuma
                                                     ::lt/alukset
                                                     ::lt/toiminnot))))]
      (doseq [alus (::lt/alukset tapahtuma)]
        (tallenna-alus-tapahtumaan! db user alus uusi-tapahtuma))

      (doseq [osa (::lt/toiminnot tapahtuma)]
        (tallenna-osa-tapahtumaan! db user osa uusi-tapahtuma)))))