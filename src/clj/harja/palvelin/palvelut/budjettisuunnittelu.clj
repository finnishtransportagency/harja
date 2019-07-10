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
            [harja.palvelin.palvelut.kokonaishintaiset-tyot :as kokhint-tyot]
            [harja.kyselyt.kokonaishintaiset-tyot :as kok-q]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.big :as big]
            [clojure.set :as set]
            [harja.domain.roolit :as roolit]))

(declare hae-urakan-budjetoidut-tyot tallenna-budjetoidut-tyot hae-urakan-budjettiviitekehys tallenna-urakan-bujettiviitekehys)

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
        :budjettiviitekehys (fn [user urakka-id]
                              (hae-urakan-budjettiviitekehys (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-budjettiviitekehys (fn [user urakka-id tavoitebudjetit kattohinnat]
                                       (tallenna-urakan-bujettiviitekehys (:db this) user urakka-id tavoitebudjetit kattohinnat))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-budjetoidut-tyot)
    (poista-palvelu (:http-palvelin this) :budjettiviitekehys)
    (poista-palvelu (:http-palvelin this) :tallenna-budjettiviitekehys)
    this))


(defn hae-urakan-budjettiviitekehys
  "Palvelu joka hakee urakan budjetin viitekehyksen: hoitokausikohtaiset tavoite- ja kattohinnat."
  [db user urakka-id]
  ;; TODO: suunnittele tietokantataulu ja toteuta haku
  )

(defn tallenna-budjettiviitekehys
  "Palvelu joka tallentaa urakan budjetin viitekehyksen: hoitokausikohtaiset tavoite- ja kattohinnat."
  [db user urakka-id tavoitebudjetit kattohinnat]
  ;; TODO: suunnittele tietokantataulu ja toteuta haku
  )

(defn hae-urakan-budjetoidut-tyot
  "Palvelu, joka palauttaa urakan budjetoidut työt. Palvelu palauttaa kiinteähintaiset, kustannusarvioidut ja yksikköhintaiset työt mapissa jäsenneltynä."
  [db user urakka-id]

  ;; Kaikkien budjetoitujen töiden käyttäjäoikeudet ovat samat kuin kokonaishintaisten töiden käsittelyllä
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kokonaishintaisettyot user urakka-id)

  {:kiinteahintaiset-tyot (kiinthint-tyot/hae-urakan-kiinteahintaiset-tyot db user urakka-id)
   :kustannusarvioidut-tyot (kustarv-tyot/hae-urakan-kustannusarvioidut-tyot db user urakka-id)
   :yksikkohintaiset-tyot (ykshint-tyot/hae-urakan-yksikkohintaiset-tyot db user urakka-id)})

(defn tallenna-budjetoidut-tyot
  "Palvelu joka tallentaa urakan kustannusarvioidut tyot."
  [db user {:keys [urakka-id sopimusnumero tyot]}]

  ;;; Onko toiminto sallittu?
  ;(let [urakkatyyppi (keyword (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
  ;  (oikeudet/vaadi-kirjoitusoikeus
  ;    (oikeudet/tarkistettava-oikeus-kok-hint-tyot urakkatyyppi) user urakka-id)
  ;  (if-not (= urakkatyyppi :teiden-hoito)
  ;    (throw (IllegalArgumentException (str "Budjetoituja töitä ei voi tallentaa urakkatyypillä " urakkatyyppi ".")))))
  ;(assert (vector? tyot) "Parametrin työt (tallenna-budjetoidut-tyot) tulee olla vektori.")
  ;
  ;
  ;
  ;(let [kiinteahintaiset 2]
  ;;; TODO: filteröi kiinteähintaiset työt
  ;
  ;
  ;
  ;;; TODO: filteröi kustannusarvioidut työt
  ;
  ;
  ;
  ;;; TODO: filteröi yksikköhintaiset työt

  ;(jdbc/with-db-transaction [c db]
  ;
  ;                          (let [nykyiset-arvot (hae-urakan-kustannusarvioidut-tyot c user urakka-id)
  ;                                valitut-vuosi-ja-kk (into #{} (map (juxt :vuosi :kuukausi) tyot))
  ;                                tyo-avain (fn [rivi]
  ;                                            [(:tyyppi rivi) (:toimenpideinstanssi rivi) (:vuosi rivi) (:kuukausi rivi)])
  ;                                tyot-kannassa (into #{} (map tyo-avain
  ;                                                             (filter #(and
  ;                                                                        (= (:sopimus %) sopimusnumero)
  ;                                                                        (valitut-vuosi-ja-kk [(:vuosi %) (:kuukausi %)]))
  ;                                                                     nykyiset-arvot)))
  ;                                urakan-toimenpideinstanssit (into #{}
  ;                                                                  (map :id)
  ;                                                                  (tpi-q/urakan-toimenpideinstanssi-idt c urakka-id))
  ;                                tallennettavat-toimenpideinstanssit (into #{} (map #(:toimenpideinstanssi %) tyot))]
  ;
  ;                            ;; Varmistetaan ettei päivitystä voi tehdä toimenpideinstanssille, joka ei kuulu
  ;                            ;; tähän urakkaan.
  ;                            (when-not (empty? (set/difference tallennettavat-toimenpideinstanssit
  ;                                                              urakan-toimenpideinstanssit))
  ;                              (throw (roolit/->EiOikeutta "virheellinen toimenpideinstanssi")))
  ;
  ;                            (doseq [tyo tyot]
  ;                              (as-> tyo t
  ;                                    (update t :summa big/unwrap)
  ;                                    (assoc t :sopimus sopimusnumero)
  ;                                    (assoc t :kayttaja (:id user))
  ;                                    (if (not (tyot-kannassa (tyo-avain t)))
  ;                                      (q/lisaa-kustannusarvioitu-tyo<! c t)
  ;                                      (q/paivita-kustannusarvioitu-tyo! c t))))
  ;
  ;                            (when (not (empty? tallennettavat-toimenpideinstanssit))
  ;                              (log/info "Merkitään kustannussuunnitelmat likaiseksi toimenpideinstansseille: " tallennettavat-toimenpideinstanssit)
  ;                              (kok-q/merkitse-kustannussuunnitelmat-likaisiksi! c tallennettavat-toimenpideinstanssit))
  ;                            (hae-urakan-kustannusarvioidut-tyot c user urakka-id)))

  ;)
  )
