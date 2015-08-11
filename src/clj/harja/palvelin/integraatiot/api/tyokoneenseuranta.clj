(ns harja.palvelin.integraatiot.api.tyokoneenseuranta
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(def +tyokone-seurantakirjaus-url+ "/api/seuranta/tyokone")

(defn- tallenna-seurantakirjaus [parametrit data kayttaja db]
  {:ilmoitukset "Hello world"})

(defrecord Tyokoneenseuranta []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           integraatioloki :integraatioloki :as this}]
    (julkaise-palvelu http :tallenna-tyokoneenseurantakirjaus
                      (POST +tyokone-seurantakirjaus-url+ request
                            (kasittele-kutsu db integraatioloki
                                             :tallenna-tyokoneenseurantakirjaus
                                             request skeemat/+tyokoneenseuranta-kirjaus+ skeemat/+kirjausvastaus+
                                             tallenna-seurantakirjaus)))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :tallenna-tyokoneenseurantakirjaus)
    this))
