(ns harja.palvelin.integraatiot.api.paivystajatiedot
  "Päivystäjätietojen kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [harja.palvelin.komponentit.liitteet :refer [->Liitteet] :as liitteet]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [throw+]]))

"NOTE: Kirjaus toimii tällä hetkellä niin, että ulkoisen id:n omaavat päivystäjät päivitetään ja
ilman ulkoista id:tä olevat lisätään Harjaan. Harjan käyttöliittymästä lisätyillä päivystäjillä
ei ole ulkoista id:tä, joten ne ovat Harjan itse ylläpitämiä."

(defn tee-onnistunut-vastaus []
  (let [vastauksen-data {:ilmoitukset "Päivystäjätiedot kirjattu onnistuneesti"}]
    vastauksen-data))

(defn paivita-tai-luo-uusi-paivystys [db urakka-id {:keys [alku loppu]} paivystaja-id]
  (if (yhteyshenkilot/onko-olemassa-paivystys-jossa-yhteyshenkilona-id? db paivystaja-id)
    (do
      (log/debug "Päivitetään päivystäjään liittyvän päivystyksen tiedot.")
      (yhteyshenkilot/paivita-paivystys-yhteyshenkilon-idlla<! db (pvm-string->java-sql-date alku) (pvm-string->java-sql-date loppu) paivystaja-id))
    (do
      (log/debug "Päivystäjällä ei ole päivystystä. Luodaan uusi päivystys.")
      (yhteyshenkilot/luo-paivystys<! db (pvm-string->java-sql-date alku) (pvm-string->java-sql-date loppu) urakka-id paivystaja-id))))

(defn paivita-tai-luo-uusi-paivystaja [db {:keys [id etunimi sukunimi email puhelinnumero liviTunnus]}]
  (if (yhteyshenkilot/onko-olemassa-yhteyshenkilo-ulkoisella-idlla? db (str id))
    (do
      (log/debug "Päivitetään päivystäjän tiedot ulkoisella id:llä " id)
      (:id (yhteyshenkilot/paivita-yhteyshenkilo-ulkoisella-idlla<! db etunimi sukunimi nil puhelinnumero email nil (str id))))
    (do
      (log/debug "Päivystäjää ei löytynyt ulkoisella id:llä. Lisätään uusi päivystäjä")
      (:id (yhteyshenkilot/luo-yhteyshenkilo<! db etunimi sukunimi nil puhelinnumero email nil nil liviTunnus (str id))))))

(defn tallenna-paivystajatiedot [db urakka-id data]
  (log/debug "Aloitetaan päivystäjätietojen kirjaus")
  (jdbc/with-db-transaction [transaktio db]
    (doseq [paivystys (:paivystykset data)]
      (let [paivystaja-id (paivita-tai-luo-uusi-paivystaja db (get-in paivystys [:paivystys :paivystaja]))]
        (paivita-tai-luo-uusi-paivystys db urakka-id (:paivystys paivystys) paivystaja-id)))))

(defn kirjaa-paivystajatiedot [db {id :id} data kirjaaja]
  (let [urakka-id (Integer/parseInt id)]
    (log/debug "Kirjataan päivystäjätiedot urakalle id:" urakka-id " kayttäjän:" (:kayttajanimi kirjaaja) " (id:" (:id kirjaaja) " tekemänä.")
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kirjaaja)
    (tallenna-paivystajatiedot db urakka-id data)
    (tee-onnistunut-vastaus)))

(defrecord Paivystajatiedot []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-paivystajatiedot
      (POST "/api/urakat/:id/paivystajatiedot" request
        (kasittele-kutsu db integraatioloki :lisaa-paivystajatiedot request json-skeemat/+paivystajatietojen-kirjaus+ json-skeemat/+kirjausvastaus+
                         (fn [parametit data kayttaja db]
                           (kirjaa-paivystajatiedot db parametit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-paivystajatiedot)
    this))
