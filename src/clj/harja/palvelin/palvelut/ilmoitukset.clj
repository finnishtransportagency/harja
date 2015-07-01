(ns harja.palvelin.palvelut.ilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]

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
              (mapv annettu? p)))

          (if (map? p)
            (do
              (if (empty? p)
                false
                (mapv #(annettu? (val %)) p)))

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
        _ (log/debug "Tyypit annettu ja tyypit: " (not (nil? (some true? (annettu? tyypit)))) tyypit)
        _ (log/debug "Hakuehto annettu ja hakuehto:" (annettu? hakuehto) (str "%" hakuehto "%"))
        _ (log/debug "Suljetut ja/tai avoimet?" (if (:suljetut tilat) true false) (if (:avoimet tilat) true false))
        mankeloitava (into []
                           (comp
                             (map konv/alaviiva->rakenne)
                             (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
                             (map #(assoc % :ilmoituksenselite (keyword (:ilmoituksenselite %))))
                             (map #(assoc % :kuittaustyyppi (keyword (:kuittaustyyppi %))))
                             (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
                             (map #(assoc % :ilmoittajatyyppi (keyword (:ilmoittajatyyppi %)))))
                           (q/hae-ilmoitukset db
                                              (annettu? hallintayksikko) (annettu? urakka)
                                              hallintayksikko urakka
                                              (annettu? aikavali-alku) (annettu? aikavali-loppu)
                                              aikavali-alku aikavali-loppu
                                              (not (nil? (some true? (annettu? tyypit)))) tyypit
                                              (annettu? hakuehto) (str "%" hakuehto "%")
                                              (if (:suljetut tilat) true false) ;; Muuttaa nil arvon tai puuttuvan avaimen
                                              (if (:avoimet tilat) true false) ;; falseksi
                                              ))]

    (log/debug mankeloitava)))

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
