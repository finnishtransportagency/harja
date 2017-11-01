(ns harja.palvelin.palvelut.kanavat.hairiotilanteet
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.kanavat.hairiotilanne :as hairio]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kanavat.kanavat :as q]
            [specql.core :as specql]
            [harja.domain.kanavat.kanava :as kan]
            [clojure.set :as set]))

(defn hae-hairiotilanteet [db user tiedot]
  ;; TODO Oikeustarkistus + testi
  ;; TODO Testi haulle
  (let [urakka-id (::hairio/urakka-id tiedot)
        sopimus-id (::hairio/sopimus-id tiedot)]
    (specql/fetch db
                  ::hairio/hairiotilanne
                  (set/union
                    hairio/perustiedot+muokkaustiedot
                    hairio/viittaukset)
                  {::hairio/urakka-id urakka-id
                   ::hairio/sopimus-id sopimus-id})))

(defrecord Hairiotilanteet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-hairiotilanteet
      (fn [user tiedot]
        (hae-hairiotilanteet db user tiedot))
      {:kysely-spec ::hairio/hae-hairiotilanteet-kysely
       :vastaus-spec ::hairio/hae-hairiotilanteet-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-hairiotilanteet)
    this))
