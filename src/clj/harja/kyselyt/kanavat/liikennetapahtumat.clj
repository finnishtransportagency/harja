(ns harja.kyselyt.kanavat.liikennetapahtumat
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

            [harja.kyselyt.kanavat.kanavat :as kanavat-q]

            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-nippu :as lt-nippu]
            [harja.domain.kanavat.kanavan-kohde :as kohde]))

;(defqueries "harja/kyselyt/kanavat/kanavat.sql")

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

(defn- hae-liikennetapahtumat* [tapahtumat urakkatiedot-fn urakka-id]
  (->>
    tapahtumat
    (liita-kohteen-urakkatiedot urakkatiedot-fn)
    (map (partial urakat-idlla urakka-id))
    (remove (comp empty? ::kohde/urakat ::lt/kohde))))

(defn hae-liikennetapahtumat [db {:keys [kohde suunta toimenpidetyyppi
                                         aluslaji niput?] :as tiedot}]
  (let [urakka-id (::ur/id tiedot)
        kohde-id (::kohde/id kohde)
        [alku loppu] (:aikavali tiedot)]
    (hae-liikennetapahtumat*
     (specql/fetch db
                   ::lt/liikennetapahtuma
                   (set/union
                     lt/perustiedot
                     lt/kuittaajan-tiedot
                     lt/alusten-tiedot
                     (when niput?
                       lt/nippujen-tiedot)
                     lt/kohteen-tiedot)
                   (op/and
                     (when (and alku loppu)
                       {::lt/aika (op/between alku loppu)})
                     (when kohde-id
                       {::kohde/id kohde-id})
                     (when toimenpidetyyppi
                       {::lt/toimenpide toimenpidetyyppi})

                     {::m/poistettu? false
                      ::lt/kohde {::m/poistettu? false}
                      ::lt/alukset (op/and
                                     (op/or {::m/poistettu? op/null?}
                                            {::m/poistettu? false})
                                     (when suunta
                                       {::lt-alus/suunta suunta})
                                     (when aluslaji
                                       {::lt-alus/laji aluslaji}))}

                     (when niput?
                       {::lt/niput (op/and
                                     (op/or {::m/poistettu? op/null?}
                                            {::m/poistettu? false})
                                     (when suunta
                                       {::lt-nippu/suunta suunta}))})))
     (partial kanavat-q/hae-kohteiden-urakkatiedot db)
     urakka-id)))