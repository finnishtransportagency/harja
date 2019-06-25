(ns harja.kyselyt.vesivaylat.kiintiot
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            
            [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! upsert!]]
            [specql.op :as op]
            [specql.rel :as rel]
            [taoensso.timbre :as log]
            [harja.id :refer [id-olemassa?]]

            [harja.kyselyt.vesivaylat.toimenpiteet :as to-q]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.pvm :as pvm]
            [clj-time.core :as t]))

(defn kiintiot-kuuluvat-urakkaan? [db kiintio-idt urakka-id]
  (->>
    (fetch db
           ::kiintio/kiintio
           #{::kiintio/urakka-id}
           {::kiintio/id (op/in kiintio-idt)})
    (keep ::kiintio/urakka-id)
    (every? (partial = urakka-id))))

(defn vaadi-kiintiot-kuuluvat-urakkaan! [db kiintio-idt urakka-id]
  (when-not (kiintiot-kuuluvat-urakkaan? db kiintio-idt urakka-id)
    (throw (SecurityException. (str "Kaikki kiintiöt " kiintio-idt " eivät kuulu urakkaan " urakka-id)))))

(defn hae-kiintiot [db {:keys [toimenpiteet?] :as tiedot}]
  (let [urakka-id (::kiintio/urakka-id tiedot)
        sopimus-id (::kiintio/sopimus-id tiedot)]
    (into
      []
      (map #(if toimenpiteet?
              (assoc % ::kiintio/toimenpiteet (into []
                                                    to-q/toimenpiteet-xf
                                                    (::kiintio/toimenpiteet %)))
              %))
      (fetch db
             ::kiintio/kiintio
             (set/union kiintio/perustiedot
                        (when toimenpiteet?
                          kiintio/kiintion-toimenpiteet))
             (op/and
               {::kiintio/urakka-id urakka-id}
               {::kiintio/sopimus-id sopimus-id}
               {::m/poistettu? false})))))

(defn tallenna-kiintiot! [db user tiedot]
  (doseq [kiintio (::kiintio/tallennettavat-kiintiot tiedot)]
    (let [sopimus-id (::kiintio/sopimus-id tiedot)
          urakka-id (::kiintio/urakka-id tiedot)
          kiintio (if (id-olemassa? (::kiintio/id kiintio))
                    kiintio
                    (dissoc kiintio ::kiintio/id))
          muokkaustiedot (cond
                           (::m/poistettu? kiintio)
                           {::m/luoja-id (:id user)
                            ::m/poistaja-id (:id user)
                            ::m/poistettu? true
                            ::m/muokattu (pvm/nyt)}

                           (id-olemassa? (::kiintio/id kiintio))
                           {::m/luoja-id (:id user)
                            ::m/muokkaaja-id (:id user)
                            ::m/muokattu (pvm/nyt)}

                           :default
                           {::m/luoja-id (:id user)
                            ::m/luotu (pvm/nyt)})]
      (upsert! db
               ::kiintio/kiintio
               (merge
                 (-> kiintio
                     (assoc ::kiintio/sopimus-id sopimus-id
                            ::kiintio/urakka-id urakka-id)
                     (dissoc ::kiintio/toimenpiteet))
                 muokkaustiedot)))))

(defn liita-toimenpiteet-kiintioon [db user tiedot]
  (update! db ::to/reimari-toimenpide
           {::to/kiintio-id (::kiintio/id tiedot)
            ::m/muokattu (pvm/nyt)
            ::m/muokkaaja-id (:id user)}
           {::to/id (op/in (::to/idt tiedot))}))

(defn irrota-toimenpiteet-kiintiosta [db user tiedot]
  (update! db ::to/reimari-toimenpide
           {::to/kiintio-id nil
            ::m/muokattu (pvm/nyt)
            ::m/muokkaaja-id (:id user)}
           {::to/id (op/in (::to/idt tiedot))}))
