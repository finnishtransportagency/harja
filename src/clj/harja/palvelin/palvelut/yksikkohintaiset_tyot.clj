(ns harja.palvelin.palvelut.yksikkohintaiset-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.sopimukset :as sopimukset-q]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]

            [harja.kyselyt.yksikkohintaiset-tyot :as q]
            [harja.kyselyt.urakat :as u-q]
            [harja.domain.oikeudet :as oikeudet]))

(def ^:private yksikkohinnan-haarukat-testidataksi
  {"h" [30 150]
   "tunti" [30 150]
   "km" [100 2000]
   "m" [1 200]
   "m2" [1 100]
   "m²" [1 100]
   "m3" [10 500]
   "m³" [10 500]
   "kpl" [10 1000]
   "kg" [1 50]
   "t" [25 500]
   "tn" [25 500]
   "ha" [50 1500]
   "vrk" [100 2000]
   "päivä" [100 2000]})

(defn generoi-vhu-yksikkohinta
  "Arpoo kehityskäyttöön yksikkökohtaisen yksikköhinnan.
  Käytetään kululomakkeella, jos testauksessa huomataan, että joltakin vhu-urakan tehtävältä puuttuu yksikköhinta."
  [yksikko]
  (let [[alaraja ylaraja] (get yksikkohinnan-haarukat-testidataksi
                          (some-> yksikko str/lower-case str/trim)
                          [10 500])]
    (+ alaraja (rand-int (inc (- ylaraja alaraja))))))

(declare tallenna-urakan-yksikkohintaiset-tyot)

(defn generoi-vhu-yksikkohinnat
  "Generoi yksikköhinnat VHU-urakan hinnoittelemattomille tehtäville kehitysympäristössä."
  [db user kehitysmoodi? {:keys [urakka-id valittu-hoitokausi]}]
  #_ (when-not kehitysmoodi?
    (throw (ex-info "VHU-yksikköhintojen generointi on käytössä vain kehitysympäristössä." {})))
  (when-not (and (number? urakka-id)
              (vector? valittu-hoitokausi)
              (= 2 (count valittu-hoitokausi))
              (every? some? valittu-hoitokausi))
    (throw (ex-info "Urakka ja hoitokausi ovat pakollisia." {})))
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-suunnittelu-yksikkohintaisettyot user urakka-id)
  (when-not (= "vesivayla-hoito" (:tyyppi (first (u-q/hae-urakan-tyyppi db {:urakka urakka-id}))))
    (throw (ex-info "Yksikköhintoja voi generoida vain VHU-urakalle." {})))
  (let [[alkupvm loppupvm] valittu-hoitokausi
        alkupvm-sql (konversio/sql-date alkupvm)
        loppupvm-sql (konversio/sql-date loppupvm)
        sopimusnumero (:id (first (sopimukset-q/hae-urakan-paasopimus db urakka-id)))]
    (let [tehtavat (q/hae-vhu-urakan-hinnoittelemattomat-tehtavat db {:urakkaid urakka-id
                                                                       :sopimusid sopimusnumero
                                                                       :alkupvm alkupvm-sql
                                                                       :loppupvm loppupvm-sql})
          generoitu (mapv (fn [{:keys [tehtava tehtavan-yksikko
                                       yksikkohintaisen-tyon-yksikko
                                       maara arvioitu_kustannus]}]
                            (let [yksikko (or yksikkohintaisen-tyon-yksikko tehtavan-yksikko)]
                              {:tehtava tehtava
                               :yksikko yksikko
                               :maara maara
                               :arvioitu_kustannus arvioitu_kustannus
                               :yksikkohinta (generoi-vhu-yksikkohinta yksikko)
                               :alkupvm alkupvm-sql
                               :loppupvm loppupvm-sql}))
                      tehtavat)]
      (when (seq generoitu)
        (tallenna-urakan-yksikkohintaiset-tyot
          db user {:urakka-id urakka-id
                   :sopimusnumero sopimusnumero
                   :tyot generoitu}))
      {:tehtavat (mapv #(select-keys % [:tehtava :yksikkohinta]) generoitu)})))


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

(defrecord Yksikkohintaiset-tyot [kehitysmoodi?]
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :yksikkohintaiset-tyot (fn [user urakka-id]
                                 (hae-urakan-yksikkohintaiset-tyot (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-urakan-yksikkohintaiset-tyot (fn [user tiedot]
                                                 (tallenna-urakan-yksikkohintaiset-tyot (:db this) user tiedot))))
    (when kehitysmoodi?
      (julkaise-palvelu
        (:http-palvelin this)
        :generoi-vhu-yksikkohinnat
        (fn [user tiedot]
          (generoi-vhu-yksikkohinnat (:db this) user kehitysmoodi? tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :yksikkohintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-urakan-yksikkohintaiset-tyot)
    (when kehitysmoodi?
      (poista-palvelu (:http-palvelin this) :generoi-vhu-yksikkohinnat))
    this))
