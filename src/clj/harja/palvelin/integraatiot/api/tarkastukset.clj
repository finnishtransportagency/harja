(ns harja.palvelin.integraatiot.api.tarkastukset
  "Tarkastusten kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :refer [join]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.kyselyt.tarkastukset :as tarkastukset]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]))

(defn tee-onnistunut-vastaus [varoitukset]
  (let [vastauksen-data {:ilmoitukset "Tarkastukset kirjattu onnistuneesti"}]
    (if (not-empty varoitukset)
      (assoc vastauksen-data :varoitukset varoitukset)
      vastauksen-data)))

(defn tallenna-mittaustulokset-tarkastukselle [db id tyyppi uusi? mittaus]
  (case tyyppi
    :talvihoito (tarkastukset/luo-tai-paivita-talvihoitomittaus db id uusi? mittaus)
    :soratie (tarkastukset/luo-tai-paivita-soratiemittaus db id uusi? mittaus)
    nil))

(defn kasittele-tarkastukset [db liitteiden-hallinta kayttaja tyyppi urakka-id data]
  (mapv
    (fn [rivi]
      (let [tarkastus (:tarkastus rivi)
            ulkoinen-id (-> tarkastus :tunniste :id)]
        (jdbc/with-db-transaction [transaktio db]
          (let [{tarkastus-id :id}
                (first
                  (tarkastukset/hae-tarkastus-ulkoisella-idlla-ja-tyypilla db ulkoinen-id (name tyyppi) (:id kayttaja)))
                uusi? (nil? tarkastus-id)]

            (let [aika (json/aika-string->java-sql-date (:aika tarkastus))
                  tr-osoite (sijainnit/hae-tierekisteriosoite db (:alkusijainti tarkastus) (:loppusijainti tarkastus))
                  geometria (if tr-osoite (:geometria tr-osoite)
                                          (sijainnit/tee-geometria (:alkusijainti tarkastus) (:loppusijainti tarkastus)))
                  id (tarkastukset/luo-tai-paivita-tarkastus
                       db kayttaja urakka-id
                       {:id          tarkastus-id
                        :ulkoinen-id ulkoinen-id
                        :tyyppi      tyyppi
                        :aika        aika
                        :tarkastaja  (json/henkilo->nimi (:tarkastaja tarkastus))
                        :sijainti    geometria
                        :tr          {:numero        (:tie tr-osoite)
                                      :alkuosa       (:aosa tr-osoite)
                                      :alkuetaisyys  (:aet tr-osoite)
                                      :loppuosa      (:losa tr-osoite)
                                      :loppuetaisyys (:let tr-osoite)}
                        :havainnot   (:havainnot tarkastus)})
                  liitteet (:liitteet tarkastus)]

              (tallenna-liitteet-tarkastukselle db liitteiden-hallinta urakka-id id kayttaja liitteet)
              (tallenna-mittaustulokset-tarkastukselle db id tyyppi uusi? (:mittaus rivi))
              (when-not tr-osoite
                (format "Annetulla sijainnilla ei voitu päätellä sijaintia tieverkolla (alku: %s, loppu %s)."
                        (:alkusijainti tarkastus) (:loppusijainti tarkastus))))))))
    (:tarkastukset data)))

(defn kirjaa-tarkastus [db liitteiden-hallinta kayttaja tyyppi {id :id} data]
  (let [urakka-id (Long/parseLong id)]
    (log/debug (format "Kirjataan tarkastus tyyppiä: %s käyttäjän: %s toimesta. Data: %s" tyyppi (:kayttajanimi kayttaja) data))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tee-onnistunut-vastaus (join ", " (kasittele-tarkastukset db liitteiden-hallinta kayttaja tyyppi urakka-id data)))))

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
