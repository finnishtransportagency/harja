(ns harja.palvelin.api.tarkastukset
  "Tarkastusten kirjaaminen urakalle"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.api.tyokalut.validointi :as validointi]
            [harja.palvelin.api.tyokalut.json :as json]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.tarkastukset :as tarkastukset]
            [clojure.java.jdbc :as jdbc]
            [harja.geo :as geo]
            [slingshot.slingshot :refer [try+ throw+]]))


(defn kirjaa-tarkastus [db kayttaja tyyppi {id :id} tarkastus]
  (let [urakka-id (Long/parseLong id)
        ulkoinen-id (-> tarkastus :tunniste :id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (log/info "TARKASTUS TULOSSA: " tarkastus "; käyttäjä: " kayttaja)
    (jdbc/with-db-transaction [db db]
      (let [tarkastus-id (first (tarkastukset/hae-tarkastus-ulkoisella-idlla db ulkoinen-id (:id kayttaja)))
            uusi? (nil? tarkastus-id)]

        ;; FIXME: havainto mukaan


        
        (let [id (tarkastukset/luo-tai-paivita-tarkastus
                  db kayttaja urakka-id nil ;; HAVAINTO nil
                  {:id tarkastus-id
                   :aika (json/parsi-aika (:paivamaara tarkastus))
                   :tarkastaja (json/henkilo->nimi (:tarkastaja tarkastus))
                   :mittaaja (json/henkilo->nimi (-> tarkastus :mittaus :mittaaja))
                   :tr (json/sijainti->tr (:sijainti tarkastus))
                   :sijainti (json/sijainti->point (:sijainti tarkastus))})]

          (case tyyppi
            :talvihoito (tarkastukset/luo-tai-paivita-talvihoitomittaus
                         db id uusi? (:mittaus tarkastus))
                                                                        
            :soratie  (tarkastukset/luo-tai-paivita-soratiemittaus
                       db id uusi? (:mittaus tarkastus))
            nil)

          
          )))))



;; Määritellään tarkastustyypit, joiden lisäämiselle tehdään API palvelut
(def tarkastukset
  [{:palvelu :api-lisaa-tiestotarkastus
    :polku "/api/urakat/:id/tarkastus/tiestotarkastus"
    :skeema skeemat/+tiestotarkastuksen-kirjaus+
    :tyyppi :tiesto}
   {:palvelu :api-lisaa-talvihoitotarkastus
    :polku "/api/urakat/:id/tarkastus/talvihoitotarkastus"
    :skeema skeemat/+talvihoitotarkastuksen-kirjaus+
    :tyyppi :talvihoito}
   {:palvelu :api-lisaa-soratietarkastus
    :polku "/api/urakat/:id/tarkastus/soratietarkastus"
    :skeema skeemat/+soratietarkastuksen-kirjaus+
    :tyyppi :soratie}])

(defrecord Tarkastukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (doseq [{:keys [palvelu polku skeema tyyppi]} tarkastukset]
      (julkaise-reitti
       http palvelu
       (POST polku request
             (kasittele-kutsu db palvelu request
                              skeema skeemat/+kirjausvastaus+
                              (fn [parametrit data kayttaja]
                                (kirjaa-tarkastus  db kayttaja tyyppi parametrit data))))))
    
    this)

  (stop [{http :http-palvelin :as this}]
    (apply poista-palvelut http (map :palvelu tarkastukset))
    this))
