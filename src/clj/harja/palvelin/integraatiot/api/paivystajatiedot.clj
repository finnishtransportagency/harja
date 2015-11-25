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
  (let [urakkaryhmat (keys (group-by :urakka_id paivystajatiedot))
        vastaus {:urakat (mapv
                           (fn [urakka-id]
                             (let [urakan-paivystykset (filter
                                                         #(= (:urakka_id %) urakka-id)
                                                         paivystajatiedot)
                                   {:keys [urakka_id urakka_nimi urakka_alkupvm
                                            urakka_loppupvm urakka_tyyppi]} (first urakan-paivystykset)
                                   {:keys [organisaatio_nimi organisaatio_ytunnus]} (first urakan-paivystykset)]
                               {:urakka {:tiedot {:id urakka_id
                                                  :nimi urakka_nimi
                                                  :urakoitsija {:ytunnus organisaatio_ytunnus
                                                                :nimi organisaatio_nimi }
                                                  :vaylamuoto "tie" ; FIXME Urakoiden haussa tehtyyn hardcoodattu assoc, onko oikein?
                                                  :tyyppi urakka_tyyppi
                                                  :alkupvm urakka_alkupvm
                                                  :loppupvm urakka_loppupvm}
                                         :paivystykset (mapv (fn [{:keys [id vastuuhenkilo varahenkilo alku loppu etunimi
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
                                                             urakan-paivystykset)}}))
                           urakkaryhmat)}]
    vastaus))

(defn hae-paivystajatiedot-urakan-idlla [db {:keys [id]} kayttaja]
  (log/debug "Haetaan päivystäjätiedot urakan id:llä: " id)
  (let [urakka-id (Integer/parseInt id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [paivystajatiedot (some->> urakka-id (yhteyshenkilot/hae-urakan-paivystajat db))
          vastaus (muodosta-vastaus-paivystajatietojen-haulle paivystajatiedot)]
    vastaus)))

(defn hae-paivystajatiedot-sijainnilla [db parametrit data kayttaja]
  ; TODO
  )

(defn trimmaa-puhelinnumero
  "Ottaa suomalaisen puhelinnumeron teksimuodossa ja palauttaa sen yksinkertaistetussa numeromuodossa ilman etuliitettä
  Esim. +358400-123-456 -> 0400123456
        +358500123123 -> 0500123123
        0400-123123 -> 0400123123"
  [numero-string]
  (let [puhdas-numero (apply str (filter
                                   #(#{\0, \1, \2, \3, \4, \5, \6, \7, \8, \9, \+} %)
                                   numero-string))
        siivottu-etuliite (if (= (str (first puhdas-numero)) "+")
                            (str "0" (subs puhdas-numero 4 (count puhdas-numero)))
                            puhdas-numero)]
    siivottu-etuliite))

(defn hae-paivystajatiedot-puhelinnumerolla [db _ {:keys [puhelinnumero]} kayttaja]
  (assert puhelinnumero "Ei voida hakea ilman puhelinnumeroa!")
  (log/debug "Haetaan päivystäjätiedot puhelinnumerolla: " puhelinnumero)
  ; (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja) FIXME Mites oikeustarkistus?
  (let [kaikki-paivystajatiedot (yhteyshenkilot/hae-kaikki-paivystajat db)
        paivystajatiedot-puhelinnumerolla (into [] (filter (fn [paivystys]
                                                             ; Ei voida helposti filtteröidä kantatasolla, koska puhelinnumeron
                                                             ; kirjoitusasu voi vaihdella.
                                                             (or (= (trimmaa-puhelinnumero (:tyopuhelin paivystys))
                                                                    (trimmaa-puhelinnumero puhelinnumero))
                                                                 (= (trimmaa-puhelinnumero (:matkapuhelin paivystys))
                                                                    (trimmaa-puhelinnumero puhelinnumero))))
                                                           kaikki-paivystajatiedot))
        vastaus (muodosta-vastaus-paivystajatietojen-haulle paivystajatiedot-puhelinnumerolla)]
    vastaus))

(def palvelutyypit
  [{:palvelu        :hae-paivystajatiedot-urakka-idlla
    :polku          "/api/urakat/:id/paivystajatiedot"
    :tyyppi         :GET
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+
    :kasittely-fn   (fn [parametrit _ kayttaja-id db]
                      (hae-paivystajatiedot-urakan-idlla db parametrit kayttaja-id))}
   {:palvelu        :hae-paivystajatiedot-sijainnilla
    :polku          "/api/paivystajatiedot/haku/tyypilla"
    :tyyppi         :POST
    :kutsu-skeema   json-skeemat/+paivystajatietojen-haku+
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+
    :kasittely-fn   (fn [parametrit data kayttaja-id db]
                      (hae-paivystajatiedot-sijainnilla db parametrit data kayttaja-id))}
   {:palvelu        :hae-paivystajatiedot-puhelinnumerolla
    :polku          "/api/paivystajatiedot/haku/puhelinnumerolla"
    :tyyppi         :POST
    :kutsu-skeema   json-skeemat/+paivystajatietojen-haku+
    :vastaus-skeema json-skeemat/+paivystajatietojen-haku-vastaus+
    :kasittely-fn   (fn [parametrit data kayttaja-id db]
                      (hae-paivystajatiedot-puhelinnumerolla db parametrit data kayttaja-id))}
   {:palvelu        :lisaa-paivystajatiedot
    :polku          "/api/urakat/:id/paivystajatiedot"
    :tyyppi         :POST
    :kutsu-skeema   json-skeemat/+paivystajatietojen-kirjaus+
    :vastaus-skeema json-skeemat/+kirjausvastaus+
    :kasittely-fn   (fn [parametrit data kayttaja db]
                      (kirjaa-paivystajatiedot db parametrit data kayttaja))}])

(defrecord Paivystajatiedot []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn]} palvelutyypit :when (= tyyppi :GET)]
      (julkaise-reitti
        http palvelu
        (GET polku request
          (kasittele-kutsu db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn))))

    (doseq [{:keys [palvelu polku tyyppi vastaus-skeema kutsu-skeema kasittely-fn]} palvelutyypit :when (= tyyppi :POST)]
      (julkaise-reitti
        http palvelu
        (POST polku request
          (kasittele-kutsu db integraatioloki palvelu request kutsu-skeema vastaus-skeema kasittely-fn))))

    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :lisaa-paivystajatiedot)
    this))
