(ns harja.palvelin.palvelut.yksikkohintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [clojure.set :refer [intersection difference]]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]

            [harja.kyselyt.indeksit :as indeksi-kyselyt]
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

(defn hae-urakan-indeksikertoimet
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus
    oikeudet/urakat-suunnittelu-yksikkohintaisettyot
    user
    urakka-id)
  (indeksi-kyselyt/hae-urakan-indeksikertoimet db urakka-id))

(defn tallenna-urakan-yksikkohintaiset-tyot
  "Palvelu joka tallentaa urakan yksikkohintaiset tyot."
  [db user {:keys [urakka-id sopimusnumero tyot kopioi-tuleville-vuosille?]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-yksikkohintaisettyot user urakka-id)
  (assert (vector? tyot) "tyot tulee olla vektori")
  (jdbc/with-db-transaction [c db]
                            (let [urakkatyyppi (u-q/hae-urakan-tyyppi db {:urakka urakka-id})
                                  urakka (first (u-q/hae-urakan-tiedot db urakka-id))
                                  nykyinen-vuosi (when-let [ensimmainen-tyo (first tyot)]
                                                   (if (= "teiden-hoito" urakkatyyppi)
                                                     (:vuosi ensimmainen-tyo)
                                                     (pvm/vuosi (:alkupvm ensimmainen-tyo))))
                                  nykyiset-arvot (hae-urakan-yksikkohintaiset-tyot c user urakka-id)
                                  normalisoi-pvm (fn [pvm-arvo] (when pvm-arvo
                                                                    (java.sql.Date/valueOf (pvm/sql-aika->pvm-str pvm-arvo))))
                                  tyo-avain (if (= "teiden-hoito" urakkatyyppi)
                                              (fn [rivi] [(:vuosi rivi) (:kuukausi rivi) (:tehtava rivi)])
                                              (fn [rivi] [(normalisoi-pvm (:alkupvm rivi))
                                                          (normalisoi-pvm (:loppupvm rivi))
                                                          (:tehtava rivi)]))
                                  tallennettavat-vuodet (if (and kopioi-tuleville-vuosille? nykyinen-vuosi)
                                                          (rest (range nykyinen-vuosi (pvm/vuosi (:loppupvm urakka))))
                                                          [])
                                  kopioitavat-tyot (mapcat
                                                    (fn [vuosi]
                                                      (map
                                                        (fn [tyo]
                                                          (if (= "teiden-hoito" urakkatyyppi)
                                                            (assoc tyo :vuosi vuosi)
                                                            (let [[alkupvm loppupvm] (pvm/vuodesta-hoitokausi vuosi)]
                                                              (assoc tyo
                                                                :alkupvm alkupvm
                                                                :loppupvm loppupvm))))
                                                        tyot))
                                                    tallennettavat-vuodet)
                                  tyot (into tyot kopioitavat-tyot)
                                  tyot-kannassa (into #{} (comp
                                                            (filter #(= (:sopimus %) sopimusnumero))
                                                            (map tyo-avain))
                                                  nykyiset-arvot)
                                  uniikit-tehtavat (into #{} (map #(:tehtava %) tyot))]
                              (doseq [tyo tyot]
                                (let [sql {:maara                         (:maara tyo)
                                           :yksikko                       (:yksikko tyo)
                                           :yksikkohinta                  (:yksikkohinta tyo)
                                           :indeksi_kaytossa              (:indeksi_kaytossa tyo)
                                           :indeksikorjattu_yksikkohinta  (:indeksikorjattu_yksikkohinta tyo)
                                           :arvioitu_kustannus            (:arvioitu_kustannus tyo)
                                           :kayttaja                      (:id user)
                                           :urakka                        urakka-id
                                           :sopimus                       sopimusnumero
                                           :tehtava                       (:tehtava tyo)
                                           :alkupvm                       (when-not (nil? (:alkupvm tyo)) (java.sql.Date. (.getTime (:alkupvm tyo))))
                                           :loppupvm                      (when-not (nil? (:loppupvm tyo)) (java.sql.Date. (.getTime (:loppupvm tyo))))
                                           :kuukausi                      (:kuukausi tyo)
                                           :vuosi                         (:vuosi tyo)}]
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
                                                 (tallenna-urakan-yksikkohintaiset-tyot (:db this) user tiedot)))
      (julkaise-palvelu
        :urakan-indeksikertoimet
        (fn [user urakka-id]
          (hae-urakan-indeksikertoimet (:db this) user urakka-id))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :yksikkohintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-urakan-yksikkohintaiset-tyot)
    this))
