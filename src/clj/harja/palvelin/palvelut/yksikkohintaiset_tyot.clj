(ns harja.palvelin.palvelut.yksikkohintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]

            [harja.kyselyt.yksikkohintaiset-tyot :as q]
            [harja.kyselyt.urakat :as u-q]
            [harja.domain.oikeudet :as oikeudet]))


(defn hae-urakan-yksikkohintaiset-tyot
  "Palvelu, joka palauttaa urakan yksikkohintaiset työt."
  [db user urakka-id]
  (or (oikeudet/voi-lukea? oikeudet/urakat-suunnittelu-yksikkohintaisettyot urakka-id user)
      (oikeudet/voi-lukea? oikeudet/urakat-toteumat-yksikkohintaisettyot urakka-id user))
  (oikeudet/ei-oikeustarkistusta!)
  (into []
        (map #(assoc %
                :maara (if (:maara %) (double (:maara %)))
                :yksikkohinta (if (:yksikkohinta %) (double (:yksikkohinta %)))))
        (q/listaa-urakan-yksikkohintaiset-tyot db {:urakka urakka-id})))

(defn tallenna-urakan-yksikkohintaiset-tyot
  "Palvelu joka tallentaa urakan yksikkohintaiset tyot."
  [db user {:keys [urakka-id sopimusnumero tyot]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-yksikkohintaisettyot user urakka-id)
  (assert (vector? tyot) "tyot tulee olla vektori")
  (jdbc/with-db-transaction [c db]
                            (let [urakkatyyppi (u-q/hae-urakan-tyyppi db {:urakka urakka-id})
                                  nykyiset-arvot (hae-urakan-yksikkohintaiset-tyot c user urakka-id)
                                  valitut-pvmt (into #{} (map (juxt :alkupvm :loppupvm) tyot))
                                  tyo-avain (if (= "teiden-hoito" urakkatyyppi)
                                              (fn [rivi] [(:vuosi rivi) (:kuukausi rivi) (:tehtava rivi)])
                                              (fn [rivi] [(:alkupvm rivi) (:loppupvm rivi) (:tehtava rivi)]))
                                  tyot-kannassa (into #{} (map tyo-avain
                                                               (filter #(and
                                                                          (= (:sopimus %) sopimusnumero)
                                                                          (valitut-pvmt [(:alkupvm %) (:loppupvm %)]))
                                                                       nykyiset-arvot)))
                                  uniikit-tehtavat (into #{} (map #(:tehtava %) tyot))]
                              (doseq [tyo tyot]
                                (let [sql {:maara              (:maara tyo)
                                           :yksikko            (:yksikko tyo)
                                           :yksikkohinta       (:yksikkohinta tyo)
                                           :arvioitu_kustannus (:arvioitu_kustannus tyo)
                                           :kayttaja           (:id user)
                                           :urakka             urakka-id
                                           :sopimus            sopimusnumero
                                           :tehtava            (:tehtava tyo)
                                           :alkupvm            (when-not (nil? (:alkupvm tyo)) (java.sql.Date. (.getTime (:alkupvm tyo))))
                                           :loppupvm           (when-not (nil? (:loppupvm tyo)) (java.sql.Date. (.getTime (:loppupvm tyo))))
                                           :kuukausi           (:kuukausi tyo)
                                           :vuosi              (:vuosi tyo)}]
                                  (if (not (tyot-kannassa (tyo-avain tyo)))
                                    ;; insert
                                    (q/lisaa-urakan-yksikkohintainen-tyo<! c sql)
                                    ;;update
                                    (q/paivita-urakan-yksikkohintainen-tyo! c sql))))
                              (when-not (empty? uniikit-tehtavat)
                                (log/debug "Merkitään kustannussuunnitelmat likaiseksi tehtäville: " uniikit-tehtavat)
                                (q/merkitse-kustannussuunnitelmat-likaisiksi! c {:urakka   urakka-id
                                                                                 :tehtavat uniikit-tehtavat})))
                            (hae-urakan-yksikkohintaiset-tyot c user urakka-id)))

(defrecord Yksikkohintaiset-tyot []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :yksikkohintaiset-tyot (fn [user urakka-id]
                                 (hae-urakan-yksikkohintaiset-tyot (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-urakan-yksikkohintaiset-tyot (fn [user tiedot]
                                                 (tallenna-urakan-yksikkohintaiset-tyot (:db this) user tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :yksikkohintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-urakan-yksikkohintaiset-tyot)
    this))
