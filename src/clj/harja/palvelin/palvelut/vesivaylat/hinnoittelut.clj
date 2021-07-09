(ns harja.palvelin.palvelut.vesivaylat.hinnoittelut
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.kyselyt.vesivaylat.hinnoittelut :as q]
            [harja.kyselyt.vesivaylat.toimenpiteet :as to-q]
            [harja.tyokalut.tietoturva :as tietoturva]
            [harja.id :as id]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.urakka :as ur]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.tyo :as tyo]
            [harja.domain.vesivaylat.kommentti :as kommentti]))

(defn hae-hintaryhmat [db user urakka-id]
  (when (ominaisuus-kaytossa? :vesivayla)
    (assert urakka-id "Urakka-id puuttuu!")
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
    (q/hae-hintaryhmat db urakka-id)))

(defn luo-hinnoittelu! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-oikeus "tilausten-muokkaus" oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
      (jdbc/with-db-transaction [db db]
        (q/luo-hinnoittelu! db user tiedot)))))

(defn poista-tyhjat-hinnoittelut! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::h/urakka-id tiedot)
          hinnoittelu-idt (::h/idt tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-oikeus "tilausten-muokkaus" oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
      (q/vaadi-hinnoittelut-kuuluvat-urakkaan db hinnoittelu-idt urakka-id)
      (doseq [hinnoittelu-id hinnoittelu-idt]
        (q/vaadi-hinnoitteluun-ei-kuulu-toimenpiteita db hinnoittelu-id))

      (jdbc/with-db-transaction [db db]
        (doseq [hinnoittelu-id hinnoittelu-idt]
          (q/poista-hinnoittelu! db user hinnoittelu-id))

        {::h/idt hinnoittelu-idt}))))

(defn liita-toimenpiteet-hinnoitteluun! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-oikeus "siirrä-tilaukseen" oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
      (to-q/vaadi-toimenpiteet-kuuluvat-urakkaan db #{(::to/id tiedot)} urakka-id)
      (q/vaadi-hinnoittelut-kuuluvat-urakkaan db #{(::h/id tiedot)} urakka-id)
      (q/poista-toimenpiteet-hintaryhmistaan! db user (::to/idt tiedot))
      (jdbc/with-db-transaction [db db]
        (q/liita-toimenpiteet-hinnoitteluun! db user (::to/idt tiedot) (::h/id tiedot))))))

(defn tallenna-hintaryhmalle-hinta! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::ur/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-oikeus "hinnoittele-tilaus" oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                      user urakka-id)
      (q/vaadi-hinnoittelut-kuuluvat-urakkaan db #{(::h/id tiedot)} urakka-id)
      (q/vaadi-hinnat-kuuluvat-hinnoitteluun db (set (map ::hinta/id (::h/tallennettavat-hinnat tiedot)))
                                             (::h/id tiedot))
      (jdbc/with-db-transaction [db db]
        (assert (not (to-q/hinnoittelu-laskutettu? db (::h/id tiedot)))
                "Hintaryhmä on jo laskutettu, eli hintaa ei voi enää muokata.")

        (q/tallenna-hintaryhmalle-hinta! db user
                                         (::h/id tiedot)
                                         (::h/tallennettavat-hinnat tiedot))

        (q/lisaa-kommentti! db user :muokattu nil nil (::h/id tiedot))

        (hae-hintaryhmat db user urakka-id)))))

