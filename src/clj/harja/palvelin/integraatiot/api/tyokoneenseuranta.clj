(ns harja.palvelin.integraatiot.api.tyokoneenseuranta
  (:require [clojure.string :as str]
            [compojure.core :refer [POST]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tyokoneseuranta :as tks]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [taoensso.timbre :as log]))

(def +tyokone-seurantakirjaus-url+ "/api/seuranta/tyokone")

(defn arrayksi [db v]
  (with-open [conn (.getConnection (:datasource db))]
    (.createArrayOf conn "text" (to-array v))))

(defn- tallenna-seurantakirjaus [parametrit data kayttaja db]
  (doseq [havainto (:havainnot data)]
    (tks/tallenna-tyokonehavainto db
                                   (get-in data [:otsikko :lahettaja :jarjestelma])
                                   (get-in data [:otsikko :lahettaja :organisaatio :nimi])
                                   (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])
                                   (get-in data [:otsikko :viestintunniste :id])
                                   (get-in data [:otsikko :lahetysaika])
                                   (get-in havainto [:havainto :tyokone :id])
                                   (get-in havainto [:havainto :tyokone :tyyppi])
                                   (get-in havainto [:havainto :sijainti :koordinaatit :x])
                                   (get-in havainto [:havainto :sijainti :koordinaatit :y])
                                   (get-in havainto [:havainto :urakkaid])
                                   (get-in havainto [:havainto :sopimusid])
                                   (arrayksi db (get-in havainto [:havainto :tehtavat]))))
  {:ilmoitukset "Havainnon tallennus onnistui"})

(defrecord Tyokoneenseuranta []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db
           integraatioloki :integraatioloki :as this}]
    (julkaise-reitti http :tallenna-tyokoneenseurantakirjaus
                      (POST +tyokone-seurantakirjaus-url+ request
                            (kasittele-kutsu db integraatioloki
                                             :tallenna-tyokoneenseurantakirjaus
                                             request skeemat/+tyokoneenseuranta-kirjaus+ skeemat/+kirjausvastaus+
                                             tallenna-seurantakirjaus)))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :tallenna-tyokoneenseurantakirjaus)
    this))
