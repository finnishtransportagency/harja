(ns harja.palvelin.palvelut.budjettisuunnittelu
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.set :as set]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]

            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt
             [budjettisuunnittelu :as q]
             [urakat :as urakat-q]
             [sopimukset :as sopimus-q]
             [kokonaishintaiset-tyot :as kok-q]
             [toimenpideinstanssit :as tpi-q]]
            [harja.palvelin.palvelut
             [kiinteahintaiset-tyot :as kiinthint-tyot]
             [kustannusarvioidut-tyot :as kustarv-tyot]
             [yksikkohintaiset-tyot :as ykshint-tyot]]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-urakan-tavoite
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  (q/hae-budjettitavoite db {:urakka urakka-id}))

(defn tallenna-urakan-tavoite
  "Palvelu joka tallentaa urakan budjettiin liittyvät tavoitteet: tavoitehinta, kattohinta ja edelliseltä hoitovuodelta siirretty tavoitehinnan lisä/vähennys.
  Budjettitiedoissa: hoitokausi, tavoitehinta, tavoitehinta_siirretty, kattohinta.
  Budjettitavoitteet-vektorissa voi lähettää yhden tai useamman mäpin, jossa kussakin urakan yhden hoitokauden tiedot."
  [db user {:keys [urakka-id tavoitteet]}]

  (let [urakkatyyppi (keyword (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakka " urakka-id " on tyyppiä " urakkatyyppi ". Tavoite kirjataan vain teiden hoidon urakoille."))))
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id))

  (assert (vector? tavoitteet) "tavoitteet tulee olla vektori")

  (jdbc/with-db-transaction [c db]
                            (let [tavoitteet-kannassa (q/hae-budjettitavoite c {:urakka urakka-id})
                                  tallennettavat-hoitokaudet (into #{} (map :hoitokausi tavoitteet))
                                  paivitettavat-tavoitteet (into #{}
                                                                 (map :hoitokausi)
                                                                 (filter #(tallennettavat-hoitokaudet (:hoitokausi %)) tavoitteet-kannassa))]
                              (doseq [hoitokausitavoite tavoitteet]
                                (as-> hoitokausitavoite hkt
                                      (assoc hkt :urakka urakka-id)
                                      (assoc hkt :kayttaja (:id user))
                                      (if (not (paivitettavat-tavoitteet (:hoitokausi hkt)))
                                        (q/tallenna-budjettitavoite<! c hkt)
                                        (q/paivita-budjettitavoite<! c hkt)))))))

(defn hae-urakan-budjetoidut-tyot
  "Palvelu, joka palauttaa urakan budjetoidut työt. Palvelu palauttaa kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset työt mapissa jäsenneltynä."
  [db user {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
  {:kiinteahintaiset-tyot   (kiinthint-tyot/hae-urakan-kiinteahintaiset-tyot db user urakka-id)
   :kustannusarvioidut-tyot (kustarv-tyot/hae-urakan-kustannusarvioidut-tyot db user urakka-id)
   :yksikkohintaiset-tyot   (ykshint-tyot/hae-urakan-yksikkohintaiset-tyot db user urakka-id)})


(defn tallenna-budjetoidut-tyot
  "Palvelu joka tallentaa urakan kustannusarvioidut tyot."
  [db user {:keys [urakka-id tyot]}]

  (let [urakkatyyppi (keyword (:tyyppi
                                (first (urakat-q/hae-urakan-tyyppi db urakka-id))))
        sopimusnumero (:id
                        (first (sopimus-q/hae-urakan-paasopimus db urakka-id))) ;; teiden hoidon urakoissa (MHU) on vain yksi sopimus
        tallennettavat-toimenpideinstanssit (into #{}
                                                  (keep :toimenpideinstanssi (concat (:kiinteahintaiset-tyot tyot)
                                                                                     (:kustannusarvioidut-tyot tyot)
                                                                                     (:yksikkohintaiset-tyot tyot))))
        tallennettavat-toimenpideinstanssit-urakassa (into #{}
                                                           (map :id)
                                                           (tpi-q/urakan-toimenpideinstanssit-idlla db urakka-id tallennettavat-toimenpideinstanssit))]

    ;; Tarkistetaan oikeudet ja että kyseessä on maanteiden hoidon urakka (MHU) ja että käsitellyt toimenpideinstanssit kuuluvat urakkaan.
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-kustannussuunnittelu user urakka-id)
    (if-not (= urakkatyyppi :teiden-hoito)
      (throw (IllegalArgumentException. (str "Urakan " urakka-id " budjetoituja töitä ei voi tallentaa urakkatyypillä " urakkatyyppi "."))))
    (when-not (empty?
                (set/difference tallennettavat-toimenpideinstanssit tallennettavat-toimenpideinstanssit-urakassa))
      (throw (IllegalArgumentException. "Väärän urakan toimenpideinstanssi")))

    (jdbc/with-db-transaction [c db]
                              (kiinthint-tyot/tallenna-kiinteahintaiset-tyot c user {:urakka-id urakka-id :sopimusnumero sopimusnumero :tyot (:kiinteahintaiset-tyot tyot)})
                              (kustarv-tyot/tallenna-kustannusarvioidut-tyot c user {:urakka-id urakka-id :sopimusnumero sopimusnumero :tyot (:kustannusarvioidut-tyot tyot)})
                              (ykshint-tyot/tallenna-urakan-yksikkohintaiset-tyot c user {:urakka-id urakka-id :sopimusnumero sopimusnumero :tyot (:yksikkohintaiset-tyot tyot)})

                              ;; Merkitään likaiseksi tallennettujen toimenpideinstanssien kustannussuunnitelmat.
                              ;; Periaatteessa tässä voisi myös selvittää ovatko kaikki tiedot päivittyneet ja jättää tarvittaessa osa kustannussuunnitelmista päivittämättä.
                              (when not-empty tallennettavat-toimenpideinstanssit-urakassa
                                              (kok-q/merkitse-kustannussuunnitelmat-likaisiksi! c tallennettavat-toimenpideinstanssit-urakassa))

                              ;; Palautetaan päivitetty tilanne
                              (hae-urakan-budjetoidut-tyot c user urakka-id))))

(defrecord Budjettisuunnittelu []
  component/Lifecycle
  (start [this]
    (when (ominaisuus-kaytossa? :mhu-urakka)
      (doto (:http-palvelin this)
        (julkaise-palvelu
          :budjetoidut-tyot (fn [user tiedot]
                              (hae-urakan-budjetoidut-tyot (:db this) user tiedot)))
        (julkaise-palvelu
          :tallenna-budjetoidut-tyot (fn [user tiedot]
                                       (tallenna-budjetoidut-tyot (:db this) user tiedot)))
        (julkaise-palvelu
          :budjettitavoite (fn [user tiedot]
                             (hae-urakan-tavoite (:db this) user tiedot)))
        (julkaise-palvelu
          :tallenna-budjettitavoite (fn [user tiedot]
                                      (tallenna-urakan-tavoite (:db this) user tiedot)))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :budjettitavoite)
    (poista-palvelu (:http-palvelin this) :tallenna-budjettitavoite)
    this))
