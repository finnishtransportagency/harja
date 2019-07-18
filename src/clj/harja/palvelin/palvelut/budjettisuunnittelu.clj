(ns harja.palvelin.palvelut.budjettisuunnittelu
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.palvelin.palvelut.kiinteahintaiset-tyot :as kiinthint-tyot]
            [harja.palvelin.palvelut.kustannusarvioidut-tyot :as kustarv-tyot]
            [harja.palvelin.palvelut.yksikkohintaiset-tyot :as ykshint-tyot]
            [harja.palvelin.palvelut.kokonaishintaiset-tyot :as kokhint]
            [harja.kyselyt.budjettisuunnittelu :as q]
            [harja.kyselyt.kokonaishintaiset-tyot :as kok-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.big :as big]
            [clojure.set :as set]
            [harja.domain.roolit :as roolit]))

(declare hae-urakan-budjetoidut-tyot tallenna-budjetoidut-tyot hae-urakan-tavoite tallenna-urakan-tavoite)

(defrecord Budjettisuunnittelu []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :budjetoidut-tyot (fn [user urakka-id]
                            (hae-urakan-budjetoidut-tyot (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-budjetoidut-tyot (fn [user tiedot]
                                     (tallenna-budjetoidut-tyot (:db this) user tiedot)))
      (julkaise-palvelu
        :budjettitavoite (fn [user urakka-id]
                           (hae-urakan-tavoite (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-budjettitavoite (fn [user urakka-id tavoitteet]
                                    (tallenna-urakan-tavoite (:db this) user urakka-id tavoitteet))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :budjettitavoite)
    (poista-palvelu (:http-palvelin this) :tallenna-budjettitavoite)
    this))


(defn- nil-to-zero [v]
  (if (nil? v) 0 v))

(defn hae-urakan-tavoite
  [db user urakka-id ]
  ;  (oikeudet/vaadi-lukuoikeus
  ;    (oikeudet/tarkistettava-oikeus-kok-hint-tyot urakkatyyppi-kannassa) user urakka-id))
  (q/hae-budjettitavoite db {:urakka urakka-id}))

(defn tallenna-urakan-tavoite
  "Palvelu joka tallentaa urakan budjettiin liittyvät tavoitteet: tavoitehinta, kattohinta ja edelliseltä hoitovuodelta siirretty tavoitehinnan lisä/vähennys.
  Budjettitiedoissa: hoitokausi, tavoitehinta, tavoitehinta_siirretty, kattohinta.
  Budjettitavoitteet-vektorissa voi lähettää yhden tai useamman mäpin, jossa kussakin urakan yhden hoitokauden tiedot."
  [db user urakka-id tavoitteet]
  ;(let [urakkatyyppi-kannassa (keyword (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
  ;  (oikeudet/vaadi-kirjoitusoikeus
  ;    (oikeudet/tarkistettava-oikeus-kok-hint-tyot urakkatyyppi-kannassa) user urakka-id))
  (assert (vector? tavoitteet) "tavoitteet tulee olla vektori")
  (jdbc/with-db-transaction [c db]
                            (let [nykyiset-arvot (q/hae-budjettitavoite c {:urakka urakka-id})
                                  valitut-hoitokaudet (into #{} (map :hoitokausi tavoitteet))
                                  tavoitteet-kannassa (into #{} (map :hoitokausi
                                                               (filter #(valitut-hoitokaudet (:hoitokausi %)
                                                                       nykyiset-arvot))))]
                            (doseq [hoitokausitavoite tavoitteet]
                              (as-> hoitokausitavoite hkt
                                    (assoc hkt :urakka urakka-id)
                                    (assoc hkt :kayttaja (:id user))
                                    (if (not (tavoitteet-kannassa (:hoitokausi hkt)))
                                      (q/tallenna-budjettitavoite<! c (conj hkt {:urakka   urakka-id
                                                                                      :kayttaja (:id user)})))
                                    (q/paivita-budjettitavoite! c (conj hkt {:urakka   urakka-id
                                                                                  :kayttaja (:id user)})))))))

  (defn hae-urakan-budjetoidut-tyot
    "Palvelu, joka palauttaa urakan budjetoidut työt. Palvelu palauttaa kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset työt mapissa jäsenneltynä."
    [db user urakka-id]

    ;; Kaikkien budjetoitujen töiden käyttäjäoikeudet ovat samat kuin kokonaishintaisten töiden käsittelyllä
    ;(oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kokonaishintaisettyot user urakka-id)

    {:kiinteahintaiset-tyot   (kiinthint-tyot/hae-urakan-kiinteahintaiset-tyot db user urakka-id)
     :kustannusarvioidut-tyot (kustarv-tyot/hae-urakan-kustannusarvioidut-tyot db user urakka-id)
     :yksikkohintaiset-tyot   (ykshint-tyot/hae-urakan-yksikkohintaiset-tyot db user urakka-id)})


  (defn tallenna-budjetoidut-tyot
    "Palvelu joka tallentaa urakan kustannusarvioidut tyot."
    [db user {:keys [urakka-id sopimusnumero tyot]}]

    ;; TODO Palauta oikeustarkastukset tässä ja muissa töiden tallennuksissa ja hauissa
    ;;; Onko toiminto sallittu?
    ;(let [urakkatyyppi (keyword (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    ;  (oikeudet/vaadi-kirjoitusoikeus
    ;    (oikeudet/tarkistettava-oikeus-kok-hint-tyot urakkatyyppi) user urakka-id)
    ;  (if-not (= urakkatyyppi :teiden-hoito)
    ;    (throw (IllegalArgumentException (str "Budjetoituja töitä ei voi tallentaa urakkatyypillä " urakkatyyppi ".")))))
    ;(assert (vector? tyot) "Parametrin työt (tallenna-budjetoidut-tyot) tulee olla vektori.")
    ;
    ;TODO: tarkista täällä että päivitetään urakan toimenpideinstansseja vrt. kokonaishintaisten päivitys

    (jdbc/with-db-transaction [c db]
                              (log/debug (:kustannusarvioidut-tyot tyot))
                              (let [tallennetut-tyot (group-by :toimenpideinstanssi (flatten (merge (kiinthint-tyot/tallenna-kiinteahintaiset-tyot c user {:urakka-id urakka-id :sopimusnumero sopimusnumero :tyot (:kiinteahintaiset-tyot tyot)})
                                                                                                    (kustarv-tyot/tallenna-kustannusarvioidut-tyot c user {:urakka-id urakka-id :sopimusnumero sopimusnumero :tyot (:kustannusarvioidut-tyot tyot)})
                                                                                                    ;(ykshint-tyot/tallenna-urakan-yksikkohintaiset-tyot c user {:urakka-id urakka-id :sopimusnumero sopimusnumero :tyot (:yksikkohintaiset-tyot tyot)})

                                                                                                    )))
                                    toimenpideinstanssit (into #{} (filter identity (map #(first %) tallennetut-tyot)))]

                                ;; Merkitään likaiseksi kaikki tallennettujen toimenpideinstanssien kustannussuunnitelmat.
                                ;; Periaatteessa tässä voisi myös selvittää ovatko kaikki tiedot päivittyneet ja jättää tarvittaessa osa kustannussuunnitelmista päivittämättä.

                                (when not-empty toimenpideinstanssit
                                                (kok-q/merkitse-kustannussuunnitelmat-likaisiksi! c toimenpideinstanssit))

                                )))
