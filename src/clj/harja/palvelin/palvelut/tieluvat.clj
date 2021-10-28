(ns harja.palvelin.palvelut.tieluvat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]

            [harja.domain.tielupa :as tielupa]
            [harja.kyselyt.tielupa-kyselyt :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-tielupa [db user {:keys [id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/tieluvat-haku user)
  (q/hae-tielupa db id))

(defn hae-tieluvat [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/tieluvat-haku user)
  (q/hae-tieluvat-hakunakymaan db tiedot))

(defn hae-tielupien-hakijat [db user hakuteksti]
  (oikeudet/vaadi-lukuoikeus oikeudet/tieluvat-haku user)
  (q/hae-tielupien-hakijat db hakuteksti))

(defn hae-alueurakat
  "Haetaan kaikki alueurakat elynumeron ja nimen perusteella sortattuna"
  [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/tieluvat-haku user)
  (q/hae-alueurakat db))

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

    (julkaise-palvelu http :hae-tielupa
      (fn [user tiedot]
        (hae-tielupa db user tiedot)))

    (julkaise-palvelu http :hae-alueurakat
      (fn [user tiedot]
        (hae-alueurakat db user)))

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
      :hae-tielupa
      :hae-alueurakat
      :hae-tielupien-hakijat)
    this))
