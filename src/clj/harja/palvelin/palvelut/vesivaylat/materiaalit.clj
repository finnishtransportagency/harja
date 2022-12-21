(ns harja.palvelin.palvelut.vesivaylat.materiaalit
  "Vesiväylien materiaaliseurannan palvelut"
  (:require [specql.core :as specql]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [com.stuartsierra.component :as component]
            [harja.domain.vesivaylat.materiaali :as m]
            [harja.domain.muokkaustiedot :as muok]
            [harja.domain.oikeudet :as oikeudet]
            [harja.id :as id]
            [harja.kyselyt.vesivaylat.materiaalit :as m-q]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.vesivaylat.viestinta :as viestinta]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]))

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

(defn hoida-halytysraja
  "Tarkistaa ensin kannasta, että onko annettu materiaali alittanut hälytysrajan urakassa.
   Jos on, lähettää siitä sähköposti-ilmoituksen tilaajan urakanvalvojalle"
  [db materiaalikirjaus fim email]
  (jdbc/with-db-transaction [db db]
    (let [materiaalilistaukset (m-q/hae-materiaalilistaus db (select-keys materiaalikirjaus [::m/urakka-id]))
          materiaalin-tiedot (some #(when (= (::m/nimi %) (::m/nimi materiaalikirjaus))
                                      %)
                                   materiaalilistaukset)
          halytysraja-ylitetty? (and (::m/halytysraja materiaalin-tiedot)
                                     (< (::m/maara-nyt materiaalin-tiedot) (::m/halytysraja materiaalin-tiedot)))]
      (when halytysraja-ylitetty?
        (let [haku-parametrit {:id (::m/urakka-id materiaalin-tiedot)}
              urakan-tiedot (first (m-q/urakan-tiedot-sahkopostin-lahetysta-varten db haku-parametrit))
              urakan-ja-materiaalin-tiedot (merge urakan-tiedot (select-keys materiaalin-tiedot [::m/halytysraja ::m/maara-nyt ::m/nimi]))]
          (viestinta/laheta-sposti-materiaalin-halyraja fim email urakan-ja-materiaalin-tiedot)))
      materiaalilistaukset)))

(defn- hae-materiaalilistaus [db user hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-vesivayla-materiaalit user (::m/urakka-id hakuehdot))
  (m-q/hae-materiaalilistaus db hakuehdot))

(defn- kirjaa-materiaali [db user materiaalikirjaus fim email]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivayla-materiaalit user
                                  (::m/urakka-id materiaalikirjaus))
  (jdbc/with-db-transaction [db db]
    (m-q/kirjaa-materiaali db user materiaalikirjaus)
    (hoida-halytysraja db materiaalikirjaus fim email)))

(defn- poista-materiaalikirjaus [db user tiedot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivayla-materiaalit user
                                  (::m/urakka-id tiedot))
  (jdbc/with-db-transaction [db db]
    (vaadi-materiaali-kuuluu-urakkaan db (::m/urakka-id tiedot) (::m/id tiedot))
    (m-q/poista-materiaalikirjaus db user (::m/id tiedot))
    (hae-materiaalilistaus db user (select-keys tiedot #{::m/urakka-id}))))

(defn- muuta-materiaalin-alkuperainen-maara [db user alkuperainen-maara ensimmainen-kirjaus-id]
  (when alkuperainen-maara
    (m-q/paivita-materiaalin-alkuperainen-maara<!
      db
      {:maara alkuperainen-maara
       :id ensimmainen-kirjaus-id
       :muokkaaja (:id user)})))

(defn- muuta-materiaalin-alkuperainen-yksikko-kaikilta-kirjauksilta [db user yksikko idt]
  (when yksikko
    (m-q/paivita-materiaalin-alkuperainen-yksikko-kaikilta-kirjauksilta<!
      db
      {:yksikko yksikko
       :idt idt
       :muokkaaja (:id user)})))

(defn- muuta-materiaalin-alkuperainen-halytysraja-kaikilta-kirjauksilta [db user halytysraja idt]
  (when halytysraja
    (m-q/paivita-materiaalin-alkuperainen-halytysraja-kaikilta-kirjauksilta<!
      db
      {:halytysraja halytysraja
       :idt idt
       :muokkaaja (:id user)})))

(defn- muuta-materiaalien-alkuperaiset-tiedot [db user tiedot]
  (let [urakka-id (::m/urakka-id tiedot)
        uudet (:uudet-alkuperaiset-tiedot tiedot)]
    (assert (every? #(not (nil? (::m/idt %))) uudet) "Päivitettävien materiaalien idt ei voi olla nil")
    (assert (every? (fn [uusi]
                      (some #(= (::m/ensimmainen-kirjaus-id uusi) %)
                            (::m/idt uusi)))
                    uudet)
            "ensimmainen-kirjaus-id täytyy löytyä myös :idt avaimesta")
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-vesivayla-materiaalit user
                                    (::m/urakka-id tiedot))
    (doseq [uusi uudet
            materiaali-id (::m/idt uusi)]
      (vaadi-materiaali-kuuluu-urakkaan db urakka-id materiaali-id))

    (jdbc/with-db-transaction [db db]
      (doseq [materiaali uudet
              :let [alkuperainen-maara (::m/alkuperainen-maara materiaali)
                    yksikko (::m/yksikko materiaali)
                    halytysraja (::m/halytysraja materiaali)
                    ensimmainen-kirjaus-id (::m/ensimmainen-kirjaus-id materiaali)
                    idt (::m/idt materiaali)]]
        (muuta-materiaalin-alkuperainen-maara db user alkuperainen-maara ensimmainen-kirjaus-id)
        (muuta-materiaalin-alkuperainen-yksikko-kaikilta-kirjauksilta db user yksikko idt)
        (muuta-materiaalin-alkuperainen-halytysraja-kaikilta-kirjauksilta db user halytysraja idt))

      (hae-materiaalilistaus db user (select-keys tiedot #{::m/urakka-id})))))

(defrecord Materiaalit []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin
           fim :fim :as this}]
    (let [email (:api-sahkoposti this)]
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
    (http-palvelin/julkaise-palvelu http :muuta-materiaalien-alkuperaiset-tiedot
                                    (fn [user tiedot]
                                      (muuta-materiaalien-alkuperaiset-tiedot db user tiedot))
                                    {:kysely-spec ::m/muuta-materiaalien-alkuperaiset-tiedot
                                     :vastaus-spec ::m/materiaalilistauksen-vastaus})
    this))

  (stop [this]
    (http-palvelin/poista-palvelut
      (:http-palvelin this)
      :hae-vesivayla-materiaalilistaus
      :kirjaa-vesivayla-materiaali
      :poista-materiaalikirjaus
      :muuta-materiaalien-alkuperaiset-tiedot)
    this))
