(ns harja.palvelin.integraatiot.api.tyokoneenseuranta
  (:require [compojure.core :refer [POST]]
            [com.stuartsierra.component :as component]
            [clojure.data.json :as json]
            [harja.kyselyt.tyokoneseuranta :as tks]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [taoensso.timbre :as log]))

(def +tyokone-seurantakirjaus-url+ "/api/seuranta/tyokone")
(def +tyokone-reitti-url+ "/api/seuranta/tyokone/reitti")

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
  (validointi/viivageometria-annettu havainto)
  (doseq [koordinaatit (get-in havainto [:havainto :sijainti :viivageometria :coordinates])]
    (validointi/tarkista-koordinaattien-jarjestys koordinaatit))
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

(defn- tallenna-seurantakirjaus-viivageometriana [_ data kayttaja db]
  (validointi/tarkista-onko-kayttaja-organisaation-jarjestelma db
                                                               (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])
                                                               kayttaja)
  (doseq [havainto (:havainnot data)]
    (let [urakka-id (get-in havainto [:havainto :urakkaid])]
      (when urakka-id (validointi/tarkista-urakka db urakka-id))
      (tallenna-tyokoneen-reitti db data havainto urakka-id)))
  (tee-kirjausvastauksen-body {:ilmoitukset "Kirjauksen tallennus onnistui"}))

(defn- tallenna-seurantakirjaus [_ data kayttaja db]
  (validointi/tarkista-onko-kayttaja-organisaation-jarjestelma db
                                                               (get-in data [:otsikko :lahettaja :organisaatio :ytunnus])
                                                               kayttaja)
  (doseq [havainto (:havainnot data)]
    (let [urakka-id (get-in havainto [:havainto :urakkaid])]
      ;; Validointi nakkaa poikkeuksen, jos urakka-id ei ole validi
      (when urakka-id (validointi/tarkista-urakka db urakka-id))
      (tallenna-tyokoneen-koordinaatti db data havainto urakka-id)))
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
    (julkaise-reitti http :tallenna-tyokoneen-reitti
                     (POST +tyokone-reitti-url+ request
                       (kasittele-kutsu db nil
                                        :tallenna-tyokoneen-reitti
                                        request json-skeemat/tyokoneenseuranta-kirjaus json-skeemat/kirjausvastaus
                                        tallenna-seurantakirjaus-viivageometriana)))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :tallenna-tyokoneenseurantakirjaus
                          :tallenna-tyokoneen-reitti)
    this))
