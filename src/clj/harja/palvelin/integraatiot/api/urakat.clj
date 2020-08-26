(ns harja.palvelin.integraatiot.api.urakat
  "Urakan yleistietojen API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.toimenpidekoodit :as q-toimenpidekoodit]
            [harja.kyselyt.materiaalit :as q-materiaalit]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.validointi.parametrit :as parametrivalidointi])
  (:use [slingshot.slingshot :only [throw+]]))

(defn hae-tehtavat [db]
  (let [yksikkohintaiset-tehtavat (q-toimenpidekoodit/hae-apin-kautta-seurattavat-yksikkohintaiset-tehtavat db)
        kokonaishintaiset-tehtavat (q-toimenpidekoodit/hae-apin-kautta-seurattavat-kokonaishintaiset-tehtavat db)
        tee-tehtavat #(mapv (fn [data] {:tehtava {:id (:apitunnus data) :selite (:nimi data) :yksikko (:yksikko data)}}) %)]
    (merge
      {:yksikkohintaiset (tee-tehtavat yksikkohintaiset-tehtavat)}
      {:kokonaishintaiset (tee-tehtavat kokonaishintaiset-tehtavat)})))

(defn hae-urakan-sopimukset [db urakka-id]
  (let [sopimukset (q-urakat/hae-urakan-sopimukset db urakka-id)]
    (for [sopimus sopimukset]
      {:sopimus sopimus})))

(defn hae-materiaalit [db]
  (let [materiaalit (q-materiaalit/hae-kaikki-materiaalit db)]
    (for [materiaali materiaalit]
      {:materiaali {:nimi (:nimi materiaali) :yksikko (:yksikko materiaali)}})))

(defn- urakan-tiedot [urakka]
  (let [urakka (if (= "teiden-hoito"(:tyyppi urakka))
                 (assoc urakka :tyyppi "hoito")
                                urakka)]
              (-> urakka
                  (select-keys #{:id :nimi :tyyppi :alkupvm :loppupvm
                                 :takuu_loppupvm :alueurakkanumero :urakoitsija})
                  (assoc :vaylamuoto "tie"))))

(defn muodosta-vastaus-urakan-haulle [db id urakka]
  {:urakka
   {:tiedot      (urakan-tiedot urakka)
    :sopimukset  (hae-urakan-sopimukset db id)
    :materiaalit (hae-materiaalit db)
    :tehtavat    (hae-tehtavat db)}})

(defn muodosta-vastaus-urakoiden-haulle [urakat]
  {:urakat (mapv (fn [urakka] {:urakka {:tiedot (urakan-tiedot urakka)}}) urakat)})

(defn hae-urakka-idlla [db {:keys [id]} kayttaja]
  (log/debug "Haetaan urakka id:llä: " id)
  (let [urakka-id (Integer/parseInt id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [urakka (some->> urakka-id (q-urakat/hae-urakka db) first konv/alaviiva->rakenne)]
      (muodosta-vastaus-urakan-haulle db urakka-id urakka))))

(defn hae-kayttajan-urakat [db parametrit {:keys [kayttajanimi] :as kayttaja}]
  (log/debug (format "Haetaan käyttäjän: %s urakat" kayttaja))
  (let [urakkatyyppi (get parametrit "urakkatyyppi")]
    (validointi/tarkista-urakkatyyppi urakkatyyppi)
    (muodosta-vastaus-urakoiden-haulle
      (konv/vector-mappien-alaviiva->rakenne
        (q-urakat/hae-jarjestelmakayttajan-urakat db kayttajanimi urakkatyyppi)))))

(defn hae-urakka-ytunnuksella [db parametrit {:keys [kayttajanimi] :as kayttaja}]
  (parametrivalidointi/tarkista-parametrit parametrit {:ytunnus "Y-tunnus puuttuu"})
  (let [{ytunnus :ytunnus} parametrit]
    (log/debug "Haetaan urakat y-tunnuksella: " ytunnus)
    (validointi/tarkista-onko-kayttaja-organisaatiossa db ytunnus kayttaja)
    (let [organisaation-urakat (q-urakat/hae-urakat-ytunnuksella db ytunnus)
          erillisoikeus-urakat (filter (fn [eu] (not-any? (fn [ou] (= (:id ou) (:id eu))) organisaation-urakat))
                                       (q-urakat/hae-urakat-joihin-jarjestelmalla-erillisoikeus db kayttajanimi))
          urakat (konv/vector-mappien-alaviiva->rakenne (into organisaation-urakat erillisoikeus-urakat))]
      (muodosta-vastaus-urakoiden-haulle urakat))))

(def hakutyypit
  [{:palvelu        :hae-urakka
    :polku          "/api/urakat/:id"
    :vastaus-skeema json-skeemat/urakan-haku-vastaus
    :kasittely-fn   (fn [parametrit _ kayttaja-id db]
                      (hae-urakka-idlla db parametrit kayttaja-id))}
   {:palvelu        :hae-kayttajan-urakat
    :polku          "/api/urakat/haku/"
    :vastaus-skeema json-skeemat/urakoiden-haku-vastaus
    :kasittely-fn   (fn [parametrit _ kayttaja db]
                      (hae-kayttajan-urakat db parametrit kayttaja))}
   {:palvelu        :hae-urakka-ytunnuksella
    :polku          "/api/urakat/haku/:ytunnus"
    :vastaus-skeema json-skeemat/urakoiden-haku-vastaus
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
    (poista-palvelut http :hae-urakka :hae-urakka-ytunnuksella :hae-kayttajan-urakat)
    this))
