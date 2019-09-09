(ns harja.palvelin.palvelut.kokonaishintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.kokonaishintaiset-tyot :as q]
            [harja.kyselyt.toimenpideinstanssit :as tpi-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tyokalut.big :as big]
            [clojure.set :as set]
            [harja.domain.roolit :as roolit]))

(declare hae-urakan-kokonaishintaiset-tyot tallenna-kokonaishintaiset-tyot)

(defrecord Kokonaishintaiset-tyot []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :kokonaishintaiset-tyot (fn [user urakka-id]
                                  (hae-urakan-kokonaishintaiset-tyot (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-kokonaishintaiset-tyot (fn [user tiedot]
                                           (tallenna-kokonaishintaiset-tyot (:db this) user tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :kokonaishintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-kokonaishintaiset-tyot)
    this))


(defn hae-urakan-kokonaishintaiset-tyot
  "Palvelu, joka palauttaa urakan kokonaishintaiset työt."
  [db user urakka-id]
 ;; (oikeudet/vaadi-lukuoikeus oikeudet/urakat-suunnittelu-kokonaishintaisettyot user urakka-id)
  (into []
        (comp
         (map #(assoc %
                      :summa (if (:summa %) (double (:summa %)))))
         (map #(if (:osuus-hoitokauden-summasta %)
                 (update % :osuus-hoitokauden-summasta big/->big)
                 %)))
        (q/listaa-kokonaishintaiset-tyot db urakka-id)))

(defn tallenna-kokonaishintaiset-tyot
  "Palvelu joka tallentaa urakan kokonaishintaiset tyot."
  [db user {:keys [urakka-id sopimusnumero tyot]}]
  (println "TYOT" tyot)
  (println "sopimusnumero" sopimusnumero)
  ;(let [urakkatyyppi-kannassa (keyword (first (urakat-q/hae-urakan-tyyppi db urakka-id)))]
  ;  (oikeudet/vaadi-kirjoitusoikeus
  ;    (oikeudet/tarkistettava-oikeus-kok-hint-tyot urakkatyyppi-kannassa) user urakka-id))
  (assert (vector? tyot) "tyot tulee olla vektori")
  (jdbc/with-db-transaction [c db]
    (let [nykyiset-arvot (hae-urakan-kokonaishintaiset-tyot c user urakka-id)
          valitut-vuosi-ja-kk (into #{} (map (juxt :vuosi :kuukausi) tyot))
          tyo-avain (fn [rivi]
                      [(:toimenpideinstanssi rivi) (:vuosi rivi) (:kuukausi rivi)])
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
          (update t :maksupvm #(when % (konv/sql-date %)))
          (assoc t :sopimus sopimusnumero)
          (assoc t :osuus-hoitokauden-summasta
                 (when-let [p (:prosentti t)]
                   (big/unwrap (big/div p (big/->big 100)))))
          (assoc t :luoja (:id user))

          (if (not (tyot-kannassa (tyo-avain t)))
            (q/lisaa-kokonaishintainen-tyo<! c t)
            (q/paivita-kokonaishintainen-tyo! c t))))

      (when (not (empty? tallennettavat-toimenpideinstanssit))
        (log/info "Merkitään kustannussuunnitelmat likaiseksi toimenpideinstansseille: " tallennettavat-toimenpideinstanssit)
        (q/merkitse-kustannussuunnitelmat-likaisiksi! c tallennettavat-toimenpideinstanssit))
      (hae-urakan-kokonaishintaiset-tyot c user urakka-id))))
