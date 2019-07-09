(ns harja.palvelin.palvelut.kiinteahintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.kiinteahintaiset-tyot :as q]
            [harja.kyselyt.kokonaishintaiset-tyot :as kok-q]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.big :as big]
            [clojure.set :as set]
            [harja.domain.roolit :as roolit]))

(declare hae-urakan-kiinteahintaiset-tyot tallenna-kiinteahintaiset-tyot)

(defrecord Kiinteahintaiset-tyot []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :kiinteahintaiset-tyot (fn [user urakka-id]
                                  (hae-urakan-kiinteahintaiset-tyot (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-kiinteahintaiset-tyot (fn [user tiedot]
                                           (tallenna-kiinteahintaiset-tyot (:db this) user tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :kiinteahintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-kiinteahintaiset-tyot)
    this))


(defn hae-urakan-kiinteahintaiset-tyot
  "Palvelu, joka palauttaa urakan kiinteahintaiset työt."
  [db user urakka-id]
  ;; kokonaishintaisten töiden käyttäjäoikeudet ovat soveltuvat sellaisenaan myös kiinteähintaisiin töihin
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kokonaishintaisettyot user urakka-id)
  (into []
        (comp
         (map #(assoc %
                      :summa (if (:summa %) (double (:summa %)))))
         (map #(if (:osuus-hoitokauden-summasta %)
                 (update % :osuus-hoitokauden-summasta big/->big)
                 %)))
        (q/hae-kiinteahintaiset-tyot db urakka-id)))

(defn tallenna-kiinteahintaiset-tyot
  "Palvelu joka tallentaa urakan kiinteahintaiset tyot."
  [db user {:keys [urakka-id sopimusnumero tyot]}]
  (let [urakkatyyppi-kannassa (keyword (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
    (oikeudet/vaadi-kirjoitusoikeus
      (oikeudet/tarkistettava-oikeus-kok-hint-tyot urakkatyyppi-kannassa) user urakka-id))
  (assert (vector? tyot) "tyot tulee olla vektori")
  (jdbc/with-db-transaction [c db]
    (let [nykyiset-arvot (hae-urakan-kiinteahintaiset-tyot c user urakka-id)
          valitut-vuosi-ja-kk (into #{} (map (juxt :vuosi :kuukausi) tyot))
          tyo-avain (fn [rivi]
                      [(:tyyppi rivi)(:toimenpideinstanssi rivi) (:vuosi rivi) (:kuukausi rivi)])
          tyot-kannassa (into #{} (map tyo-avain
                                       (filter #(and
                                                  (= (:sopimus %) sopimusnumero)
                                                  (valitut-vuosi-ja-kk [(:vuosi %) (:kuukausi %)]))
                                               nykyiset-arvot)))
          urakan-toimenpideinstanssit (into #{}
                                            (map :id)
                                            (tpi-q/urakan-toimenpideinstanssi-idt c urakka-id))
          tallennettavat-toimenpideinstanssit (into #{} (map #(:toimenpideinstanssi %) tyot))]

      ;; Varmistetaan ettei päivitystä voi tehdä toimenpideinstanssille, joka ei kuulu
      ;; tähän urakkaan.
      (when-not (empty? (set/difference tallennettavat-toimenpideinstanssit
                                        urakan-toimenpideinstanssit))
        (throw (roolit/->EiOikeutta "virheellinen toimenpideinstanssi")))

      (doseq [tyo tyot]
        (as-> tyo t
          (update t :summa big/unwrap)
          (assoc t :sopimus sopimusnumero)
          (assoc t :kayttaja (:id user))
          (if (not (tyot-kannassa (tyo-avain t)))
            (q/lisaa-kiinteahintainen-tyo<! c t)
            (q/paivita-kiinteahintainen-tyo! c t))))

      (when (not (empty? tallennettavat-toimenpideinstanssit))
        (log/info "Merkitään kustannussuunnitelmat likaiseksi toimenpideinstansseille: " tallennettavat-toimenpideinstanssit)
        (kok-q/merkitse-kustannussuunnitelmat-likaisiksi! c tallennettavat-toimenpideinstanssit))
      (hae-urakan-kiinteahintaiset-tyot c user urakka-id))))
