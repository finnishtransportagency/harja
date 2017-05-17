(ns harja.palvelin.palvelut.vesivaylat.vaylat
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [specql.core :refer [fetch]]
            [specql.op :as op]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.vesivaylat.vayla :as vay]))

(defn hae-vaylat [db user]
  ;; FIXME Kuka saa lukea kaikki väylät?
  (vec (fetch db ::vay/vayla vay/perustiedot {})))

(defrecord Vaylat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu http
      :hae-vaylat
      (fn [user tiedot]
        (hae-vaylat db user)))
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-vaylat)
    this))