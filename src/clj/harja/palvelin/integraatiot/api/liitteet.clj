(ns harja.palvelin.integraatiot.api.liitteet
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST DELETE]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liite-tyokalut]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.liitteet :as liitteet-q]
            [harja.kyselyt.siltatarkastukset :as silta-q]
            [harja.kyselyt.tarkastukset :as tarkastukset-q]
            [harja.kyselyt.laatupoikkeamat :as laatupoikkeamat-q]
            [harja.kyselyt.turvallisuuspoikkeamat :as turvallisuuspoikkeamat-q]
            [harja.palvelin.integraatiot.api.siltatarkastukset :as siltatarkastukset]
            [clojure.java.jdbc :as jdbc]
            [slingshot.slingshot :refer [throw+]]))

(defn- heita-liitevirhe [viesti]
  (throw+ {:type virheet/+viallinen-kutsu+
           :virheet [{:koodi virheet/+virheellinen-liite-koodi+
                      :viesti viesti}]}))

(defn- tallenna-siltatarkastuskohteen-liitteet
  [{id :id} {:keys [liite-lahetys]} kayttaja db liitteiden-hallinta]
  (let [urakka-id (Integer/parseInt id)
        ulkoinen-id (str (get-in liite-lahetys [:kohdetunniste :id]))
        kohde (get liite-lahetys :siltatarkastuskohde)
        kohde-id (get siltatarkastukset/api-kohde->numero kohde)
        liite (:liite liite-lahetys)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (when-not kohde
      (heita-liitevirhe "Siltatarkastuksen kohde puuttuu"))
    (when-not kohde-id
      (heita-liitevirhe (format "Tuntematon siltatarkastuksen kohde: %s" kohde)))
    (let [siltatarkastus (first (silta-q/hae-siltatarkastus-ulkoisella-idlla-ja-urakalla
                                  db ulkoinen-id urakka-id))]
      (when-not siltatarkastus
        (heita-liitevirhe (format "Siltatarkastusta ei löydy tunnisteella: %s" ulkoinen-id)))
      (jdbc/with-db-transaction [db db]
        (liite-tyokalut/tallenna-liitteet-siltatarkastuskohteelle
          db
          liitteiden-hallinta
          kayttaja
          urakka-id
          (:id siltatarkastus)
          kohde-id
          [{:liite liite}]))
      (tee-kirjausvastauksen-body {:ilmoitukset "Liite vastaanotettu onnistuneesti"}))))

(defn- tallenna-tarkastuksen-liite
  [{id :id} {:keys [liite-lahetys]} kayttaja db liitteiden-hallinta]
  (let [urakka-id (Integer/parseInt id)
        ulkoinen-id (get-in liite-lahetys [:kohdetunniste :id])
        liite (:liite liite-lahetys)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [tarkastus (first (tarkastukset-q/hae-tarkastus-ulkoisella-idlla-ja-urakalla
                              db ulkoinen-id urakka-id))]
      (when-not tarkastus
        (heita-liitevirhe (format "Tarkastusta ei löydy tunnisteella: %s" ulkoinen-id)))
      (jdbc/with-db-transaction [db db]
        (liite-tyokalut/tallenna-liitteet-tarkastukselle
          db
          liitteiden-hallinta
          urakka-id
          (:id tarkastus)
          kayttaja
          [{:liite liite}]))
      (tee-kirjausvastauksen-body {:ilmoitukset "Liite vastaanotettu onnistuneesti"}))))

(defn- tallenna-tarkastushavainnon-liite
  [{id :id} {:keys [liite-lahetys]} kayttaja db liitteiden-hallinta]
  (let [urakka-id (Integer/parseInt id)
        ulkoinen-id (str (get-in liite-lahetys [:kohdetunniste :id]))
        liite (:liite liite-lahetys)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [laatupoikkeama (first (laatupoikkeamat-q/hae-laatupoikkeama-ulkoisella-idlla-ja-urakalla
                                  db ulkoinen-id urakka-id))]
      (when-not laatupoikkeama
        (heita-liitevirhe (format "Tarkastushavaintoa ei löydy tunnisteella: %s" ulkoinen-id)))
      (jdbc/with-db-transaction [db db]
        (liite-tyokalut/tallenna-liitteet-laatupoikkeamalle
          db
          liitteiden-hallinta
          urakka-id
          (:id laatupoikkeama)
          kayttaja
          [{:liite liite}]))
      (tee-kirjausvastauksen-body {:ilmoitukset "Liite vastaanotettu onnistuneesti"}))))

(defn- tallenna-laatupoikkeaman-liite
  [{id :id} {:keys [liite-lahetys]} kayttaja db liitteiden-hallinta]
  (let [urakka-id (Integer/parseInt id)
        ulkoinen-id (str (get-in liite-lahetys [:kohdetunniste :id]))
        liite (:liite liite-lahetys)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [laatupoikkeama (first (laatupoikkeamat-q/hae-laatupoikkeama-ulkoisella-idlla-ja-urakalla
                                  db ulkoinen-id urakka-id))]
      (when-not laatupoikkeama
        (heita-liitevirhe (format "Laatupoikkeamaa ei löydy tunnisteella: %s" ulkoinen-id)))
      (jdbc/with-db-transaction [db db]
        (liite-tyokalut/tallenna-liitteet-laatupoikkeamalle
          db
          liitteiden-hallinta
          urakka-id
          (:id laatupoikkeama)
          kayttaja
          [{:liite liite}]))
      (tee-kirjausvastauksen-body {:ilmoitukset "Liite vastaanotettu onnistuneesti"}))))

(defn- tallenna-turvallisuuspoikkeaman-liite
  [{id :id} {:keys [liite-lahetys]} kayttaja db liitteiden-hallinta]
  (let [urakka-id (Integer/parseInt id)
        ulkoinen-id (str (get-in liite-lahetys [:kohdetunniste :id]))
        liite (:liite liite-lahetys)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [turvallisuuspoikkeama (first (turvallisuuspoikkeamat-q/hae-turvallisuuspoikkeama-ulkoisella-idlla-ja-urakalla
                                          db ulkoinen-id urakka-id))]
      (when-not turvallisuuspoikkeama
        (heita-liitevirhe (format "Turvallisuuspoikkeamaa ei löydy tunnisteella: %s" ulkoinen-id)))
      (jdbc/with-db-transaction [db db]
        (liite-tyokalut/tallenna-liitteet-turvallisuuspoikkeamalle
          db
          liitteiden-hallinta
          urakka-id
          (:id turvallisuuspoikkeama)
          kayttaja
          [{:liite liite}]))
      (tee-kirjausvastauksen-body {:ilmoitukset "Liite vastaanotettu onnistuneesti"}))))

(defn- poista-liite [parametrit data kayttaja db]
  (let [urakka-id (Integer/parseInt (:id parametrit))
        ulkoinen-id (get-in data [:liite-poisto :tunniste :id])]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (when (= 0 (liitteet-q/merkitse-liite-poistetuksi! db {:urakka urakka-id
                                                             :ulkoinen_id ulkoinen-id
                                                             :kayttaja (:id kayttaja)}))
      (heita-liitevirhe (format "Liitettä ei löydy tunnisteella: %s" ulkoinen-id)))
    (tee-kirjausvastauksen-body {:ilmoitukset "Liite poistettu onnistuneesti"})))

(defn vastaanota-liite [parametrit data kayttaja db liitteiden-hallinta]
  (let [kohdeluokka (get-in data [:liite-lahetys :kohdeluokka])]
    (try
      (case kohdeluokka
        "siltatarkastuskohde" (tallenna-siltatarkastuskohteen-liitteet
                                 parametrit data kayttaja db liitteiden-hallinta)
        "tarkastus" (tallenna-tarkastuksen-liite
                       parametrit data kayttaja db liitteiden-hallinta)
        "tarkastushavainto" (tallenna-tarkastushavainnon-liite
                               parametrit data kayttaja db liitteiden-hallinta)
        "laatupoikkeama" (tallenna-laatupoikkeaman-liite
                            parametrit data kayttaja db liitteiden-hallinta)
        "turvallisuuspoikkeama" (tallenna-turvallisuuspoikkeaman-liite
                                  parametrit data kayttaja db liitteiden-hallinta)
        (heita-liitevirhe (format "Liitteen kohdeluokkaa ei vielä tueta: %s" kohdeluokka)))
      (catch IllegalArgumentException _
        (heita-liitevirhe "Liitteen sisältö on virheellinen")))))


(defrecord Liitteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :api-vastaanota-liite
      (POST "/api/urakat/:id/liite" request
        (kasittele-kutsu db integraatioloki :vastaanota-liite request
          json-skeemat/liitteen-lahetys json-skeemat/kirjausvastaus
          (fn [parametrit data kayttaja db]
            (vastaanota-liite parametrit data kayttaja db liitteiden-hallinta))
          :kirjoitus)))
    (julkaise-reitti
      http :api-poista-liite
      (DELETE "/api/urakat/:id/liite" request
        (kasittele-kutsu db integraatioloki :poista-liite request
          json-skeemat/liitteen-poisto json-skeemat/kirjausvastaus
          (fn [parametrit data kayttaja db]
            (poista-liite parametrit data kayttaja db))
          :kirjoitus)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :api-vastaanota-liite :api-poista-liite)
    this))
