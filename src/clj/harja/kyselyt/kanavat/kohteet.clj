(ns harja.kyselyt.kanavat.kohteet
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

            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.kohdekokonaisuus :as kok]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.domain.kanavat.kanavan-huoltokohde :as huoltokohde]
            [harja.domain.oikeudet :as oikeudet]))


(defn- hae-kohteiden-urakkatiedot* [user kohteet linkit]
  (let [kohde-ja-urakat (->> linkit
                             (group-by ::kohde/kohde-id)
                             (map (fn [[kohde-id urakat]]
                                    [kohde-id (filter
                                                (fn [urakka]
                                                  (oikeudet/voi-lukea?
                                                    oikeudet/urakat-kanavat-kanavakohteet
                                                    (::ur/id urakka)
                                                    user))
                                                (map ::kohde/linkin-urakka urakat))]))
                             (into {}))]
    (map
      (fn [kohde]
        (assoc kohde ::kohde/urakat (kohde-ja-urakat (::kohde/id kohde))))
      kohteet)))

(defn hae-kohteiden-urakkatiedot
  ([db user kohteet]
   (hae-kohteiden-urakkatiedot db user nil kohteet))
  ([db user urakka-id kohteet]
   (hae-kohteiden-urakkatiedot* user
                                kohteet
                                (specql/fetch db
                                              ::kohde/kohde<->urakka
                                              (set/union
                                                kohde/kohteen-urakkatiedot
                                                #{::kohde/kohde-id})
                                              (op/and
                                                {::kohde/kohde-id (op/in (into #{} (map ::kohde/id kohteet)))
                                                 ::m/poistettu? false}
                                                (when urakka-id
                                                  {::kohde/urakka-id urakka-id}))))))

(defn- hae-kokonaisuudet-ja-kohteet* [kokonaisuudet kohteen-haku]
  (into []
        (comp
          (map #(update % ::kok/kohteet kohteen-haku)))
        kokonaisuudet))

(defn hae-kohteen-urakat [db kohde]
  (specql/fetch db
                ::kohde/kohde<->urakka
                #{::kohde/urakka-id}
                {::kohde/kohde-id kohde
                 ::m/poistettu? false}))

(defn hae-kohteenosat [db]
  (specql/fetch db
                ::osa/kohteenosa
                (set/union osa/perustiedot
                           osa/kohteen-tiedot)
                {::m/poistettu? false}))

(defn hae-kokonaisuudet-ja-kohteet [db user]
  (sort-by :harja.domain.kanavat.kohdekokonaisuus/id
  (hae-kokonaisuudet-ja-kohteet*
    (specql/fetch db
                  ::kok/kohdekokonaisuus
                  (set/union
                    kok/perustiedot
                    kok/kohteet)
                  {::m/poistettu? false
                   ::kok/kohteet (op/or {::m/poistettu? op/null?}
                                        {::m/poistettu? false})})
    (partial hae-kohteiden-urakkatiedot db user))))

(defn hae-urakan-kohteet [db user urakka-id]
  (->>
    (sort-by :harja.domain.kanavat.kohde/jarjestys (specql/fetch db
                  ::kohde/kohde
                  (set/union
                    kohde/perustiedot
                    kohde/kohteen-kohdekokonaisuus
                    kohde/kohteenosat)
                  {::m/poistettu? false}))
    (hae-kohteiden-urakkatiedot db user urakka-id)
    (remove (comp empty? ::kohde/urakat))))

(defn hae-urakan-kohteet-mukaanlukien-poistetut [db user urakka-id]
  (->>
    (sort-by :harja.domain.kanavat.kohde/jarjestys (specql/fetch db
                  ::kohde/kohde
                  (set/union
                    kohde/perustiedot)
                  {}))
    (hae-kohteiden-urakkatiedot db user urakka-id)
    (remove (comp empty? ::kohde/urakat))))

(defn liita-kohde-urakkaan! [db user kohde-id urakka-id poistettu?]
  (jdbc/with-db-transaction [db db]
    (let [olemassa? (-> (specql/fetch
                          db
                          ::kohde/kohde<->urakka
                          #{::kohde/kohde-id}
                          {::kohde/kohde-id kohde-id
                           ::kohde/urakka-id urakka-id})
                        first
                        ::kohde/kohde-id
                        some?
                        boolean)]
      (if olemassa?
        (specql/update! db
                        ::kohde/kohde<->urakka
                        (merge
                          (if poistettu?
                            {::m/poistaja-id (:id user)
                             ::m/muokattu (pvm/nyt)}

                            {::m/muokkaaja-id (:id user)
                             ::m/muokattu (pvm/nyt)})
                          {::m/poistettu? poistettu?})
                        {::kohde/kohde-id kohde-id
                         ::kohde/urakka-id urakka-id})

        (specql/insert! db
                        ::kohde/kohde<->urakka
                        (merge
                          {::m/luoja-id (:id user)}
                          {::kohde/kohde-id kohde-id
                           ::kohde/urakka-id urakka-id}))))))

(defn tallenna-kohdekokonaisuudet! [db user kokonaisuudet]
  (jdbc/with-db-transaction [db db]
    (doseq [kokonaisuus kokonaisuudet]
      (if (id-olemassa? (::kok/id kokonaisuus))
        (specql/update! db
                        ::kok/kohdekokonaisuus
                        (merge
                          (if (::m/poistettu? kokonaisuus)
                            {::m/poistaja-id (:id user)
                             ::m/muokattu (pvm/nyt)}

                            {::m/muokkaaja-id (:id user)
                             ::m/muokattu (pvm/nyt)})
                          kokonaisuus)
                        {::kok/id (::kok/id kokonaisuus)})

        (specql/insert! db
                        ::kok/kohdekokonaisuus
                        (merge {::m/luoja-id (:id user)}
                               (dissoc kokonaisuus ::kok/id)))))))

(defn merkitse-kohde-poistetuksi! [db user kohde-id]
  (specql/update! db
                  ::kohde/kohde
                  {::m/poistettu? true
                   ::m/poistaja-id (:id user)
                   ::m/muokattu (pvm/nyt)}
                  {::kohde/id kohde-id}))

(defn hae-huoltokohteet [db]
  (sort-by ::huoltokohde/nimi (specql/fetch db ::huoltokohde/huoltokohde huoltokohde/perustiedot {})))


(defn lisaa-kohteelle-osa! [db user osa kohde]
  (if (id-olemassa? (::osa/id osa))
    (let [osa (if (::m/poistettu? osa)
                ;; Poistaminen tarkoittaa kohteesta irrottamista
                (-> osa
                    (dissoc ::m/poistettu?)
                    (assoc ::osa/kohde-id nil))
                (assoc osa ::osa/kohde-id (::kohde/id kohde)))]

      (specql/update!
       db
       ::osa/kohteenosa
       (merge
         {::m/muokkaaja-id (:id user)
          ::m/muokattu (pvm/nyt)}
         osa)
       {::osa/id (::osa/id osa)}))

    (specql/insert!
      db
      ::osa/kohteenosa
      (merge
        {::m/luoja-id (:id user)}
        (-> osa
            (dissoc ::osa/id)
            (assoc ::osa/kohde-id (::kohde/id kohde)))))))

(defn tallenna-kohde! [db user kohde]
  (jdbc/with-db-transaction [db db]
    (let [osat (::kohde/kohteenosat kohde)
          kohde (if (id-olemassa? (::kohde/id kohde))
                  (do
                    (specql/update!
                      db
                      ::kohde/kohde
                      (merge
                        (if (::m/poistettu? kohde)
                          {::m/poistaja-id (:id user)
                           ::m/muokattu (pvm/nyt)}

                          {::m/muokkaaja-id (:id user)
                           ::m/muokattu (pvm/nyt)})
                        (dissoc kohde ::kohde/kohteenosat))
                      {::kohde/id (::kohde/id kohde)})
                    kohde)

                  (specql/insert!
                    db
                    ::kohde/kohde
                    (merge
                      {::m/luoja-id (:id user)}
                      (dissoc kohde ::kohde/id ::kohde/kohteenosat))))]
      (doseq [osa osat]
        (lisaa-kohteelle-osa! db user osa kohde)))))


