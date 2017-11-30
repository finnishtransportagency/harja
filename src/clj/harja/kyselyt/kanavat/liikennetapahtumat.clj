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

            [harja.kyselyt.kanavat.kohteet :as kohteet-q]

            [harja.domain.urakka :as ur]
            [harja.domain.sopimus :as sop]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
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

(defn- hae-liikennetapahtumat* [tapahtumat urakkatiedot-fn urakka-id]
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

(defn hae-liikennetapahtumat [db user {:keys [niput? aikavali] :as tiedot}]
  (let [urakka-id (::ur/id tiedot)
        sopimus-id (::sop/id tiedot)
        kohde-id (get-in tiedot [::lt/kohde ::kohde/id])
        toimenpide (::lt/sulku-toimenpide tiedot)
        aluslaji (::lt-alus/laji tiedot)
        suunta (::lt-alus/suunta tiedot)
        [alku loppu] aikavali]
    (hae-liikennetapahtumat*
      (into []
            (apply comp
                   (remove nil?
                           [(when niput? vain-uittoniput)
                            ;; Jos hakuehdossa otetaan pois poistetut alukset,
                            ;; niin ei palaudu tapahtumat, joiden kaikki alukset ovat poistettuja.
                            ilman-poistettuja-aluksia]))
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
                              {::lt/sulku-toimenpide toimenpide})

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
      (partial kohteet-q/hae-kohteiden-urakkatiedot db user)
      urakka-id)))

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