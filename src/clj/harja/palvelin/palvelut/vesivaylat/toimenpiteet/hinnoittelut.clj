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
            [harja.domain.urakka :as ur]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.kyselyt.vesivaylat.toimenpiteet :as to-q]))

(defn hae-hinnoittelut [db user urakka-id]
  (when (ominaisuus-kaytossa? :vesivayla)
    (assert urakka-id "Urakka-id puuttuu!")
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
    (q/hae-hinnoittelut db urakka-id)))

(defn luo-hinnoittelu! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/luo-hinnoittelu! db user tiedot)))))

(defn liita-toimenpiteet-hinnoitteluun! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (to-q/vaadi-toimenpiteet-kuuluvat-urakkaan db #{(::to/id tiedot)} urakka-id)
      (q/vaadi-hinnoittelut-kuuluvat-urakkaan db #{(::h/id tiedot)} urakka-id)
      (q/poista-toimenpiteet-hintaryhmistaan! db user (::to/idt tiedot))
      (jdbc/with-db-transaction [db db]
        (q/liita-toimenpiteet-hinnoitteluun! db user (::to/idt tiedot) (::h/id tiedot))))))

(defn tallenna-hintaryhmalle-hinta! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (q/vaadi-hinnoittelut-kuuluvat-urakkaan db #{(::h/id tiedot)} urakka-id)
      (q/vaadi-hinnat-kuuluvat-hinnoitteluun db (set (map ::hinta/id (::h/hintaelementit tiedot)))
                                             (::h/id tiedot))
      (jdbc/with-db-transaction [db db]
        (q/tallenna-hintaryhmalle-hinta! db user
                                         (::h/id tiedot)
                                         (::h/hintaelementit tiedot))
        (hae-hinnoittelut db user urakka-id)))))

(defn tallenna-toimenpiteelle-hinta! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (to-q/vaadi-toimenpiteet-kuuluvat-urakkaan db #{(::to/id tiedot)} urakka-id)
      ;; Tarkistetaan, että olemassa olevat hinnat kuuluvat annettuun toimenpiteeseen
      (let [hinta-idt (set (keep ::hinta/id (::h/hintaelementit tiedot)))]
        (when-not (empty? hinta-idt)
          (q/vaadi-hinnat-kuuluvat-toimenpiteeseen db hinta-idt (::to/id tiedot))))
      (jdbc/with-db-transaction [db db]
        (q/tallenna-toimenpiteelle-hinta! db user
                                          (::to/id tiedot)
                                          (::h/hintaelementit tiedot)
                                          urakka-id)
        (q/hae-toimenpiteen-oma-hinnoittelu db (::to/id tiedot))))))

(defrecord Hinnoittelut []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-hinnoittelut
      (fn [user tiedot]
        (let [urakka-id (::ur/id tiedot)]
          (hae-hinnoittelut db user urakka-id)))
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
      :tallenna-hintaryhmalle-hinta
      (fn [user tiedot]
        (tallenna-hintaryhmalle-hinta! db user tiedot))
      {:kysely-spec ::h/tallenna-hintaryhmalle-hinta-kysely
       :vastaus-spec ::h/tallenna-hintaryhmalle-hinta-vastaus})

    (julkaise-palvelu
      http
      :tallenna-toimenpiteelle-hinta
      (fn [user tiedot]
        (tallenna-toimenpiteelle-hinta! db user tiedot))
      {:kysely-spec ::h/tallenna-toimenpiteelle-hinta-kysely
       :vastaus-spec ::h/tallenna-toimenpiteelle-hinta-vastaus})
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-hinnoittelut
      :luo-hinnoittelu
      :liita-toimenpiteet-hinnoitteluun
      :tallenna-hintaryhmalle-hinta
      :tallenna-toimenpiteelle-hinta)
    this))
