(ns harja.palvelin.integraatiot.api.taitorakennerekisteri
  "Taitorakennerekisterin endpointit"
  (:require [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [compojure.core :refer [GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-get-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.parametrit :as parametrit]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [taoensso.timbre :as log])
  (:import (java.text SimpleDateFormat))
  (:use [slingshot.slingshot :only [throw+]]))

(s/def ::alkuaika #(and (string? %) (>= (count %) 20) (or
                                                        (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) %))
                                                        (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muotoZ) %)))))
(s/def ::loppuaika #(and (string? %) (>= (count %) 20) (or
                                                         (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muoto) %))
                                                         (inst? (.parse (SimpleDateFormat. parametrit/pvm-aika-muotoZ) %)))))

(defn- tarkista-haun-parametrit [parametrit]
  (try
    (s/valid? ::loppuaika (:loppuaika parametrit))
    (s/valid? ::alkuaika (:alkuaika parametrit))
    (parametrivalidointi/tarkista-parametrit
      parametrit
      {:alkuaika "Alkuaika puuttuu"
       :loppuaika "Loppuaika puuttuu"})
    (catch Exception e
      (log/error "Virhe Taitorakennerekisteri-api kutsussa:" e)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+puutteelliset-parametrit+
                          :viesti "Poikkeus annetuissa parametreissa. Anna päivämäärät muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03"}]})))
  (when (not (s/valid? ::alkuaika (:alkuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Alkuaika väärässä muodossa: %s Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03" (:alkuaika parametrit))}))
  (when (not (s/valid? ::loppuaika (:loppuaika parametrit)))
    (virheet/heita-viallinen-apikutsu-poikkeus
      {:koodi virheet/+puutteelliset-parametrit+
       :viesti (format "Loppuaika väärässä muodossa: %s Anna muodossa: yyyy-MM-dd'T'HH:mm:ss esim: 2005-01-01T00:00:00+03" (:loppuaika parametrit))})))

(defn hae-siltatarkastukset
  "Hakee siltatarkastukset annettujen alku- ja loppuajan puitteissa."
  ;;TODO Toteuta oikea tietokantakysely
  [db {:keys [alkuaika loppuaika] :as parametrit} kayttaja]
  (log/info "Taitorakennerekisteri API, siltatarkastusten haku, parametrit: " (pr-str parametrit))
  (tarkista-haun-parametrit parametrit)
  ;; Palautetaan esimerkkidata  
  (json/parse-string (slurp (io/resource "api/examples/trex-siltatarkastukset-haku-response.json")) true))

(defrecord Taitorakennerekisteri []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :hae-siltatarkastukset
      (GET "/api/taitorakennerekisteri/siltatarkastukset/:alkuaika/:loppuaika" parametrit
        (kasittele-get-kutsu db integraatioloki :hae-siltatarkastukset parametrit
          json-skeemat/+taitorakennerekisteri-siltatarkastukset-haku-vastaus+
          (fn [parametrit kayttaja db]
            (hae-siltatarkastukset db parametrit kayttaja))
          :taitorakenne "trex")))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-siltatarkastukset)
    this))
