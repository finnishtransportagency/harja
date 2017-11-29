(ns harja.kyselyt.tielupa
  (:require
    [harja.kyselyt.specql-db :refer [define-tables]]
    [specql.core :refer [fetch update! insert! upsert!]]
    [clojure.set :as set]
    [harja.domain.tielupa :as tielupa]))

(defn hae-tieluvat [db hakuehdot]
  (fetch db
         ::tielupa/tielupa
         (set/union
           harja.domain.tielupa/perustiedot
           harja.domain.tielupa/hakijan-tiedot
           harja.domain.tielupa/urakoitsijan-tiedot
           harja.domain.tielupa/liikenneohjaajan-tiedot
           harja.domain.tielupa/tienpitoviranomaisen-tiedot)
         hakuehdot))

(defn onko-olemassa-ulkoisella-tunnisteella? [db ulkoinen-id]
  (and
    (number? ulkoinen-id)
    (not (empty? (hae-tieluvat db {::tielupa/ulkoinen-tunniste ulkoinen-id})))))

(defn tallenna-tielupa [db tielupa]
  (insert! db
           ::tielupa/tielupa
           tielupa))


