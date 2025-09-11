(ns harja.palvelin.palvelut.suunnittelu.tarjous-palvelu
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.toimenkuvat-kyselyt :as toimenkuva-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]))

(defn hae-tarjouksen-tiedot [db user {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (tarjous-kyselyt/hae-tarjous db urakka-id))

(defn hae-tyhjat-tarjouksen-tiedot
  "Käyttöliittymässä voidaan tyhjätä tarjouslomake, jolloin halutaan
  palauttaa tyhjät tiedot, jotka voidaan täyttää uudelleen."
  [db user tiedot]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user (:urakka-id tiedot))
  (let [tarjous {:urakka-id (:urakka-id tiedot)
                 :kaikki-toimenkuvat (map #(assoc % :nimi (str/capitalize (:nimi %))) (toimenkuva-kyselyt/hae-toimenkuvat db))
                 :tarjous (tarjous-kyselyt/luo-default-tarjous db (:urakka-id tiedot))}]
    tarjous))

(defn tallenna-tarjous [db kayttaja {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu kayttaja urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit db {:urakkaid urakka-id}))
          kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
          _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id (:id kayttaja) kattohintakerroin tiedot)]
      (tarjous-kyselyt/hae-tarjous db (:urakka-id tiedot)))))


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
