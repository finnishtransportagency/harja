(ns harja.palvelin.integraatiot.api.tiemerkintatoteuma
  "Pistetoteuman kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST DELETE]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.palvelut :as palvelut]
            [harja.palvelin.integraatiot.api.kasittely.tiemerkintatoteumat :as tiemerkintatoteumat])
  (:use [slingshot.slingshot :only [throw+]]))

(defn kirjaa-tiemerkintatoteuma [db kayttaja {urakka-id :id} {tiemerkintatoteumat :tiemerkintatoteumat}]
  (let [urakka-id (Integer/parseInt urakka-id)]
    (log/info (format "Kirjataan urakalle (id: %s) tiemerkintätoteumia käyttäjän (%s) toimesta. Toteumat: %s."
                      urakka-id
                      kayttaja
                      tiemerkintatoteumat))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (tiemerkintatoteumat/luo-tai-paivita-tiemerkintatoteumat db kayttaja urakka-id nil tiemerkintatoteumat)
    (tee-kirjausvastauksen-body {:ilmoitukset "Tiemerkintätoteuma kirjattu onnistuneesti"})))

(defn poista-tiemerkintatoteuma [db kayttaja {urakka-id :id} {toteumien-tunnisteet :toteumien-tunnisteet}]
  (let [urakka-id (Integer/parseInt urakka-id)]
    (log/info (format "Poistetaan urakalle (id: %s) tiemerkintätoteumia käyttäjän (%s) toimesta. Toteumientunnisteet: %s."
                      urakka-id
                      kayttaja
                      toteumien-tunnisteet))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [poistettujen-maara (tiemerkintatoteumat/poista-tiemerkintatoteumat db kayttaja urakka-id toteumien-tunnisteet)
          ilmoitukset (if (and (not (nil? poistettujen-maara)) (pos? poistettujen-maara))
                        (format "Toteumat poistettu onnistuneesti. Poistettiin: %s toteumaa." poistettujen-maara)
                        "Tunnisteita vastaavia toteumia ei löytynyt käyttäjän kirjaamista toteumista.")]
      (tee-kirjausvastauksen-body {:ilmoitukset ilmoitukset}))))

(def palvelut
  [{:palvelu :kirjaa-tiemerkintatoteuma
    :polku "/api/urakat/:id/toteumat/tiemerkinta"
    :tyyppi :POST
    :kutsu-skeema json-skeemat/urakan-tiemerkintatoteuman-kirjaus-request
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (kirjaa-tiemerkintatoteuma db kayttaja parametrit data))}
   {:palvelu :poista-tiemerkintatoteuma
    :polku "/api/urakat/:id/toteumat/tiemerkinta"
    :tyyppi :DELETE
    :kutsu-skeema json-skeemat/pistetoteuman-poisto
    :vastaus-skeema json-skeemat/kirjausvastaus
    :kasittely-fn (fn [parametrit data kayttaja db]
                    (poista-tiemerkintatoteuma db kayttaja parametrit data))}])

(defrecord Tiemerkintatoteuma []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (palvelut/julkaise http db integraatioloki palvelut)
    this)
  (stop [{http :http-palvelin :as this}]
    (palvelut/poista http palvelut)
    this))

