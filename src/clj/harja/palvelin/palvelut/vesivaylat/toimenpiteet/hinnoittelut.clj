(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.hinnoittelut
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.kyselyt.vesivaylat.hinnoittelut :as q]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.urakka :as ur]))

(defn hae-hinnoittelut [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)

      (q/hae-hinnoittelut db tiedot))))

(defn luo-hinnoittelu [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (q/luo-hinnoittelu! db user tiedot))))

(defrecord Hinnoittelut []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-hinnoittelut
      (fn [user tiedot]
        (hae-hinnoittelut db user tiedot))
      {:kysely-spec ::h/hae-hinnoittelut-kysely
       :vastaus-spec ::h/hae-hinnoittelut-vastaus})

    (julkaise-palvelu
      http
      :luo-hinnoittelu
      (fn [user tiedot]
        (luo-hinnoittelu db user tiedot))
      {:kysely-spec ::h/luo-hinnoittelu-kysely})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-hinnoittelut
      :luo-hinnoittelu)
    this))
