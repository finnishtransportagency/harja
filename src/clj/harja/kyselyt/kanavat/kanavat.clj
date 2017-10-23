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

(defn- hae-kohteiden-urakkatiedot [db kohteet]
  (let [kohde-ja-urakat (->> (specql/fetch db
                                           ::kohde/kohde<->urakka
                                           (set/union
                                             kohde/kohteen-urakkatiedot
                                             #{::kohde/kohde-id})
                                           {::kohde/kohde-id (op/in (into #{} (map ::kohde/id kohteet)))})
                             (group-by ::kohde/kohde-id)
                             (map (fn [[kohde-id urakat]] [kohde-id (map ::kohde/linkin-urakka urakat)]))
                             (into {}))]
    (map
      (fn [kohde]
        (assoc kohde ::kohde/urakat (kohde-ja-urakat (::kohde/id kohde))))
      kohteet)))

(defn hae-kanavat-ja-kohteet [db]
  (into []
        (comp
          (map #(update % ::kanava/kohteet (partial hae-kohteiden-urakkatiedot db))))
        (specql/fetch db
                      ::kanava/kanava
                      (set/union
                        kanava/perustiedot
                        kanava/kohteet)
                      {::kanava/kohteet (op/or {::m/poistettu? op/null?}
                                               {::m/poistettu? false})})))