(defn tallenna-toimenpiteelle-hinta! [db user tiedot]
  (when (ominaisuus-kaytossa? :vesivayla)
    (let [urakka-id (::to/urakka-id tiedot)
          toimenpide-id (::to/id tiedot)]
      (assert urakka-id "Urakka-id puuttuu!")
      (oikeudet/vaadi-oikeus "hinnoittele-toimenpide" oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset user urakka-id)
      (to-q/vaadi-toimenpiteet-kuuluvat-urakkaan db #{(::to/id tiedot)} urakka-id)
      (let [olemassa-olevat-hinta-idt (->> (keep ::hinta/id (::h/tallennettavat-hinnat tiedot))
                                           (filter id/id-olemassa?)
                                           (set))
            olemassa-olevat-tyo-idt (->> (keep ::tyo/id (::h/tallennettavat-tyot tiedot))
                                         (filter id/id-olemassa?)
                                         (set))]
        (q/vaadi-hinnat-kuuluvat-toimenpiteeseen db olemassa-olevat-hinta-idt toimenpide-id)
        (q/vaadi-tyot-kuuluvat-toimenpiteeseen db olemassa-olevat-tyo-idt toimenpide-id))
      (jdbc/with-db-transaction [db db]
        (let [hinnoittelu-id (q/luo-toimenpiteelle-oma-hinnoittelu-jos-puuttuu db user toimenpide-id urakka-id)]
          (assert (not (to-q/hinnoittelu-laskutettu? db hinnoittelu-id))
                  "Toimenpiteen hinnoittelu on jo laskutettu, eli hintaa ei voi enää muokata.")

          (q/tallenna-toimenpiteen-omat-hinnat!
            {:db db
             :user user
             :hinnoittelu-id hinnoittelu-id
             :hinnat (::h/tallennettavat-hinnat tiedot)})
          (q/tallenna-toimenpiteen-tyot!
            {:db db
             :user user
             :hinnoittelu-id hinnoittelu-id
             :tyot (::h/tallennettavat-tyot tiedot)})

          (q/lisaa-kommentti! db user :muokattu nil nil hinnoittelu-id)

          (q/hae-toimenpiteen-oma-hinnoittelu db toimenpide-id))))))

(defn tallenna-hinnoittelun-kommentti! [db user tiedot]
  (let [urakka-id (::h/urakka-id tiedot)]
    (assert urakka-id "Urakka-id puuttuu!")

    ;; Hinnoittelu, jonka id on kommentin hinnoittelu-id,
    ;; pitää kuulua urakkaan, jonka id on parametrina annettu urakka-id
    (tietoturva/vaadi-linkitys
      db
      ::h/hinnoittelu
      ::h/id
      (::kommentti/hinnoittelu-id tiedot)
      ::h/urakka-id
      urakka-id)

    (assert (not (to-q/hinnoittelu-laskutettu? db (::h/id tiedot)))
            "Hinnoittelu on jo laskutettu, eli tilaa ei voi enää muuttaa.")

    (q/lisaa-kommentti! db
                        user
                        (::kommentti/tila tiedot)
                        (::kommentti/kommentti tiedot)
                        (::kommentti/laskutus-pvm tiedot)
                        (::h/id tiedot))))

(defrecord Hinnoittelut []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu
      http
      :hae-hintaryhmat
      (fn [user tiedot]
        (let [urakka-id (::ur/id tiedot)]
          (hae-hintaryhmat db user urakka-id)))
      {:kysely-spec ::h/hae-hintaryhmat-kysely
       :vastaus-spec ::h/hae-hintaryhmat-vastaus})

    (julkaise-palvelu
      http
      :luo-hinnoittelu
      (fn [user tiedot]
        (luo-hinnoittelu! db user tiedot))
      {:kysely-spec ::h/luo-hinnoittelu-kysely
       :vastaus-spec ::h/luo-hinnoittelu-vastaus})

    (julkaise-palvelu
      http
      :poista-tyhjat-hinnoittelut
      (fn [user tiedot]
        (poista-tyhjat-hinnoittelut! db user tiedot))
      {:kysely-spec ::h/poista-tyhjat-hinnoittelut-kysely
       :vastaus-spec ::h/poista-tyhjat-hinnoittelut-vastaus})

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
      :tallenna-vv-toimenpiteen-hinta
      (fn [user tiedot]
        (tallenna-toimenpiteelle-hinta! db user tiedot))
      {:kysely-spec ::h/tallenna-vv-toimenpiteen-hinta-kysely
       :vastaus-spec ::h/tallenna-vv-toimenpiteen-hinta-vastaus})

    (julkaise-palvelu
      http
      :tallenna-hinnoittelun-kommentti
      (fn [user tiedot]
        (tallenna-hinnoittelun-kommentti! db user tiedot))
      {:kysely-spec ::h/tallenna-hinnoittelun-kommentti-kysely})

    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-hintaryhmat
      :luo-hinnoittelu
      :poista-tyhjat-hinnoittelut
      :liita-toimenpiteet-hinnoitteluun
      :tallenna-hintaryhmalle-hinta
      :tallenna-vv-toimenpiteen-hinta)
    this))
