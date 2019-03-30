(ns harja.palvelin.palvelut.vesivaylat.vaylat
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [specql.core :refer [fetch]]
            [specql.op :as op]
            [harja.domain.muokkaustiedot :as m]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.vesivaylat.vayla :as vay]))

(defn hae-vaylat [db user {:keys [vaylatyyppi hakuteksti] :as tiedot}]
  (oikeudet/ei-oikeustarkistusta!)
  (vec (fetch db ::vay/vayla vay/perustiedot
              (op/and
                (when hakuteksti
                  {::vay/nimi (op/ilike (str hakuteksti "%"))})
                (when vaylatyyppi
                  {::vay/tyyppi vaylatyyppi})
                {::m/poistettu? false}))))

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
