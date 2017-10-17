(ns harja.palvelin.palvelut.geometria-aineistot
  (:require [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.geometriaaineistot :as geometria-aineistot]
            [harja.domain.geometriaaineistot :as ga]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [taoensso.timbre :as log]
            [harja.id :refer [id-olemassa?]]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.jdbc :as jdbc]))

(defn hae-geometria-aineistot [db user]
  ;; todo: lisää oikeustarkastus
  (geometria-aineistot/hae-geometria-aineistot db))

(defn tallenna-geometria-aineistot [db user geometria-aineistot]
  ;;todo: lisää oikeustarkastus
  (jdbc/with-db-transaction [db db]
    (doseq [aineisto geometria-aineistot]
      (if (:poistettu aineisto)
        (geometria-aineistot/poista-geometria-aineisto db (::ga/id aineisto))
        (geometria-aineistot/tallenna-geometria-aineisto db aineisto))))
  (hae-geometria-aineistot db user))

(defrecord Geometria-aineistot []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-palvelu http :hae-geometria-aineistot
                      (fn [user _]
                        (hae-geometria-aineistot db user))
                      ;; todo: lisää speksi
                      ;;{:kysely-spec ::kiintio/irrota-toimenpiteet-kiintiosta-kysely
                      ;; :vastaus-spec ::kiintio/irrota-toimenpiteet-kiintiosta-vastaus}
                      )
    (julkaise-palvelu http :tallenna-geometria-aineistot
                      (fn [user geometria-aineistot]
                        (tallenna-geometria-aineistot db user geometria-aineistot))
                      ;; todo: lisää speksi
                      ;;{:kysely-spec ::kiintio/irrota-toimenpiteet-kiintiosta-kysely
                      ;; :vastaus-spec ::kiintio/irrota-toimenpiteet-kiintiosta-vastaus}
                      )
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-geometria-aineistot
                     :tallenna-geometria-aineistot)
    this))
