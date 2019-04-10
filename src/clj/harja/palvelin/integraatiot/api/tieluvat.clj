(ns harja.palvelin.integraatiot.api.tieluvat
  "Tielupien hallinta API:n kautta"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE]]
            [clojure.string :refer [join]]
            [slingshot.slingshot :refer [try+ throw+]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :refer [tallenna-liitteet-tarkastukselle]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.sanomat.tielupa-sanoma :as tielupa-sanoma]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet]
            [harja.domain.tielupa :as tielupa]
            [harja.kyselyt.tielupa :as tielupa-q]
            [harja.kyselyt.kayttajat :as kayttajat-q]
            [harja.kyselyt.tieverkko :as tieverkko-q]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (java.util Date)))

(defn hae-sijainti [db sijainti]
  (let [parametrit {:tie (::tielupa/tie sijainti)
                    :aosa (::tielupa/aosa sijainti)
                    :aet (::tielupa/aet sijainti)
                    :losa (::tielupa/losa sijainti)
                    :loppuet (::tielupa/let sijainti)}
        geometria (if (and (:losa parametrit) (:loppuet parametrit))
                    (tieverkko-q/tierekisteriosoite-viivaksi db parametrit)
                    (tieverkko-q/tierekisteriosoite-pisteeksi db parametrit))]
    (assoc sijainti ::tielupa/geometria geometria)))

(defn- tarkista-datan-oikeellisuus
  [tielupa]
  (cond
    (some #(= (::tielupa/laite %) "Muuntamo") (::tielupa/kaapeliasennukset tielupa))
    (if (pvm/jalkeen? (java.util.Date. (.getTime (::tielupa/myontamispvm tielupa)))
                      (pvm/luo-pvm 2015 0 1))
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+tieluvan-data-vaarin+
                          :viesti (str "Kaapeliasennusluvalle merkattu laitteeksi 'Muuntamo' vaikka myöntämispäivämäärä (" (pvm/pvm (java.util.Date. (.getTime (::tielupa/myontamispvm tielupa)))) ") on merkattu 1.1.2015 jälkeen")}]})
      tielupa)
    :else tielupa))

(defn hae-tieluvan-sijainnit [db tielupa]
  (let [sijainnit (::tielupa/sijainnit tielupa)]
    (if (empty? sijainnit)
      tielupa
      (assoc tielupa ::tielupa/sijainnit (map #(hae-sijainti db %) sijainnit)))))

(defn hae-sijainnit-avaimella [db avain tielupa]
  (assoc tielupa avain (mapv #(hae-sijainti db %) (get tielupa avain))))

;; Tieluvilla on enemmän ely-keskuksia kuin Harjassa muuten
(defn hae-ely [db ely tielupa]
  (let [ely-numero (case ely
                     "Uusimaa" 1
                     "Keski-Suomi" 9
                     "Lappi" 14
                     "Etelä-Pohjanmaa" 10
                     "Pohjois-Pohjanmaa" 12
                     "Kaakkois-Suomi" 3
                     "Varsinais-Suomi" 2
                     "Pohjois-Savo" 8
                     "Pirkanmaa" 4
                     "Ahvenanmaa" 16
                     "Etelä-Savo" 7
                     "Häme" 4
                     "Kainuu" 14
                     "Pohjanmaa" 12
                     "Pohjois-Karjala" 9
                     "Satakunta" 3
                     (throw+ {:type virheet/+viallinen-kutsu+
                              :virheet [{:koodi virheet/+tuntematon-ely+
                                         :viesti (str "Tuntematon ELY " ely)}]}))
        ely-id (:id (first (kayttajat-q/hae-ely-numerolla-tielupaa-varten db ely-numero)))]
    (assoc tielupa ::tielupa/ely ely-id)))

(defn kirjaa-tielupa [liitteiden-hallinta db data kayttaja]
  (validointi/tarkista-onko-liikenneviraston-jarjestelma db kayttaja)
  (->> (tielupa-sanoma/api->domain (:tielupa data))
       (tarkista-datan-oikeellisuus)
       (hae-tieluvan-sijainnit db)
       (hae-sijainnit-avaimella db ::tielupa/kaapeliasennukset)
       (hae-sijainnit-avaimella db ::tielupa/mainokset)
       (hae-sijainnit-avaimella db ::tielupa/opasteet)
       (hae-sijainnit-avaimella db ::tielupa/liikennemerkkijarjestelyt)
       (hae-sijainnit-avaimella db ::tielupa/johtoasennukset)
       (hae-ely db (get-in data [:tielupa :perustiedot :ely]))
       (tielupa-q/tallenna-tielupa db))

  (let [tielupa (first (tielupa-q/hae-tieluvat db {::tielupa/ulkoinen-tunniste (get-in data [:tielupa :perustiedot :tunniste :id])}))
        tielupa-id (::tielupa/id tielupa)]
    (tielupa-q/aseta-tieluvalle-urakka db tielupa-id)

    (when-let [liitteet (get-in data [:tielupa :liitteet])]
      (liitteet/tallenna-liitteet-tieluvalle db liitteiden-hallinta nil tielupa-id kayttaja liitteet)))

  (tee-kirjausvastauksen-body {:ilmoitukset "Tielupa kirjattu onnistuneesti"}))

(defrecord Tieluvat []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :kirjaa-tielupa
      (POST "/api/tieluvat" request
        (kasittele-kutsu db
                         integraatioloki
                         :kirjaa-tielupa
                         request
                         json-skeemat/tieluvan-kirjaus-request
                         json-skeemat/kirjausvastaus
                         (fn [_ data kayttaja db]
                           (kirjaa-tielupa liitteiden-hallinta db data kayttaja)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :kirjaa-tielupa)
    this))
