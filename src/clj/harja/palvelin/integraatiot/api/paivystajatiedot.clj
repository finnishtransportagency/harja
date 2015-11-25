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
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.konversio :as konv])
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

(defn muodosta-vastaus-paivystajatietojen-haulle [paivystajatiedot]
  {:paivystykset (mapv (fn [{:keys [id vastuuhenkilo varahenkilo alku loppu etunimi
                                    sukunimi sahkoposti tyopuhelin matkapuhelin]}]
                         {:paivystys {:paivystaja    {:id            id
                                                      :etunimi       etunimi
                                                      :sukunimi      sukunimi
                                                      :email         sahkoposti
                                                      :puhelinnumero tyopuhelin}
                                      :alku          alku
                                      :loppu         loppu
                                      :vastuuhenkilo vastuuhenkilo
                                      :varahenkilo   varahenkilo}})
                       paivystajatiedot)})

(defn hae-paivystajatiedot [db parametrit data kayttaja]
  ;; dummytoteutus t-loik testiä varten
  ;; todo: tee käyttäjärajaus
  ;; todo: validoi parametrit
  ;; todo: tee oikea haku
  {"otsikko" {"lahettaja" {"jarjestelma" "Urakoitsijan järjestelmä", "organisaatio" {"nimi" "Urakoitsija", "ytunnus" "1234567-8"}}, "viestintunniste" {"id" 123}, "lahetysaika" "2015-01-03T12:00:00Z"}, "paivystykset" [{"paivystys" {"paivystaja" {"id" 454653434, "etunimi" "Päivi", "sukunimi" "Päivystäjä", "email" "paivi.paivystaja@test.i", "puhelinnumero" "7849885769", "liviTunnus" "LX1234345"}, "alku" "2015-01-30T12:00:00Z", "loppu" "2016-01-30T12:00:00Z", "vastuuhenkilo" true, "yhteyshenkilo" true, "varahenkilo" true}} {"paivystys" {"paivystaja" {"id" 1123588, "etunimi" "Pertti", "sukunimi" "Päivystäjä", "email" "pertti.paivystaja@foo.bar", "puhelinnumero" "0287445698"}, "alku" "2015-01-30T12:00:00Z", "loppu" "2016-01-30T12:00:00Z", "vastuuhenkilo" false, "yhteyshenkilo" false, "varahenkilo" true}}]})

(defn hae-paivystajatiedot-urakan-idlla [db {:keys [id]} kayttaja]
  (log/debug "Haetaan päivystäjätiedot urakan id:llä: " id)
  (let [urakka-id (Integer/parseInt id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [paivystajatiedot (some->> urakka-id (yhteyshenkilot/hae-urakan-paivystajat db))
          vastaus (muodosta-vastaus-paivystajatietojen-haulle paivystajatiedot)]
    vastaus)))

(defn hae-paivystajatiedot-tyypilla-ja-sijainnilla [db parametrit data kayttaja]
  ; TODO
  )

(defn hae-paivystajatiedot-puhelinnumerolla [db parametrit data kayttaja]
  ; TODO
  )

(def hakutyypit
  [{:palvelu        :hae-paivystajatiedot-urakka-idlla
    :polku          "/api/urakat/:id/paivystajatiedot"
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+
    :kasittely-fn   (fn [parametrit _ kayttaja-id db]
                      (hae-paivystajatiedot-urakan-idlla db parametrit kayttaja-id))}
   {:palvelu        :hae-paivystajatiedot-tyypilla-ja-sijainnilla
    :polku          "/api/paivystajatiedot/haku/tyypilla-ja-sijainnilla"
    :pyynto-skeema  nil ; FIXME Tee tämä
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+ ; FIXME Sopiiko tähän?
    :kasittely-fn   (fn [parametrit data kayttaja-id db]
                      (hae-paivystajatiedot-tyypilla-ja-sijainnilla db parametrit data kayttaja-id))}
   {:palvelu        :hae-paivystajatiedot-puhelinnumerolla
    :polku          "/api/paivystajatiedot/haku/puhelinnumerolla"
    :pyynto-skeema  nil ; FIXME Tee tämä
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+ ; FIXME Sopiiko tähän?
    :kasittely-fn   (fn [parametrit data kayttaja-id db]
                      (hae-paivystajatiedot-puhelinnumerolla db parametrit data kayttaja-id))}])

(defrecord Paivystajatiedot []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku vastaus-skeema pyynto-skeema kasittely-fn]} hakutyypit]
      (julkaise-reitti
        http palvelu
        (GET polku request
          (kasittele-kutsu db integraatioloki palvelu request pyynto-skeema vastaus-skeema kasittely-fn))))

    ; FIXME Poista dummy-toteutus kun oikea toteutus on kasassa
    (julkaise-reitti
      http :hae-paivystajatiedot
      (GET "/api/paivystajatiedot" request
        (kasittele-kutsu db integraatioloki :hae-paivystajatiedot request json-skeemat/+paivystajatietojen-haku+ json-skeemat/+paivystajatietojen-kirjaus+
                         (fn [parametit data kayttaja db]
                           (hae-paivystajatiedot db parametit data kayttaja)))))

    (julkaise-reitti
      http :lisaa-paivystajatiedot
      (POST "/api/urakat/:id/paivystajatiedot" request
        (kasittele-kutsu db integraatioloki :lisaa-paivystajatiedot request json-skeemat/+paivystajatietojen-kirjaus+ json-skeemat/+kirjausvastaus+
                         (fn [parametrit data kayttaja db]
                           (kirjaa-paivystajatiedot db parametrit data kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-paivystajatiedot)
    this))
