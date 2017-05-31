(ns harja.palvelin.palvelut.vesivaylat.toimenpiteet.hinnoittelut
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.kyselyt.vesivaylat.hinnoittelut :as q]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.urakka :as ur]))

(defn hae-hinnoittelut [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)

      (q/hae-hinnoittelut db tiedot))))

(defn luo-hinnoittelu! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (q/luo-hinnoittelu! db user tiedot))))

(defn liita-toimenpiteet-hinnoitteluun! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)

      (q/poista-toimenpiteet-hintaryhmistaan! db user (::to/idt tiedot) urakka-id)
      (q/liita-toimenpiteet-hinnoitteluun! db user (::to/idt tiedot) (::h/id tiedot) urakka-id))))

(defn anna-hintaryhmalle-hinta! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)

      (q/anna-hintaryhmalle-hinta! db user
                                   (::h/hinnoittelu-id tiedot)
                                   (::h/hinnat tiedot)
                                   urakka-id))))

(defn anna-toimenpiteelle-hinta! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (q/anna-toimenpiteelle-hinta! db user
                                    (::to/toimenpide-id tiedot)
                                    (::h/hinnat tiedot)
                                    urakka-id))))

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
        (luo-hinnoittelu! db user tiedot))
      {:kysely-spec ::h/luo-hinnoittelu-kysely
       :vastaus-spec ::h/luo-hinnoittelu-vastaus})

    (julkaise-palvelu
      http
      :liita-toimenpiteet-hinnoitteluun
      (fn [user tiedot]
        (liita-toimenpiteet-hinnoitteluun! db user tiedot))
      {:kysely-spec ::h/liita-toimenpiteet-hinnotteluun-kysely})

    (julkaise-palvelu
      http
      :anna-hintaryhmalle-hinta
      (fn [user tiedot]
        (anna-hintaryhmalle-hinta! db user tiedot))
      {:kysely-spec ::h/anna-hintaryhmalle-hinta-kysely
       :vastaus-spec ::h/anna-hintaryhmalle-hinta-vastaus})

    (julkaise-palvelu
      http
      :anna-toimenpiteelle-hinta
      (fn [user tiedot]
        (anna-toimenpiteelle-hinta! db user tiedot))
      {:kysely-spec ::h/anna-toimenpiteelle-hinta-kysely
       :vastaus-spec ::h/anna-toimenpiteelle-hinta-vastaus})

    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-hinnoittelut
      :luo-hinnoittelu)
    this))
