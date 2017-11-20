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

(defn tallenna-liikennetapahtuma [db user tiedot]
  (assert (::lt/urakka-id tiedot) "Urakka id puuttuu, ei voi tallentaa liikennetapahtumaa!")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kanavat-liikenne user (::lt/urakka-id tiedot))
  (q/tallenna-liikennetapahtuma db user (dissoc tiedot :hakuparametrit))
  (q/hae-liikennetapahtumat db (:hakuparametrit tiedot)))

(defn hae-liikennetapahtumat [db user tiedot]
  (assert (::ur/id tiedot) "Urakka id puuttuu, ei voida hakea liikennetapahtumia!")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-liikenne user (::ur/id tiedot))
  (q/hae-liikennetapahtumat db tiedot))

(defn hae-edelliset-tapahtumat [db user tiedot]
  (assert (::lt/urakka-id tiedot) "Urakka id puuttuu, ei voida hakea edellisiä tapahtumia!")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-liikenne user (::lt/urakka-id tiedot))
  (q/hae-edelliset-tapahtumat db tiedot))

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
    (julkaise-palvelu
      http
      :hae-edelliset-tapahtumat
      (fn [user tiedot]
        (hae-edelliset-tapahtumat db user tiedot))
      {:kysely-spec ::lt/hae-edelliset-tapahtumat-kysely
       :vastaus-spec ::lt/hae-edelliset-tapahtumat-vastaus})
    (julkaise-palvelu
      http
      :tallenna-liikennetapahtuma
      (fn [user tiedot]
        (tallenna-liikennetapahtuma db user tiedot))
      {:kysely-spec ::lt/tallenna-liikennetapahtuma-kysely
       :vastaus-spec ::lt/tallenna-liikennetapahtuma-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-liikennetapahtumat
      :tallenna-liikennetapahtuma)
    this))

