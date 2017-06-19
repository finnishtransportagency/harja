(ns harja.kyselyt.vesivaylat.kiintiot
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! upsert!]]
            [specql.op :as op]
            [specql.rel :as rel]
            [taoensso.timbre :as log]
            [harja.id :refer [id-olemassa?]]

            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.pvm :as pvm]))

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

(defn hae-kiintiot [db tiedot]
  (let [urakka-id (::kiintio/urakka-id tiedot)
        sopimus-id (::kiintio/sopimus-id tiedot)]
    (into
      []
      (comp
        (map #(assoc % ::kiintio/toimenpiteet (into []
                                                    harja.kyselyt.vesivaylat.toimenpiteet/toimenpiteet-xf
                                                    (::kiintio/toimenpiteet %)))))
      (fetch db
            ::kiintio/kiintio
            (set/union kiintio/perustiedot
                       kiintio/kiintion-toimenpiteet
                       m/muokkauskentat)
            (op/and
              {::kiintio/urakka-id urakka-id}
              {::kiintio/sopimus-id sopimus-id}
              {::m/poistettu? false})))))

(defn tallenna-kiintiot! [db user tiedot]
  (jdbc/with-db-transaction [db db]
    (doseq [kiintio (:kiintiot tiedot)]
      (let [sopimus-id (::kiintio/sopimus-id tiedot)
            urakka-id (::kiintio/urakka-id tiedot)
            muokkaustiedot (cond
                             (::m/poistettu? kiintio)
                             {::m/poistaja-id (:id user)
                              ::m/poistettu? true
                              ::m/muokattu (pvm/nyt)}

                             (id-olemassa? (::kiintio/id kiintio))
                             {::m/muokkaaja-id (:id user)
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
                   muokkaustiedot))))

    (hae-kiintiot db tiedot)))
