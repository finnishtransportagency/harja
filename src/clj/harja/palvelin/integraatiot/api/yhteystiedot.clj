(ns harja.palvelin.integraatiot.api.yhteystiedot
  "Urakan yhteystietojen hallinta"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [compojure.core :refer [GET]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu-async tee-kirjausvastauksen-body]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-timestamp]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-kirjausvastauksen-body]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn hae-urakan-yhteystiedot [db fim {urakkanro :urakkanro} kayttaja]
  ;; tarkista, että käyttäjän organisaatio on Liikennevirasto, jos ei heitä poikkeus

  (when-not (yhteyshenkilot/liikenneviraston-jarjestelma? db (:kayttajatunnus kayttaja)))
  (if-let [{id :id sampoid :sampoid} (urakat/hae-urakka-urakkanumerolla db urakkanro)]
    (let [fim-yhteyshenkilot (fim/hae-urakan-kayttajat fim sampoid)
          harja-yhteyshenkilot (yhteyshenkilot/hae-urakan-yhteyshenkilot db id)])
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+tuntematon-urakka-koodi+
                        :viesti (format "Urakkanumerolla: %s ei löydy urakkaa Harjassa." urakkanro)}]})))



(defrecord Yhteystiedot []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :fim fim :integraatioloki :as this}]
    (julkaise-reitti
      http :hae-yhteystiedot
      (GET "/api/urakat/yhteystiedot/:urakkanro" request
           (kasittele-kutsu-async
             db
             integraatioloki
             :hae-yhteystiedot
             request
             nil
             json-skeemat/urakan-yhteystietojen-haku-vastaus
             (fn [parametit _ kayttaja db]
               (hae-urakan-yhteystiedot db fim parametit kayttaja)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-yhteystiedot)
    this))
