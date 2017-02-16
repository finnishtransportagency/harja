(ns harja.palvelin.integraatiot.api.ping
  "Tarkastusten kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [clojure.string :refer [join]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.kyselyt.status :as q-status]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))

(defn tarkista-yhteydet [db]
  (when (not (= 3 (q-status/tarkista-kantayhteys db)))
    (throw+ {:type virheet/+sisainen-kasittelyvirhe+
             :virheet [{:koodi virheet/+tietokanta-yhteys-poikki+
                        :viesti "Tietokantayhteys on poikki"}]}))

  (tee-kirjausvastauksen-body {:ilmoitukset "pong"}))

(defrecord Ping []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :api-ping
      (GET "/api/ping" request
        (kasittele-kutsu db integraatioloki :ping-sisaan request nil json-skeemat/kirjausvastaus
                         (fn [_ _ _ _]
                           (tarkista-yhteydet db)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :api-ping)
    this))
