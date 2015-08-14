(ns harja.palvelin.palvelut.muokkauslukko
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.muokkauslukko :as q]))

(defn hae-lukko-idlla [db user {:keys [id]}]
  (log/debug "Haetaan lukko id:llä " id)
  (q/hae-lukko-idlla db id))

(defn lukitse [db user {:keys [id kayttaja]}]
  (jdbc/with-db-transaction [c db]
    (log/debug "Yritetään lukita " id)
    (let [lukko (q/hae-lukko-idlla db id)]
      (if (not lukko)
        (do
          (log/debug "Lukitaan " id)
          (let [vastaus (q/lukitse<! db id kayttaja)]
            {:lukko-id (:id vastaus)}))
        (do
          (log/debug "Ei voida lukita " id " koska on jo lukittu!")
          {:lukko-id nil})))))

(defn virkista-lukko [db user {:keys [id]}]
  (log/debug "Virkistetään lukko")
  (q/virkista-lukko! db id user))

(defn vapauta-lukko [db user {:keys [id]}]
  (log/debug "Vapautetaan lukko")
  (q/vapauta-lukko! db id user))

(defrecord Muokkauslukko []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :hae-lukko-idlla
                        (fn [user tiedot]
                          (hae-lukko-idlla db user tiedot)))
      (julkaise-palvelu http :lukitse
                        (fn [user tiedot]
                          (lukitse db user tiedot)))
      (julkaise-palvelu http :vapauta-lukko
                        (fn [user tiedot]
                          (vapauta-lukko db user tiedot)))
      (julkaise-palvelu http :virkista-lukko
                        (fn [user tiedot]
                          (virkista-lukko db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-lukko-idlla
      :lukitse
      :vapauta-lukko
      :virkista-lukko)
    this))