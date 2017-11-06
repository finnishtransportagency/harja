(ns harja.palvelin.palvelut.kanavat.liikennetapahtumat
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kanavat.liikennetapahtumat :as q]

            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.urakka :as ur]))


(defn hae-liikennetapahtumat [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-liikenne user (::ur/id tiedot))
  (q/hae-liikennetapahtumat db (::ur/id tiedot)))

(defrecord Liikennetapahtumat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-liikennetapahtumat
      (fn [user tiedot]
        (hae-liikennetapahtumat db user tiedot))
      {:kysely-spec ::lt/hae-liikennetapahtumat-kysely
       :vastaus-spec ::lt/hae-liikennetapahtumat-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-liikennetapahtumat)
    this))

