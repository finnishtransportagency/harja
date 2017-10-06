(ns harja.palvelin.palvelut.vesivaylat.materiaalit
  "Vesiväylien materiaaliseurannan palvelut"
  (:require [specql.core :as specql]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.domain.muokkaustiedot :as muok]
            [harja.domain.oikeudet :as oikeudet]
            [harja.id :as id]
            [harja.kyselyt.vesivaylat.materiaalit :as m-q]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.vesivaylat.viestinta :as viestinta]
            [clojure.java.jdbc :as jdbc]))

(defn vaadi-materiaali-kuuluu-urakkaan
  [db urakka-id materiaali-id]
  "Tarkistaa, että materiaali kuuluu annettuun urakkaan. Jos ei kuulu, heittää poikkeuksen."
  (assert urakka-id "Urakka-id puuttuu")
  (when (id/id-olemassa? materiaali-id)
    (let [materiaalin-urakka-id (::m/urakka-id (first (specql/fetch db
                                                                    ::m/materiaali
                                                                    #{::m/urakka-id}
                                                                    {::m/id materiaali-id})))]
      (when (not= materiaalin-urakka-id urakka-id)
        (throw (SecurityException. (str "Materiaali " materiaali-id " ei kuulu valittuun urakkaan "
                                        urakka-id " vaan urakkaan " materiaalin-urakka-id)))))))

(defn- hae-materiaalilistaus [db user params]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivayla-materiaalit user (::m/urakka-id params))
  (specql/fetch db ::m/materiaalilistaus (specql/columns ::m/materiaalilistaus) params))

(defn- kirjaa-materiaali [db user materiaali fim email]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivayla-materiaalit user
                                  (::m/urakka-id materiaali))
  (jdbc/with-db-transaction [db db]
    (specql/insert! db ::m/materiaali
                    (muok/lisaa-muokkaustiedot materiaali ::m/id user))
    (let [materiaalilistaus (hae-materiaalilistaus db user (select-keys materiaali #{::m/urakka-id}))
          muokattu-materiaali (some #(when (and (= (::m/urakka-id %) (::m/urakka-id materiaali))
                                                (= (::m/nimi %) (::m/nimi materiaali)))
                                       %)
                                    materiaalilistaus)]
      (println "MUOKATTU MATERIAALI: " muokattu-materiaali)
      (when (and (::m/halytysraja muokattu-materiaali)
                 (< (::m/maara-nyt muokattu-materiaali) (::m/halytysraja muokattu-materiaali)))
        (let [parametrit {:id (::m/urakka-id muokattu-materiaali)}
              urakan-tiedot (first (m-q/urakan-tiedot-sahkopostin-lahetysta-varten db parametrit))
              urakan-ja-materiaalin-tiedot (merge urakan-tiedot (select-keys muokattu-materiaali [::m/halytysraja ::m/maara-nyt ::m/nimi]))]
          (viestinta/laheta-sposti-materiaalin-halyraja fim email urakan-ja-materiaalin-tiedot)))
      materiaalilistaus)))

(defn- poista-materiaalikirjaus [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivayla-materiaalit user
                                  (::m/urakka-id tiedot))
  (vaadi-materiaali-kuuluu-urakkaan db (::m/urakka-id tiedot) (::m/id tiedot))
  (specql/update! db ::m/materiaali
                  (muok/poistotiedot user)
                  {::m/id (::m/id tiedot)})
  (hae-materiaalilistaus db user (select-keys tiedot #{::m/urakka-id})))

(defn- muuta-materiaalin-alkuperainen-maara [db materiaali]
  (m-q/paivita-materiaalin-alkuperainen-maara<!
    db
    {:maara (::m/alkuperainen-maara materiaali)
     :nimi (::m/nimi materiaali)}))

(defn- muuta-materiaalien-alkuperainen-maara [db user tiedot]
  (let [urakka-id (::m/urakka-id tiedot)
        uudet (:uudet-alkuperaiset-maarat tiedot)]
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivayla-materiaalit user
                                    (::m/urakka-id tiedot))
    (doseq [materiaali uudet]
      (let [materiaali-id (:id (first (m-q/materiaalin-id-nimella db {:nimi (::m/nimi materiaali)})))]
        (vaadi-materiaali-kuuluu-urakkaan db urakka-id materiaali-id)))

    (doseq [materiaali uudet]
      (muuta-materiaalin-alkuperainen-maara db materiaali))

    (hae-materiaalilistaus db user (select-keys tiedot #{::m/urakka-id}))))

(defrecord Materiaalit []
  component/Lifecycle
  (start [{db    :db
           http  :http-palvelin
           fim   :fim
           email :sonja-sahkoposti :as this}]
    (http-palvelin/julkaise-palvelu http :hae-vesivayla-materiaalilistaus
                                    (fn [user haku]
                                      (hae-materiaalilistaus db user haku))
                                    {:kysely-spec ::m/materiaalilistauksen-haku
                                     :vastaus-spec ::m/materiaalilistauksen-vastaus})
    (http-palvelin/julkaise-palvelu http :kirjaa-vesivayla-materiaali
                                    (fn [user materiaali]
                                      (kirjaa-materiaali db user materiaali fim email))
                                    {:kysely-spec ::m/materiaalikirjaus
                                     :vastaus-spec ::m/materiaalilistauksen-vastaus})
    (http-palvelin/julkaise-palvelu http :poista-materiaalikirjaus
                                    (fn [user tiedot]
                                      (poista-materiaalikirjaus db user tiedot))
                                    {:kysely-spec ::m/poista-materiaalikirjaus
                                     :vastaus-spec ::m/materiaalilistauksen-vastaus})
    (http-palvelin/julkaise-palvelu http :muuta-materiaalien-alkuperainen-maara
                                    (fn [user tiedot]
                                      (muuta-materiaalien-alkuperainen-maara db user tiedot))
                                    {:kysely-spec ::m/muuta-materiaalien-alkuperainen-maara
                                     :vastaus-spec ::m/materiaalilistauksen-vastaus})
    this)

  (stop [this]
    (http-palvelin/poista-palvelut
      (:http-palvelin this)
      :hae-vesivayla-materiaalilistaus
      :kirjaa-vesivayla-materiaali
      :poista-materiaalikirjaus)
    this))
