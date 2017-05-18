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

(defn hae-vaylat [db user {:keys [hakuteksti] :as tiedot}]
  ;; FIXME Kuka saa lukea kaikki väylät?
  (vec (fetch db ::vay/vayla vay/perustiedot
              (when hakuteksti
                {::vay/nimi (op/ilike (str hakuteksti "%"))}))))

(defrecord Vaylat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-vaylat
      (fn [user tiedot]
        (hae-vaylat db user tiedot))
      {:kysely-spec ::vay/hae-vaylat-kysely
       :vastaus-spec ::vay/hae-vaylat-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-vaylat)
    this))