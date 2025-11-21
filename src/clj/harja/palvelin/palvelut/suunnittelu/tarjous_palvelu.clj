(ns harja.palvelin.palvelut.suunnittelu.tarjous-palvelu
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.tarjous-kyselyt :as tarjous-kyselyt]
            [harja.kyselyt.uusi-kustannussuunnitelma-kyselyt :as ks-kyselyt]
            [harja.kyselyt.toimenkuvat-kyselyt :as toimenkuva-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.suunnittelu.suunnittelu-apurit :as apurit]
            [harja.pvm :as pvm]))

(defn luo-oletusrivit-puuttuviin-osioihin [tarjous]
  (let [tarjous-tiedot (:tarjous tarjous)
        nollatut-arvot (mapv (fn [osio]
                               (update osio :hoitovuosittaiset-arvot
                                 (fn [arvot]
                                   (mapv #(update % :summa (fn [a] (if (nil? a) 0.00M a))) arvot))))
                         tarjous-tiedot)]
    (assoc tarjous :tarjous nollatut-arvot)))

(defn kustannussuunnitelman-vahvistukset [db urakka-id]
  (let [urakan-tiedot (first (urakat-kyselyt/hae-urakan-tiedot db urakka-id))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakan-tiedot))
        ;; Varmista, että yhtenäkään vuonna koko urakan keston ajalta kustannussuunnitelmaa ei ole vahvistettu. Jos on, niin tarjousta ei voi enää muokata
        vuodet (apurit/jasenna-tallennettavat-vuodet db urakka-id urakan-alkuvuosi true)
        vahvistukset (reduce (fn [lista vuosi]
                               (let [vahvistettu? (ks-kyselyt/kustannussuunnitelma-vahvistettu? db urakka-id vuosi)]
                                 (conj lista {:vuosi vuosi :vahvistettu? vahvistettu?})))
                       [] vuodet)]
    vahvistukset))

(defn koosta-tarjouksen-tiedot [db urakka-id]
  (let [urakan-parametrit (first (urakat-kyselyt/hae-urakan-parametrit db {:urakkaid urakka-id}))
        tarjous (luo-oletusrivit-puuttuviin-osioihin (tarjous-kyselyt/hae-tarjous db urakka-id))
        vahvistukset (kustannussuunnitelman-vahvistukset db urakka-id)]
    (-> tarjous
      (assoc :muokkaa-kattohinta-kasin (:muokkaa_kattohinta_kasin urakan-parametrit))
      (assoc :vahvistetut-vuodet (into #{}
                                   (flatten (map (juxt :vuosi) (filter #(true? (:vahvistettu? %)) vahvistukset))))))))

(defn hae-tarjouksen-tiedot [db user {:keys [urakka-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (koosta-tarjouksen-tiedot db urakka-id))

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
          vahvistukset (kustannussuunnitelman-vahvistukset db urakka-id)
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
          kattohintakerroin (:hoitokauden_lopun_kattohinta_kerroin urakan-parametrit)
          _ (tarjous-kyselyt/tallenna-tarjous-tietokantaan db urakka-id (:id kayttaja) kattohintakerroin tiedot vahvistetut-vuodet)]
      (koosta-tarjouksen-tiedot db urakka-id))))

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
