(ns harja.palvelin.integraatiot.api.tyokoneenseuranta
  (:require [compojure.core :refer [POST]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tyokoneseuranta :as tks]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]))

(def +tyokone-seurantakirjaus-url+ "/api/seuranta/tyokone")

(defn arrayksi [db v]
  (with-open [conn (.getConnection (:datasource db))]
    (.createArrayOf conn "text" (to-array v))))

(defn- tallenna-seurantakirjaus [_ data kayttaja db]
  (validointi/tarkista-onko-kayttaja-organisaation-jarjestelma db
                                                               (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])
                                                               kayttaja)
  (doseq [havainto (:havainnot data)]
    (let [urakka-id (get-in havainto [:havainto :urakkaid])]
      (when urakka-id (validointi/tarkista-urakka db urakka-id))
      (tks/tallenna-tyokonehavainto db
                                    (get-in data [:otsikko :lahettaja :jarjestelma])
                                    (get-in data [:otsikko :lahettaja :organisaatio :nimi])
                                    (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])
                                    (get-in data [:otsikko :viestintunniste :id])
                                    (get-in data [:otsikko :lahetysaika])
                                    (get-in havainto [:havainto :tyokone :id])
                                    (get-in havainto [:havainto :tyokone :tyokonetyyppi])
                                    (get-in havainto [:havainto :sijainti :koordinaatit :x])
                                    (get-in havainto [:havainto :sijainti :koordinaatit :y])
                                    (get-in havainto [:havainto :suunta])
                                    urakka-id
                                    (arrayksi db (get-in havainto [:havainto :suoritettavatTehtavat])))))
  {:ilmoitukset "Kirjauksen tallennus onnistui"})

(defrecord Tyokoneenseuranta []
  component/Lifecycle
  (start [{http :http-palvelin
           db   :db :as this}]
    (julkaise-reitti http :tallenna-tyokoneenseurantakirjaus
                     (POST +tyokone-seurantakirjaus-url+ request
                       (kasittele-kutsu db nil
                                        :tallenna-tyokoneenseurantakirjaus
                                        request json-skeemat/+tyokoneenseuranta-kirjaus+ json-skeemat/+kirjausvastaus+
                                        tallenna-seurantakirjaus)))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :tallenna-tyokoneenseurantakirjaus)
    this))
