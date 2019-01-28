(ns harja.palvelin.integraatiot.api.tyokoneenseuranta
  (:require [compojure.core :refer [POST]]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [harja.kyselyt.tyokoneseuranta :as tks]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [taoensso.timbre :as log]
            [clojure.data.json :as json]))

(def +tyokone-seurantakirjaus-url+ "/api/seuranta/tyokone")

(defn arrayksi [db v]
  (with-open [conn (.getConnection (:datasource db))]
    (.createArrayOf conn "text" (to-array v))))


(defn- tallenna-tyokoneen-koordinaatti
  "Tallentaa työkoneen sijainnin pistegeometriana."
  [db data havainto urakka-id]
  (validointi/tarkista-koordinaattien-jarjestys (get-in havainto [:havainto :sijainti :koordinaatit]))
  (tks/tallenna-tyokonehavainto<!
    db
    {:jarjestelma (get-in data [:otsikko :lahettaja :jarjestelma])
     :organisaationimi (get-in data [:otsikko :lahettaja :organisaatio :nimi])
     :ytunnus (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])
     :viestitunniste (get-in data [:otsikko :viestintunniste :id])
     :lahetysaika (get-in data [:otsikko :lahetysaika])
     :tyokoneid (get-in havainto [:havainto :tyokone :id])
     :tyokonetyyppi (get-in havainto [:havainto :tyokone :tyokonetyyppi])
     :xkoordinaatti (get-in havainto [:havainto :sijainti :koordinaatit :x])
     :ykoordinaatti (get-in havainto [:havainto :sijainti :koordinaatit :y])
     :suunta (get-in havainto [:havainto :suunta])
     :urakkaid urakka-id
     :tehtavat (arrayksi db (get-in havainto [:havainto :suoritettavatTehtavat]))}))

(defn- tallenna-tyokoneen-reitti
  "Tallentaa työkoneen sijainnin viivageometriana."
  [db data havainto urakka-id]
  (tks/tallenna-tyokonehavainto-viivageometrialla<!
    db
    {:jarjestelma (get-in data [:otsikko :lahettaja :jarjestelma])
     :organisaationimi (get-in data [:otsikko :lahettaja :organisaatio :nimi])
     :ytunnus (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])
     :viestitunniste (get-in data [:otsikko :viestintunniste :id])
     :lahetysaika (get-in data [:otsikko :lahetysaika])
     :tyokoneid (get-in havainto [:havainto :tyokone :id])
     :tyokonetyyppi (get-in havainto [:havainto :tyokone :tyokonetyyppi])
     :viivageometria (json/write-str (get-in havainto [:havainto :sijainti :viivageometria]))
     :suunta (get-in havainto [:havainto :suunta])
     :urakkaid urakka-id
     :tehtavat (arrayksi db (get-in havainto [:havainto :suoritettavatTehtavat]))}))


(defn- tallenna-seurantakirjaus [_ data kayttaja db]
  (validointi/tarkista-onko-kayttaja-organisaation-jarjestelma db
                                                               (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])
                                                               kayttaja)
  (doseq [havainto (:havainnot data)]
    (let [urakka-id (get-in havainto [:havainto :urakkaid])
          tallenna-havainto (if (= nil (get-in havainto [:havainto :sijainti :viivageometria]))
                              tallenna-tyokoneen-koordinaatti
                              tallenna-tyokoneen-reitti)]
      (when urakka-id (validointi/tarkista-urakka db urakka-id)
                      (tallenna-havainto db data havainto urakka-id))))
  (tee-kirjausvastauksen-body {:ilmoitukset "Kirjauksen tallennus onnistui"}))

(defrecord Tyokoneenseuranta []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-reitti http :tallenna-tyokoneenseurantakirjaus
                     (POST +tyokone-seurantakirjaus-url+ request
                       (kasittele-kutsu db nil
                                        :tallenna-tyokoneenseurantakirjaus
                                        request json-skeemat/tyokoneenseuranta-kirjaus json-skeemat/kirjausvastaus
                                        tallenna-seurantakirjaus)))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :tallenna-tyokoneenseurantakirjaus)
    this))
