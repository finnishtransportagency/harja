(ns harja.palvelin.palvelut.suunnittelu.tarjous-palvelu
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.toimenkuvat-kyselyt :as toimenkuva-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.suunnittelu.suunnittelu-apurit :as apurit]
            [harja.pvm :as pvm]))

(defn hae-tarjouksen-tiedot [db user {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (apurit/koosta-tarjouksen-tiedot db urakka-id))

(defn hae-tyhjat-tarjouksen-tiedot
  "Käyttöliittymässä voidaan tyhjätä tarjouslomake, jolloin halutaan
  palauttaa tyhjät tiedot, jotka voidaan täyttää uudelleen."
  [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user (:urakka-id tiedot))
  (let [tarjous {:urakka-id (:urakka-id tiedot)
                 :kaikki-toimenkuvat (map #(assoc %
                                             :toimenkuva (:nimi %)
                                             :nimi (str/capitalize (:nimi %))) (toimenkuva-kyselyt/hae-toimenkuvat db))
                 :tarjous (tarjous-kyselyt/luo-default-tarjous db (:urakka-id tiedot))}]
    tarjous))

(defn tallenna-tarjous [db kayttaja {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [urakan-tiedot (first (urakat-kyselyt/hae-urakan-tiedot db urakka-id))
          urakan-vuodet (map (fn [vuosi]
                               {:vuosi vuosi})
                          (range (pvm/vuosi (:alkupvm urakan-tiedot)) (pvm/vuosi (:loppupvm urakan-tiedot)))) ;; Haetaan kaikki urakan vuodet

          ;; Varmistetaan, että ei yritetä tallentaa sellaisen vuoden tietoja, jotka on jo vahvistettu
          vahvistukset (apurit/kustannussuunnitelman-vahvistukset db urakka-id)
          vahvistetut-vuodet (set (map :vuosi (filter :vahvistettu? vahvistukset)))
          kaikki-vahvistettu? (= (count vahvistetut-vuodet) (count urakan-vuodet))

          ;; Poistetaan sellaiset hoitovuosittaiset arvot, jotka on jo vahvistettu
          tarjous-rivit (reduce (fn [uudet-rivit rivi]
                                  (let [sallitut-hoitovuosittaiset-arvot (filter
                                                                           #(not (contains? vahvistetut-vuodet (:vuosi %)))
                                                                           (:hoitovuosittaiset-arvot rivi))]
                                    (conj uudet-rivit (assoc rivi :hoitovuosittaiset-arvot sallitut-hoitovuosittaiset-arvot))))
                          [] ;; Annetaan tyhjä lista, joka koostetaan sallituista tiedoista
                          (:tarjous tiedot) ;; Otetaan kaikki tarjouksen rivit tarkastettavaksi
                          )
          tiedot (assoc tiedot :tarjous tarjous-rivit) ;; Korvataan tarjouksen rivit sallituilla riveillä
          _ (when kaikki-vahvistettu?
              (throw (IllegalArgumentException. (str "Tarjousta ei voi enää muokata, koska kustannussuunitelmat on jo vahvistettu."))))
          urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit db {:urakkaid urakka-id}))
          _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id (:id kayttaja) tiedot vahvistetut-vuodet)]
      (apurit/koosta-tarjouksen-tiedot db urakka-id))))

(defrecord Tarjous []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
      :hae-tarjouksen-tiedot
      (fn [user tiedot]
        (hae-tarjouksen-tiedot (:db this) user tiedot)))
    (julkaise-palvelu (:http-palvelin this)
      :hae-tyhjat-tarjouksen-tiedot
      (fn [user tiedot]
        (hae-tyhjat-tarjouksen-tiedot (:db this) user tiedot)))

    (julkaise-palvelu (:http-palvelin this)
      :tallenna-tarjouksen-tiedot
      (fn [user tiedot]
        (tallenna-tarjous (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
      :hae-tarjouksen-tiedot
      :hae-tyhjat-tarjouksen-tiedot
      :tallenna-tarjouksen-tiedot)
    this))
