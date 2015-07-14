(ns harja.palvelin.palvelut.turvallisuuspoikkeamat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [clj-time.core :as t]
            [clj-time.coerce :refer [from-sql-time]]

            [harja.kyselyt.turvallisuuspoikkeamat :as q]))

(defn tallenna-turvallisuuspoikkeama [db user tiedot]
  (log/info "Turvallisuuspoikkeaminen tallentaminen on implementoimatta"))

(defn hae-turvallisuuspoikkeamat [db user {:keys [urakka-id alku loppu]}]
  (log/debug "Haetaan turvallisuuspoikkeamia urakasta " urakka-id ", aikaväliltä " alku " - " loppu)
  (let [mankeloitava (into []
                           (comp (map konv/alaviiva->rakenne)
                                 (harja.geo/muunna-pg-tulokset :sijainti)
                                 (map #(konv/array->vec % :tyyppi))
                                 (map #(assoc % :tyyppi (keyword (:tyyppi %))))
                                 (map #(assoc-in % [:kommentti :tyyppi] (keyword (get-in % [:kommentti :tyyppi])))))
                           (q/hae-urakan-turvallisuuspoikkeamat db urakka-id (konv/sql-date alku) (konv/sql-date loppu)))

        ;; Tässä tehdään suhteellisen monimutkaisia mankelointeja.
        ;; Yhdella turvallisuuspoikkeamalla (tp:llä) voi olla useampia kommentteja,
        ;; useampia liitteitä, sekä kommentteja joilla on liitteitä.
        ;; Tietokantahaku luonnollisesti palauttaa yhden kommentin/liitteen per rivi,
        ;; joten ensimmäisenä kaivetaan ulos uniikit tp:t, ja yhdistetään niiden
        ;; kommentit yhteen taulukkoon
        kommentteineen (mapv
                         #(assoc % :kommentit
                                   (into []
                                         (keep
                                           (fn [tp]
                                             (when
                                               (and (not (nil? (get-in tp [:kommentti :id])))
                                                    (= (:id tp) (:id %)))
                                               (:kommentti tp)))
                                           mankeloitava)))
                         (set (map #(dissoc % :kommentti :liite) mankeloitava)))

        ;; Sitten sama tehdään liitteille
        liitteineen (mapv
                      #(assoc % :liitteet
                                (into []
                                      (keep
                                        (fn [tp]
                                          (when
                                            (and (not (nil? (get-in tp [:liite :id])))
                                                 (= (:id tp) (:id %)))
                                            (:liite tp)))
                                        mankeloitava)))
                      (set (map #(dissoc % :kommentti :liite) mankeloitava)))

        ;; Joillain kommenteilla on viittaus liitteen id:hen.
        ;; Korvataan id itse liitteellä.
        liitteet-kommenteissa (mapv
                                (fn [tp]
                                  (assoc tp :kommentit
                                            (mapv
                                              (fn [kommentti]
                                                (if-not (:liite kommentti)
                                                  kommentti

                                                  (assoc
                                                    kommentti
                                                    :liite
                                                    (some
                                                      (fn [liite]
                                                        (when (= (:id liite) (:liite kommentti)) liite))
                                                      (flatten (map :liitteet liitteineen)))))
                                                )
                                              (:kommentit tp)))
                                  )
                                kommentteineen)

        ;; Osa liitteistä on nyt liitetty mukaan kommenttiin.
        ;; TP:n liitteet-vektorista voidaan siis poistaa liitteet, jotka liittyvät
        ;; johonkin kommenttiin
        ilman-redundantteja-liitteita (mapv
                                        (fn[tp]
                                          (assoc tp :liitteet
                                                    (vec (remove nil? (map
                                                                    (fn [liite]
                                                                      (when-not
                                                                        (some
                                                                          (fn [kommentti]
                                                                            (= (get-in kommentti [:liite :id]) (:id liite)))
                                                                          (flatten (map :kommentit liitteet-kommenteissa)))
                                                                        liite))
                                                                    (:liitteet tp))))))
                                        liitteineen)

        ;; Lopuksi tehdään tp, jolla on molemmat kommentit- ja liitteet-vektorit
        yhdistetty (mapv
                     (fn [tp]
                       (assoc tp :liitteet
                                 (some
                                   (fn [tpl]
                                     (when (= (:id tpl) (:id tp)) (:liitteet tpl)))
                                   ilman-redundantteja-liitteita)))
                     liitteet-kommenteissa)

        tulos yhdistetty]
    (log/debug "Löydettiin turvallisuuspoikkeamat: " (pr-str (mapv :id tulos)))
    tulos))

(defrecord Turvallisuuspoikkeamat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelut (:http-palvelin this)

                       :hae-turvallisuuspoikkeamat
                       (fn [user tiedot]
                         (hae-turvallisuuspoikkeamat (:db this) user tiedot))

                       :tallenna-turvallisuuspoikkeama
                       (fn [user tiedot]
                         (tallenna-turvallisuuspoikkeama (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-turvallisuuspoikkeamat
                     :tallenna-turvallisuuspoikkeama)

    this))
