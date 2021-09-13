(ns harja.palvelin.integraatiot.velho.apurit
  (:require [clojure.data.json :as json]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]))

(defn hae-velho-api-token
  "Hakee OAuth2.0 authorization tokenin annetusta `token-url` rajapinasta"
  [token-url kayttajatunnus salasana konteksti]
  (fn []
    (let [otsikot {"Content-Type" "application/x-www-form-urlencoded"}
          http-asetukset {:metodi         :POST
                          :url            token-url
                          :kayttajatunnus kayttajatunnus
                          :salasana       salasana
                          :otsikot        otsikot}
          kutsudata "grant_type=client_credentials"
          vastaus (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)
          vastaus-body (json/read-str (:body vastaus))
          token (get vastaus-body "access_token")]
      token))
  )
