(ns harja.kyselyt.tielupa
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [specql.core :refer [fetch update! insert! upsert!]]
    [specql.op :as op]
    [jeesql.core :refer [defqueries]]
    [clojure.set :as set]
    [harja.id :refer [id-olemassa?]]
    [harja.domain.tielupa :as tielupa]
    [harja.pvm :as pvm]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/tielupa.sql"
            {:positional? true})

(defn hae-tieluvat [db hakuehdot]
  (fetch db
         ::tielupa/tielupa
         (set/union
           harja.domain.tielupa/perustiedot
           harja.domain.tielupa/hakijan-tiedot
           harja.domain.tielupa/urakoitsijan-tiedot
           harja.domain.tielupa/liikenneohjaajan-tiedot
           harja.domain.tielupa/tienpitoviranomaisen-tiedot
           harja.domain.tielupa/johto-ja-kaapeliluvan-tiedot)
         hakuehdot))

(defn overlaps? [rivi-alku rivi-loppu alku loppu]
  (op/or {rivi-alku (op/between alku loppu)}
         {rivi-loppu (op/between alku loppu)}
         {rivi-alku (op/<= alku) rivi-loppu (op/>= loppu)}))

(defn hae-tieluvat-hakunakymaan [db hakuehdot]
  (let [hakuehdot (merge
                    (dissoc hakuehdot ::tielupa/voimassaolon-loppupvm
                            ::tielupa/voimassaolon-alkupvm)
                    #_(let [alku (::tielupa/voimassaolon-alkupvm hakuehdot)
                            loppu (::tielupa/voimassaolon-loppupvm hakuehdot)]
                        (cond (and alku loppu)
                              (op/or
                                {::tielupa/voimassaolon-alkupvm (op/between alku loppu)}
                                {::tielupa/voimassaolon-loppupvm (op/between alku loppu)}
                                {::tielupa/voimassaolon-alkupvm (op/<= alku)
                                 ::tielupa/voimassaolon-loppupvm (op/>= loppu)})
                              alku
                              {::tielupa/voimassaolon-alkupvm (op/<= alku)}

                              loppu
                              {::tielupa/voimassaolon-loppupvm (op/>= loppu)}

                              :else {})))])

  (fetch db
         ::tielupa/tielupa
         (set/union
           harja.domain.tielupa/perustiedot
           harja.domain.tielupa/hakijan-tiedot
           harja.domain.tielupa/urakoitsijan-tiedot
           harja.domain.tielupa/liikenneohjaajan-tiedot
           harja.domain.tielupa/tienpitoviranomaisen-tiedot
           harja.domain.tielupa/johto-ja-kaapeliluvan-tiedot)
         (op/and
           (when-let [nimi (::tielupa/hakija-nimi hakuehdot)]
             {::tielupa/hakija-nimi nimi})
           (when-let [tyyppi (::tielupa/tyyppi hakuehdot)]
             {::tielupa/tyyppi tyyppi})
           (when-let [tunniste (::tielupa/ulkoinen-tunniste hakuehdot)]
             {::tielupa/ulkoinen-tunniste tunniste})
           (let [alku (::tielupa/voimassaolon-alkupvm hakuehdot)
                 loppu (::tielupa/voimassaolon-loppupvm hakuehdot)]
             (cond
               (and alku loppu)
               (overlaps? ::tielupa/voimassaolon-alkupvm
                          ::tielupa/voimassaolon-loppupvm
                          alku
                          loppu)

               :else nil))
           (let [[alku loppu] (:myonnetty hakuehdot)]
             (cond
               (and alku loppu)
               {::tielupa/myontamispvm (op/between alku loppu)}

               :else nil)))))

(defn hae-tielupien-hakijat [db hakuteksti]
  (set
    (fetch db
           ::tielupa/tielupa
           #{::tielupa/hakija-nimi}
           {::tielupa/hakija-nimi (op/ilike (str hakuteksti "%"))})))

(defn hae-ulkoisella-tunnistella [db ulkoinen-id]
  (first (hae-tieluvat db {::tielupa/ulkoinen-tunniste ulkoinen-id})))

(defn onko-olemassa-ulkoisella-tunnisteella? [db ulkoinen-id]
  (and
    (number? ulkoinen-id)
    (not (empty? (hae-tieluvat db {::tielupa/ulkoinen-tunniste ulkoinen-id})))))

(defn tallenna-tielupa [db tielupa]
  (let [id (::tielupa/id tielupa)
        ulkoinen-tunniste (::tielupa/ulkoinen-tunniste tielupa)
        uusi (assoc tielupa ::muokkaustiedot/luotu (pvm/nyt))
        muokattu (assoc tielupa ::muokkaustiedot/muokattu (pvm/nyt))]
    (if (id-olemassa? id)
      (update! db ::tielupa/tielupa muokattu {::tielupa/id id})
      (if (onko-olemassa-ulkoisella-tunnisteella? db ulkoinen-tunniste)
        (update! db ::tielupa/tielupa muokattu {::tielupa/ulkoinen-tunniste ulkoinen-tunniste})
        (insert! db ::tielupa/tielupa uusi)))))


