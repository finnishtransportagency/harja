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
            [harja.domain.sopimus :as sop]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
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

(defn hae-liikennetapahtumat [db {:keys [niput? aikavali] :as tiedot}]
  (let [urakka-id (::ur/id tiedot)
        sopimus-id (::sop/id tiedot)
        kohde-id (get-in tiedot [::lt/kohde ::kohde/id])
        toimenpide (::lt/toimenpide tiedot)
        aluslaji (::lt-alus/laji tiedot)
        suunta (::lt-alus/suunta tiedot)
        [alku loppu] aikavali]
    (hae-liikennetapahtumat*
      (into []
            (comp
              (map #(update % ::lt/alukset (partial remove ::m/poistettu?))))
            (specql/fetch db
                     ::lt/liikennetapahtuma
                     (set/union
                       lt/perustiedot
                       lt/kuittaajan-tiedot
                       lt/sopimuksen-tiedot
                       lt/alusten-tiedot
                       lt/kohteen-tiedot)
                     (op/and
                       (when (and alku loppu)
                         {::lt/aika (op/between alku loppu)})
                       (when kohde-id
                         {::lt/kohde-id kohde-id})
                       (when toimenpide
                         {::lt/toimenpide toimenpide})

                       (op/and
                         {::m/poistettu? false
                         ::lt/urakka-id urakka-id
                         ::lt/sopimus-id sopimus-id
                         ::lt/kohde {::m/poistettu? false}}
                         (when (or suunta aluslaji)
                           {::lt/alukset (op/and
                                           (when suunta
                                             {::lt-alus/suunta suunta})
                                           (when aluslaji
                                             {::lt-alus/laji aluslaji}))})))))
     (partial kanavat-q/hae-kohteiden-urakkatiedot db)
     urakka-id)))

(defn vaadi-alus-kuuluu-tapahtumaan! [db alus tapahtuma]
  false)

(defn tallenna-alus-tapahtumaan! [db user alus tapahtuma]
  (vaadi-alus-kuuluu-tapahtumaan! db alus tapahtuma)
  (let [olemassa? (id-olemassa? (::lt-alus/id alus))]
    (if olemassa?
      (specql/update! db
                      ::lt-alus/liikennetapahtuman-alus
                      (merge
                        (if (::m/poistettu? alus)
                          {::m/poistaja-id (:id user)
                           ::m/muokattu (pvm/nyt)}

                          {::m/muokkaaja-id (:id user)
                           ::m/muokattu (pvm/nyt)})
                        alus)
                      {::lt-alus/id (::lt-alus/id alus)})

      (specql/insert! db
                      ::lt-alus/lt-alus
                      (merge
                        {::m/luoja-id (:id user)}
                        alus)))))

(defn tallenna-liikennetapahtuma [db user tapahtuma]
  (jdbc/with-db-transaction [db db]
    (let [olemassa? (id-olemassa? (::lt/id tapahtuma))
          uusi-tapahtuma (if olemassa?
                           (do
                             (specql/update! db
                                             ::lt/liikennetapahtuma
                                             (merge
                                               (if (::m/poistettu? tapahtuma)
                                                 {::m/poistaja-id (:id user)
                                                  ::m/muokattu (pvm/nyt)}

                                                 {::m/muokkaaja-id (:id user)
                                                  ::m/muokattu (pvm/nyt)})
                                               (dissoc tapahtuma ::lt/alukset))
                                             {::lt/id (::lt/id tapahtuma)})
                             ;; Palautetaan päivitetty tapahtuma
                             tapahtuma)

                           (specql/insert! db
                                           ::lt/liikennetapahtuma
                                           (merge
                                             {::m/luoja-id (:id user)}
                                             (dissoc tapahtuma ::lt/alukset))))]
      (doseq [alus (::lt/alukset tapahtuma)]
        (tallenna-alus-tapahtumaan! db user alus uusi-tapahtuma)))))