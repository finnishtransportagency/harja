(ns harja.palvelin.integraatiot.api.yllapitokohteet
  "Ylläpitokohteiden hallinta"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu-async tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [dekoodaa-base64]]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.sanomat.yllapitokohdesanomat :as yllapitokohdesanomat]
            [harja.kyselyt.yllapitokohteet :as q]
            [harja.kyselyt.konversio :as konv])
  (:use [slingshot.slingshot :only [throw+]]))

(defn hae-yllapitokohteet [db parametit kayttaja]
  (let [urakka-id (Integer/parseInt (:id parametit))]
    (log/debug (format "Haetaan urakan (id: %s) ylläpitokohteet käyttäjälle: %s (id: %s)."
                       urakka-id
                       (:kayttajanimi kayttaja)
                       (:id kayttaja)))
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [yllapitokohteet (into []
                                (map konv/alaviiva->rakenne)
                                (q/hae-urakan-yllapitokohteet-alikohteineen db {:urakka urakka-id}))
          yllapitokohteet (konv/sarakkeet-vektoriin
                            yllapitokohteet
                            {:kohdeosa :alikohteet}
                            :id)]
      (yllapitokohdesanomat/rakenna-kohteet yllapitokohteet))))

(defrecord Yllapitokohteet []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :hae-yllapitokohteet
      (GET "/api/urakat/:id/yllapitokohteet" request
        (kasittele-kutsu-async db
                               integraatioloki
                               :hae-yllapitokohteet
                               request
                               nil
                               json-skeemat/urakan-yllapitokohteiden-haku-vastaus
                               (fn [parametit _ kayttaja db] (hae-yllapitokohteet db parametit kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-yllapitokohteet)
    this))
