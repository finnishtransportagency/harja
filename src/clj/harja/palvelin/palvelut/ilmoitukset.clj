(ns harja.palvelin.palvelut.ilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [clj-time.core :as t]
            [clj-time.coerce :refer [from-sql-time]]

            [harja.kyselyt.ilmoitukset :as q]))

(defn annettu? [p]
  (if (nil? p)
    false
    (do
      (if (string? p)
        (not (empty? p))

        (if (vector? p)
          (do
            (if (empty? p)
              false
              (some true? (map annettu? p))))

          (if (map? p)
            (do
              (if (empty? p)
                false
                (some true? (map #(annettu? (val %)) p))))

            true))))))

(defn hae-ilmoitukset
  [db user hallintayksikko urakka tilat tyypit aikavali hakuehto]
  (let [aikavali-alku (when (first aikavali)
                        (konv/sql-timestamp (first aikavali)))
        aikavali-loppu (when (second aikavali)
                         (konv/sql-timestamp (second aikavali)))
        tyypit (mapv name tyypit)
        _ (log/debug "HY ja urakka annettu? " (annettu? hallintayksikko) (annettu? urakka))
        _ (log/debug "HY ja urakka: " hallintayksikko urakka)
        _ (log/debug "Aikavälit annettu? " (annettu? aikavali-alku) (annettu? aikavali-loppu))
        _ (log/debug "Aikavälit: " aikavali-alku aikavali-loppu)
        _ (log/debug "Tyypit annettu ja tyypit: " (annettu? tyypit) tyypit)
        _ (log/debug "Hakuehto annettu ja hakuehto:" (annettu? hakuehto) (str "%" hakuehto "%"))
        _ (log/debug "Suljetut ja/tai avoimet?" (if (:suljetut tilat) true false) (if (:avoimet tilat) true false))
        mankeloitava (into []
                           (comp
                             (harja.geo/muunna-pg-tulokset :sijainti)
                             (map konv/alaviiva->rakenne)
                             (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
                             (map #(konv/array->vec % :selitteet))
                             (map #(assoc % :selitteet (mapv keyword (:selitteet %))))
                             (map #(assoc-in % [:kuittaus :kuittaustyyppi] (keyword (get-in % [:kuittaus :kuittaustyyppi]))))
                             (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
                             (map #(assoc-in % [:ilmoittaja :tyyppi] (keyword (get-in % [:ilmoittaja :tyyppi])))))
                           (q/hae-ilmoitukset db
                                              (annettu? hallintayksikko) (annettu? urakka)
                                              hallintayksikko urakka
                                              (annettu? aikavali-alku) (annettu? aikavali-loppu)
                                              aikavali-alku aikavali-loppu
                                              (annettu? tyypit) tyypit
                                              (annettu? hakuehto) (str "%" hakuehto "%")
                                              (if (:suljetut tilat) true false) ;; Muuttaa nil arvon tai puuttuvan avaimen
                                              (if (:avoimet tilat) true false) ;; falseksi
                                              ))
        mankeloitu (mapv
                     #(assoc % :kuittaukset
                               (into []
                                     (keep
                                       (fn [ilm]
                                         (when
                                           (and (not (nil? (:id (:kuittaus ilm))))
                                                (= (:id ilm) (:id %)))
                                           (:kuittaus ilm)))
                                       mankeloitava)))
                     (set (map #(dissoc % :kuittaus) mankeloitava)))

        tulos (mapv
                #(assoc % :uusinkuittaus
                          (when-not (empty? (:kuittaukset %))
                            (reduce
                              (fn [a b]
                                (if-not (:kuitattu a)
                                  (:kuitattu b)

                                  (if-not (:kuitattu b)
                                    (:kuitattu a)

                                    (if (t/after? (from-sql-time (:kuitattu a)) (from-sql-time (:kuitattu b)))
                                      (:kuitattu a)
                                      (:kuitattu b)))))
                              (:kuittaukset %))))
                mankeloitu)]
    (log/debug "Löydettiin ilmoitukset: " (map :id mankeloitu))
    (log/debug "Kuittauksilla: " (map :uusinkuittaus tulos))

    tulos))

(defrecord Ilmoitukset []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-ilmoitukset
                      (fn [user tiedot #_[{:keys [hallintayksikko urakka tilat tyypit aikavali hakuehto]} tiedot]]
                        (log/debug ":hae-ilmoitukset")
                        (hae-ilmoitukset (:db this) user (:hallintayksikko tiedot)
                                         (:urakka tiedot) (:tilat tiedot) (:tyypit tiedot) (:aikavali tiedot)
                                         (:hakuehto tiedot))))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-ilmoitukset)

    this))
