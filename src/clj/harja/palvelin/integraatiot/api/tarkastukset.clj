(ns harja.palvelin.integraatiot.api.tarkastukset
  "Tarkastusten kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.kyselyt.tarkastukset :as tarkastukset]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.kyselyt.tieverkko :as tieverkko]))

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Tarkastukset kirjattu onnistuneesti"}]
    vastauksen-data))

(defn hae-sijainti [db alkusijainti loppusijainti]
  (println "----> alkusijainti" alkusijainti)
  (println "----> loppusijainti" loppusijainti)

  (let [alku-x (:x alkusijainti)
        alku-y (:y alkusijainti)
        loppu-x (:x loppusijainti)
        loppu-y (:y loppusijainti)
        threshold 250]
    (if (and alku-x alku-y loppu-x loppu-y)
      (first (tieverkko/hae-tr-osoite-valille db alku-x alku-y loppu-x loppu-y threshold))
      (when (and alku-x alku-y)
        (first (tieverkko/hae-tr-osoite db alku-x alku-y threshold))))))

(defn tallenna-mittaustulokset-tarkastukselle [db id tyyppi uusi? tarkastus]
  (case tyyppi
    :talvihoito (tarkastukset/luo-tai-paivita-talvihoitomittaus
                  db id uusi? (-> tarkastus
                                  :mittaus
                                  (assoc :lumimaara (:lumisuus (:mittaus tarkastus)))))

    :soratie (tarkastukset/luo-tai-paivita-soratiemittaus
               db id uusi? (:mittaus tarkastus))))

(defn kirjaa-tarkastus [db liitteiden-hallinta kayttaja tyyppi {id :id} data]
  (let [urakka-id (Long/parseLong id)]
    (log/debug (format "Kirjataan tarkastus: tyyppi: %s, käyttäjä: %s, data: %s" tyyppi kayttaja data))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (doseq [tarkastus (:tarkastukset data)]
      (let [tarkastus (:tarkastus tarkastus)
            ulkoinen-id (-> tarkastus :tunniste :id)]
        (jdbc/with-db-transaction [db db]
          (let [{tarkastus-id :id}
                (first
                  (tarkastukset/hae-tarkastus-ulkoisella-idlla-ja-tyypilla db ulkoinen-id (name tyyppi) (:id kayttaja)))
                uusi? (nil? tarkastus-id)]

            (let [aika (json/pvm-string->java-sql-date (:aika tarkastus))
                  sijainti (hae-sijainti db (:alkusijainti tarkastus) (:loppusijainti tarkastus))
                  id (tarkastukset/luo-tai-paivita-tarkastus
                       db kayttaja urakka-id
                       {:id          tarkastus-id
                        :ulkoinen-id ulkoinen-id
                        :tyyppi      tyyppi
                        :aika        aika
                        :tarkastaja  (json/henkilo->nimi (:tarkastaja tarkastus))
                        :sijainti    (:geometria sijainti)
                        :tr          {:numero        (:tie sijainti)
                                      :alkuosa       (:aosa sijainti)
                                      :alkuetaisyys  (:aet sijainti)
                                      :loppuosa      (:losa sijainti)
                                      :loppuetaisyys (:let sijainti)}
                        :havainnot   (:havainnot tarkastus)})
                  liitteet (:liitteet tarkastus)]

              (tallenna-liitteet-tarkastukselle db liitteiden-hallinta urakka-id id kayttaja liitteet)
              (tallenna-mittaustulokset-tarkastukselle db id tyyppi uusi? tarkastus))))))
    (tee-onnistunut-vastaus)))

(def palvelut
  [{:palvelu       :lisaa-tiestotarkastus
    :polku         "/api/urakat/:id/tarkastus/tiestotarkastus"
    :pyynto-skeema json-skeemat/+tiestotarkastuksen-kirjaus+
    :tyyppi        :tiesto}
   {:palvelu       :lisaa-talvihoitotarkastus
    :polku         "/api/urakat/:id/tarkastus/talvihoitotarkastus"
    :pyynto-skeema json-skeemat/+talvihoitotarkastuksen-kirjaus+
    :tyyppi        :talvihoito}
   {:palvelu       :lisaa-soratietarkastus
    :polku         "/api/urakat/:id/tarkastus/soratietarkastus"
    :pyynto-skeema json-skeemat/+soratietarkastuksen-kirjaus+
    :tyyppi        :soratie}])

(defrecord Tarkastukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku pyynto-skeema tyyppi]} palvelut]
      (julkaise-reitti
        http palvelu
        (POST polku request
          (do
            (kasittele-kutsu db integraatioloki palvelu request
                             pyynto-skeema json-skeemat/+kirjausvastaus+
                             (fn [parametrit data kayttaja db]
                               (kirjaa-tarkastus db liitteiden-hallinta kayttaja tyyppi parametrit data)))))))

    this)

  (stop [{http :http-palvelin :as this}]
    (apply poista-palvelut http (map :palvelu palvelut))
    this))
