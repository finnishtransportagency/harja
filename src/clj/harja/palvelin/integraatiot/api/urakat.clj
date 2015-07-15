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
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+]]))

(defn muodosta-kokonaishintaiset-tyot [tyot]
  (for [{:keys [id vuosi kuukausi summa tpi_id tpi_nimi] :as tyo} tyot]
    {:kokonaishintainenTyo
     {:id       id
      :tehtavat [{:tehtava {:id tpi_id :selite tpi_nimi}}]
      :vuosi    vuosi
      :kuukausi kuukausi
      :summa    summa}}))

(defn muodosta-yksikkohintaiset-tyot [tyot]
  (for [{:keys [id alkupvm loppupvm maara yksikko yksikkohinta tehtavan_id tehtavan_nimi] :as tyo} tyot]
    {:yksikkohintainenTyo
     {:id           id
      :tehtava      {:id tehtavan_id :selite tehtavan_nimi} ;; 4. tason tehtava
      :alkupvm      alkupvm
      :loppupvm     loppupvm
      :maara        maara
      :yksikko      yksikko
      :yksikkohinta yksikkohinta}}))

(defn muodosta-materiaalin-kaytot [materiaalit]
  (for [{:keys [id alkupvm loppupvm maara materiaali] :as mat} (map konv/alaviiva->rakenne materiaalit)]
    {:materiaalinKaytto
     {:materiaali
      ;; FIXME: Tämä lista pitää tarkistaa Annelta, materiaali UI:n puolella on kommentoitu,
      ;; että vain suolankäyttö kiinnostaa... turha naulata tätä listaa APIin kiinni, jos sen
      ;; on asiakas jo huonoksi todennut.
                (case (:nimi materiaali)
                  "Talvisuolaliuos NaCl" "talvisuolaliuosNaCl"
                  "Talvisuolaliuos CaCl2" "talvisuolaliuosCaCl2"
                  "Erityisalueet NaCl" "erityisalueetNaCl"
                  "Erityisalueet NaCl-liuos" "erityisalueetNaClLiuos"
                  "Hiekoitushiekka" "hiekoitushiekka"
                  "Kaliumformiaatti" "kaliumformiaatti"

                  ;; default
                  "Talvisuolaliuos NaCl")
      :maara    {:yksikko (:yksikko materiaali)
                 :maara   maara}
      :alkupvm  alkupvm
      :loppupvm loppupvm}}))

(defn hae-urakan-sopimukset [db urakka-id]
  (let [sopimukset (urakat/hae-urakan-sopimukset db urakka-id)
        kokonaishintaiset (group-by :sopimus (kokonaishintaiset-tyot/listaa-kokonaishintaiset-tyot db urakka-id))
        yksikkohintaiset (group-by :sopimus (yksikkohintaiset-tyot/listaa-urakan-yksikkohintaiset-tyot db urakka-id))
        materiaalit (group-by :sopimus (materiaalit/hae-urakan-materiaalit db urakka-id))]
    (for [sopimus sopimukset]
      {:sopimus (assoc sopimus
                  :kokonaishintaisetTyot (muodosta-kokonaishintaiset-tyot (get kokonaishintaiset (:id sopimus)))
                  :yksikkohintaisetTyot (muodosta-yksikkohintaiset-tyot (get yksikkohintaiset (:id sopimus)))
                  :materiaalinKaytot (muodosta-materiaalin-kaytot (get materiaalit (:id sopimus))))})))

(defn muodosta-vastaus-hae-urakka-idlla [db id urakka]
  {:urakka
   {:tiedot     (assoc urakka                               ; perustiedot (pl. väylämuoto) tulevat suoraan hae-urakka kyselystä
                  :vaylamuoto "tie")
    :sopimukset (hae-urakan-sopimukset db id)}})

(defn muodosta-vastaus-hae-urakka-ytunnuksella [db urakat]
  {:urakat (mapv #({:urakka
                    {:tiedot (assoc %
                               :vaylamuoto "tie")}}) urakat)})

(defn hae-urakka-idlla [db {:keys [id]} kayttaja]
  (let [urakka-id (Integer/parseInt id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [urakka (some->> urakka-id (urakat/hae-urakka db) first konv/alaviiva->rakenne)]
      (log/debug "Urakka haettu: " urakka)
      (muodosta-vastaus-hae-urakka-idlla db urakka-id urakka))))

(defn hae-urakka-ytunnuksella [db {:keys [ytunnus]} kayttaja]
  ;(validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja) FIXME Validointi puuttuu, tarkista että käyttäjällä on oikeus urakoihin?
  (log/debug "Haetaan urakat ytynnuksella " ytunnus)
  (let [urakat (some->> ytunnus (urakat/hae-urakat-ytunnuksella db) konv/vector-mappien-alaviiva->rakenne)]
    (log/debug "Urakat haettu: " urakat)
    (muodosta-vastaus-hae-urakka-ytunnuksella db urakat)))

(def hakutyypit
  [{:palvelu        :hae-urakka
    :polku          "/api/urakat/:id"
    :vastaus-skeema skeemat/+urakan-haku-vastaus+
    :kasittely-fn   (fn [parametrit data kayttaja-id db] (hae-urakka-idlla db parametrit kayttaja-id))}
   {:palvelu        :hae-urakka-ytunnuksella
    :polku          "/api/urakat/haku/:ytunnus"
    :vastaus-skeema skeemat/+urakoiden-haku-vastaus+
    :kasittely-fn   (fn [parametrit data kayttaja-id db] (hae-urakka-ytunnuksella db parametrit kayttaja-id))}])

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
    (poista-palvelut http :hae-urakka)
    this))