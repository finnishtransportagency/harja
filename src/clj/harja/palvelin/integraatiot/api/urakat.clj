(ns harja.palvelin.integraatiot.api.urakat
  "Urakan yleistietojen API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.kyselyt.yksikkohintaiset-tyot :as yksikkohintaiset-tyot]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+]]))

(defn muodosta-tehtavat [tehtavat]
  (mapv (fn [data] {:tehtava {:id (:id data) :selite (:nimi data)}}) tehtavat))

(defn muodosta-toteumakirjauskohteet [sopimus yksikkohintaiset-tehtavat kokonaishintaiset-tehtavat]
  (assoc sopimus :toteumakirjauskohteet (merge
                                          {:yksikkohintaiset (muodosta-tehtavat yksikkohintaiset-tehtavat)}
                                          {:kokonaishintaiset (muodosta-tehtavat kokonaishintaiset-tehtavat)})))

(defn hae-urakan-sopimukset [db urakka-id]
  (let [sopimukset (urakat/hae-urakan-sopimukset db urakka-id)]
    (for [sopimus sopimukset]
      (let [sopimus-id (:id sopimus)
            yksikkohintaiset-tehtavat (yksikkohintaiset-tyot/hae-urakan-sopimuksen-yksikkohintaiset-tehtavat
                                        db urakka-id sopimus-id)
            kokonaishintaiset-tehtavat (kokonaishintaiset-tyot/hae-urakan-sopimuksen-kokonaishintaiset-tehtavat
                                         db urakka-id sopimus-id)]
        {:sopimus (muodosta-toteumakirjauskohteet sopimus
                                                  yksikkohintaiset-tehtavat
                                                  kokonaishintaiset-tehtavat)}))))

(defn muodosta-vastaus-urakan-haulle [db id urakka]
  {:urakka
   {:tiedot     (assoc urakka :vaylamuoto "tie")
    :sopimukset (hae-urakan-sopimukset db id)}})

(defn muodosta-vastaus-organisaation-urakoiden-haulle [urakat]
  {:urakat (mapv (fn [urakka] {:urakka {:tiedot (assoc urakka :vaylamuoto "tie")}}) urakat)})

(defn hae-urakka-idlla [db {:keys [id]} kayttaja]
  (log/debug "Haetaan urakka id:llä: " id)
  (let [urakka-id (Integer/parseInt id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [urakka (some->> urakka-id (urakat/hae-urakka db) first konv/alaviiva->rakenne)]
      (muodosta-vastaus-urakan-haulle db urakka-id urakka))))

(defn hae-urakka-ytunnuksella [db {:keys [ytunnus]} kayttaja]
  (log/debug "Haetaan urakat y-tunnuksella: " ytunnus)
  (validointi/tarkista-onko-kayttaja-organisaatiossa db ytunnus kayttaja)
  (let [urakat (some->> ytunnus (urakat/hae-urakat-ytunnuksella db) konv/vector-mappien-alaviiva->rakenne)]
    (muodosta-vastaus-organisaation-urakoiden-haulle urakat)))

(def hakutyypit
  [{:palvelu        :hae-urakka
    :polku          "/api/urakat/:id"
    :vastaus-skeema skeemat/+urakan-haku-vastaus+
    :kasittely-fn   (fn [parametrit _ kayttaja-id db]
                      (hae-urakka-idlla db parametrit kayttaja-id))}
   {:palvelu        :hae-urakka-ytunnuksella
    :polku          "/api/urakat/haku/:ytunnus"
    :vastaus-skeema skeemat/+urakoiden-haku-vastaus+
    :kasittely-fn   (fn [parametrit _ kayttaja-id db]
                      (hae-urakka-ytunnuksella db parametrit kayttaja-id))}])

(defrecord Urakat []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (doseq [{:keys [palvelu polku vastaus-skeema kasittely-fn]} hakutyypit]
      (julkaise-reitti
        http palvelu
        (GET polku request
          (kasittele-kutsu db integraatioloki palvelu request nil vastaus-skeema kasittely-fn))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :hae-urakka :hae-urakka-ytunnuksella)
    this))