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

(defn tallenna-muutoshintaiset-tyot
      "Palvelu joka tallentaa muutoshintaiset tyot."
  [db user {:keys [urakka-id tyot]}]
  (log/info "tallenna-muutoshintaiset-tyot" urakka-id " työt " tyot)
  (oikeudet/vaadi-rooli-urakassa user oikeudet/rooli-urakanvalvoja urakka-id)
  (assert (vector? tyot) "tyot tulee olla vektori")

  (jdbc/with-db-transaction [c db]
    (let [nykyiset-arvot (hae-urakan-muutoshintaiset-tyot c user urakka-id)
          sopimusnumero (:sopimus (first tyot))
          valitut-pvmt (into #{} (map (juxt :alkupvm :loppupvm) tyot))
          tyo-avain (fn [rivi]
                      [(:alkupvm rivi) (:loppupvm rivi) (:tehtava rivi)])
          tyot-kannassa (into #{} (map tyo-avain
                                    (filter #(and
                                              (= (:sopimus %) sopimusnumero)
                                              (valitut-pvmt [(:alkupvm %) (:loppupvm %)]))
                                      nykyiset-arvot)))]
      (doseq [tyo tyot]
        (log/info "TALLENNA TYÖ: " (pr-str tyo))
        (if (neg? (:id tyo))
          ;; insert
          (do
            (log/info "--> LISÄTÄÄN UUSI!")
            (q/lisaa-muutoshintainen-tyo<! c (:yksikko tyo) (:yksikkohinta tyo)
              urakka-id (:sopimus tyo) (:tehtava tyo)
              (java.sql.Date. (.getTime (:alkupvm tyo)))
              (java.sql.Date. (.getTime (:loppupvm tyo)))))
          ;;update
          (do (log/info " --> päivitetään vanha")
              (log/info "  päivittyi: " (q/paivita-muutoshintainen-tyo! c (:yksikko tyo) (:yksikkohinta tyo)
                                          urakka-id (:sopimus tyo) (:tehtava tyo)
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
        :tallenna-muutoshintaiset-tyot (fn [user tiedot]
                                                 (tallenna-muutoshintaiset-tyot (:db this) user tiedot))))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :muutoshintaiset-tyot)
    (poista-palvelu (:http-palvelin this) :tallenna-muutoshintaiset-tyot)
    this))
