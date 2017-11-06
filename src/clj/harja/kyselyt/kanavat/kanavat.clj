(ns harja.kyselyt.kanavat.kanavat
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.future :refer :all]
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
            [harja.domain.kanavat.kanava :as kanava]
            [harja.domain.kanavat.kanavan-kohde :as kohde]))

;(defqueries "harja/kyselyt/kanavat/kanavat.sql")

(defn- hae-kohteiden-urakkatiedot* [kohteet linkit]
  (let [kohde-ja-urakat (->> linkit
                             (group-by ::kohde/kohde-id)
                             (map (fn [[kohde-id urakat]] [kohde-id (map ::kohde/linkin-urakka urakat)]))
                             (into {}))]
    (map
      (fn [kohde]
        (assoc kohde ::kohde/urakat (kohde-ja-urakat (::kohde/id kohde))))
      kohteet)))

(defn hae-kohteiden-urakkatiedot [db kohteet]
  (hae-kohteiden-urakkatiedot* kohteet
                               (specql/fetch db
                                             ::kohde/kohde<->urakka
                                             (set/union
                                               kohde/kohteen-urakkatiedot
                                               #{::kohde/kohde-id})
                                             {::kohde/kohde-id (op/in (into #{} (map ::kohde/id kohteet)))
                                              ::m/poistettu? false})))

(defn- hae-kanavat-ja-kohteet* [kanavat kohteen-haku]
  (into []
        (comp
          (map #(update % ::kanava/kohteet kohteen-haku)))
        kanavat))

(defn hae-kanavat-ja-kohteet [db]
  (hae-kanavat-ja-kohteet*
    (specql/fetch db
                  ::kanava/kanava
                  (set/union
                    kanava/perustiedot
                    kanava/kohteet)
                  {::kanava/kohteet (op/or {::m/poistettu? op/null?}
                                           {::m/poistettu? false})})
    (partial hae-kohteiden-urakkatiedot db)))

(defn lisaa-kanavalle-kohteet! [db user kohteet]
  (jdbc/with-db-transaction [db db]
    (doseq [kohde kohteet]
      (if (id-olemassa? (::kohde/id kohde))
        (specql/update!
          db
          ::kohde/kohde
          (merge
            (if (::m/poistettu? kohde)
              {::m/poistaja-id (:id user)
               ::m/muokattu (pvm/nyt)}

              {::m/muokkaaja-id (:id user)
               ::m/muokattu (pvm/nyt)})
            kohde)
          {::kohde/id (::kohde/id kohde)})

        (specql/insert!
          db
          ::kohde/kohde
          (merge
            {::m/luoja-id (:id user)}
            (dissoc kohde ::kohde/id)))))))

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
                             ::m/muokattu (pvm/nyt)
                             }

                            {::m/muokkaaja-id (:id user)
                             ::m/muokattu (pvm/nyt)
                             })
                          {::m/poistettu? poistettu?})
                        {::kohde/kohde-id kohde-id
                         ::kohde/urakka-id urakka-id})

        (specql/insert! db
                        ::kohde/kohde<->urakka
                        (merge
                          {::m/luoja-id (:id user)}
                          {::kohde/kohde-id kohde-id
                           ::kohde/urakka-id urakka-id}))))))

(defn merkitse-kohde-poistetuksi! [db user kohde-id]
  (specql/update! db
                  ::kohde/kohde
                  {::m/poistettu? true
                   ::m/poistaja-id (:id user)
                   ::m/muokattu (pvm/nyt)}
                  {::kohde/id kohde-id}))
