(ns harja.palvelin.palvelut.muokkauslukko
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.domain.roolit :as roolit]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.domain.paikkaus.minipot :as minipot]

            [harja.kyselyt.muokkauslukko :as q]
            [harja.kyselyt.materiaalit :as materiaalit-q]

            [harja.palvelin.palvelut.materiaalit :as materiaalipalvelut]
            [cheshire.core :as cheshire]
            [harja.domain.skeema :as skeema]
            [clj-time.format :as format]
            [clj-time.coerce :as coerce]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]))

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
          (q/lukitse<! db id kayttaja))
        (do
          (log/debug "Ei voida lukita " id " koska on jo lukittu!")
          (q/lukitse<! db id kayttaja))))))

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