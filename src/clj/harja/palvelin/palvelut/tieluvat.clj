(ns harja.palvelin.palvelut.tieluvat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]

            [harja.domain.tielupa :as tielupa]
            [harja.kyselyt.tielupa :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-tieluvat [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/tieluvat-haku user)
  (q/hae-tieluvat-hakunakymaan db tiedot))

(defn hae-tielupien-hakijat [db user hakuteksti]
  (oikeudet/vaadi-lukuoikeus oikeudet/tieluvat-haku user)
  (q/hae-tielupien-hakijat db hakuteksti))

(defrecord Tieluvat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-tieluvat
      (fn [user tiedot]
        (hae-tieluvat db user tiedot))
      {:kysely-spec ::tielupa/hae-tieluvat-kysely
       :vastaus-spec ::tielupa/hae-tieluvat-vastaus})

    (julkaise-palvelu
      http
      :hae-tielupien-hakijat
      (fn [user tiedot]
        (hae-tielupien-hakijat db user (:hakuteksti tiedot)))
      {:kysely-spec ::tielupa/hae-tielupien-hakijat-kysely
       :vastaus-spec ::tielupa/hae-tielupien-hakijat-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-tieluvat
      :hae-tielupien-hakijat)))
