(ns harja.palvelin.palvelut.muut-tyot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [taoensso.timbre :as log]
            [clojure.set :refer [intersection difference]]
            [clojure.string :as str]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :as t]
            [clj-time.coerce :as c]

            [harja.palvelin.oikeudet :as oikeudet]
            [harja.kyselyt.muutoshintaiset-tyot :as q]
            [harja.kyselyt.toteumat :as tot-q]
            [harja.kyselyt.konversio :as konv]))


(def muutoshintaiset-xf
  (comp
    (map konv/alaviiva->rakenne)
    (map #(assoc %
           :yksikkohinta (if (:yksikkohinta %) (double (:yksikkohinta %)))))))


(defn hae-urakan-muutoshintaiset-tyot
      "Palvelu, joka palauttaa urakan muutoshintaiset työt."
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus-urakkaan user urakka-id)
  (log/info "hae-urakan-muutoshintaiset-tyot" urakka-id)
  (into []
    muutoshintaiset-xf
    (q/listaa-urakan-muutoshintaiset-tyot db urakka-id)))

(defn tallenna-urakan-muutoshintaiset-tyot
      "Palvelu joka tallentaa urakan muutoshintaiset tyot."
  [db user {:keys [urakka-id sopimusnumero tyot]}]
  (oikeudet/vaadi-rooli-urakassa user oikeudet/rooli-urakanvalvoja urakka-id)
  (assert (vector? tyot) "tyot tulee olla vektori")


  ;; FIXME: ei ole vielä implementoitu

  (jdbc/with-db-transaction [c db]
    (let [nykyiset-arvot (hae-urakan-muutoshintaiset-tyot c user urakka-id)
          valitut-pvmt (into #{} (map (juxt :alkupvm :loppupvm) tyot))
          tyo-avain (fn [rivi]
                      [(:alkupvm rivi) (:loppupvm rivi) (:tehtava rivi)])
          tyot-kannassa (into #{} (map tyo-avain
                                    (filter #(and
                                              (= (:sopimus %) sopimusnumero)
                                              (valitut-pvmt [(:alkupvm %) (:loppupvm %)]))
                                      nykyiset-arvot)))
          uniikit-tehtavat (into #{} (map #(:tehtava %) tyot)) ]
      (doseq [tyo tyot]
        (log/info "TALLENNA TYÖ: " (pr-str tyo))
        (if (not (tyot-kannassa (tyo-avain tyo)))
          ;; insert
          (do
            (log/info "--> LISÄTÄÄN UUSI!")
            (q/lisaa-urakan-muutoshintainen-tyo<! c (:yksikko tyo) (:yksikkohinta tyo)
              urakka-id sopimusnumero (:tehtava tyo)
              (java.sql.Date. (.getTime (:alkupvm tyo)))
              (java.sql.Date. (.getTime (:loppupvm tyo)))))
          ;;update
          (do (log/info " --> päivitetään vanha")
              (log/info "  päivittyi: " (q/paivita-urakan-muutoshintainen-tyo! c (:yksikko tyo) (:yksikkohinta tyo)
                                          urakka-id sopimusnumero (:tehtava tyo)
                                          (java.sql.Date. (.getTime (:alkupvm tyo)))
                                          (java.sql.Date. (.getTime (:loppupvm tyo)))))))))
    (hae-urakan-muutoshintaiset-tyot c user urakka-id)))

(defrecord Muut-tyot []
  component/Lifecycle
  (start [this]
    (doto (:http-palvelin this)
      (julkaise-palvelu
        :muutoshintaiset-tyot (fn [user urakka-id]
                                 (hae-urakan-muutoshintaiset-tyot (:db this) user urakka-id)))
      (julkaise-palvelu
        :tallenna-urakan-muutoshintaiset-tyot (fn [user tiedot]
                                                 (tallenna-urakan-muutoshintaiset-tyot (:db this) user tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :muutoshintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-urakan-muutoshintaiset-tyot)
    this))
