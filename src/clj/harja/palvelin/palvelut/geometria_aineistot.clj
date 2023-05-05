(ns harja.palvelin.palvelut.geometria-aineistot
  (:require [harja.kyselyt.geometriaaineistot :as geometria-aineistot]
            [harja.domain.geometriaaineistot :as ga]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [clojure.java.jdbc :as jdbc]))

(defn hae-geometria-aineistot [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-jarjestelmaasetukset user)
  (geometria-aineistot/hae-geometria-aineistot db))


(defn hae-geometriapaivitykset [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-jarjestelmaasetukset user)
  (geometria-aineistot/hae-geometriapaivitykset db))

(defn tallenna-geometria-aineistot [db user geometria-aineistot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-jarjestelmaasetukset user)
  (jdbc/with-db-transaction [db db]
    (doseq [aineisto geometria-aineistot]
      (if (:poistettu aineisto)
        (geometria-aineistot/poista-geometria-aineisto db (::ga/id aineisto))
        (geometria-aineistot/tallenna-geometria-aineisto db aineisto))))
  (hae-geometria-aineistot db user))

(defrecord Geometria-aineistot []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-palvelu http :hae-geometriapaivitykset
      (fn [user _]
        (hae-geometriapaivitykset db user)))
    (julkaise-palvelu http :hae-geometria-aineistot
                      (fn [user _]
                        (hae-geometria-aineistot db user)))
    (julkaise-palvelu http :tallenna-geometria-aineistot
                      (fn [user geometria-aineistot]
                        (tallenna-geometria-aineistot db user geometria-aineistot)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :hae-geometriapaivitykset
      :hae-geometria-aineistot
      :tallenna-geometria-aineistot)
    this))
