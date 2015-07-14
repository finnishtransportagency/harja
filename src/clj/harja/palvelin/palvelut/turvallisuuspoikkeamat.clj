(ns harja.palvelin.palvelut.turvallisuuspoikkeamat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]

            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.liitteet :as liitteet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.turvallisuuspoikkeamat :as q]))

(defn hae-turvallisuuspoikkeamat [db user {:keys [urakka-id alku loppu]}]
  (log/debug "Haetaan turvallisuuspoikkeamia urakasta " urakka-id ", aikaväliltä " alku " - " loppu)
  (let [mankeloitava (into []
                           (comp (map konv/alaviiva->rakenne)
                                 (harja.geo/muunna-pg-tulokset :sijainti)
                                 (map #(konv/array->vec % :tyyppi))
                                 (map #(assoc % :tyyppi (mapv keyword (:tyyppi %))))
                                 (map #(assoc-in % [:kommentti :tyyppi] (keyword (get-in % [:kommentti :tyyppi])))))
                           (q/hae-urakan-turvallisuuspoikkeamat db urakka-id (konv/sql-date alku) (konv/sql-date loppu)))

        ;; Tässä tehdään suhteellisen monimutkaisia mankelointeja.
        ;; Yhdella turvallisuuspoikkeamalla (tp:llä) voi olla useampia kommentteja,
        ;; useampia liitteitä, sekä kommentteja joilla on liitteitä.
        ;; Tietokantahaku luonnollisesti palauttaa yhden kommentin/liitteen per rivi,
        ;; joten ensimmäisenä kaivetaan ulos uniikit tp:t, ja yhdistetään niiden
        ;; kommentit yhteen taulukkoon
        ;;
        ;; Itse asiassa tämä tehtiin turhankin monimutkaisesti, koska nykyisellään ei voi
        ;; olla liitteitä, jotka eivät liity kommenttiin - tämä ei kuitenkaan ole tietomallin aiheuttama
        ;; rajoite, vaan käyttöliittymän. Ehkä ei siis ollut hukkaan heitettyä aikaa? ;)
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

(defn luo-tai-paivita-korjaavatoimenpide
  [db user tp-id {:keys [id turvallisuuspoikkeama kuvaus suoritettu vastaavahenkilo]}]

  (assert
    (or (nil? turvallisuuspoikkeama) (= turvallisuuspoikkeama tp-id))
    "Korjaavan toimenpiteen 'turvallisuuspoikkeama' pitäisi olla joko tyhjä (uusi korjaava), tai sama kuin parametrina
    annettu turvallisuuspoikkeaman id.")

  (if id
    (do (q/paivita-korjaava-toimenpide<! db kuvaus suoritettu vastaavahenkilo (:id user) id tp-id))

    (:id (q/luo-korjaava-toimenpide<! db tp-id kuvaus suoritettu vastaavahenkilo (:id user))))
  )

(defn luo-tai-paivita-turvallisuuspoikkeama
  [db user
   {:keys
    [id urakka tapahtunut paattynyt kasitelty tyontekijanammatti tyotehtava kuvaus vammat sairauspoissaolopaivat
     sairaalavuorokaudet sijainti tr_numero tr_alkuetaisyys tr_loppuetaisyys tr_alkuosa tr_loppuosa
     tyyppi]}]

  (if id
    (do (q/paivita-turvallisuuspoikkeama<! db urakka (konv/sql-timestamp tapahtunut) (konv/sql-timestamp paattynyt)
                                           (konv/sql-timestamp kasitelty) tyontekijanammatti tyotehtava
                                           kuvaus vammat sairauspoissaolopaivat sairaalavuorokaudet sijainti tr_numero
                                           tr_alkuetaisyys tr_loppuetaisyys tr_alkuosa tr_loppuosa tyyppi (:id user) id)
        id)

    (:id (q/luo-turvallisuuspoikkeama<! db urakka (konv/sql-timestamp tapahtunut) (konv/sql-timestamp paattynyt)
                           (konv/sql-timestamp kasitelty) tyontekijanammatti tyotehtava
                           kuvaus vammat sairauspoissaolopaivat sairaalavuorokaudet sijainti tr_numero
                           tr_alkuetaisyys tr_loppuetaisyys tr_alkuosa tr_loppuosa tyyppi (:id user)))))

(defn tallenna-turvallisuuspoikkeama [db user [tp korjaavatoimenpide uusi-kommentti hoitokausi]]
  (log/debug "Tallennetaan turvallisuuspoikkeama " (:id tp) " urakkaan " (:urakka tp))

  (jdbc/with-db-transaction [c db]
    (let [id (luo-tai-paivita-turvallisuuspoikkeama c user tp)]

      (when uusi-kommentti
        (log/debug "Turvallisuuspoikkeamalle lisätään uusi kommentti.")
        (let [liite (some->> uusi-kommentti
                             :liite
                             :id
                             (liitteet/hae-urakan-liite-id c (:urakka tp))
                             first
                             :id)
              kommentti (kommentit/luo-kommentti<! c
                                                   nil
                                                   (:kommentti uusi-kommentti)
                                                   liite
                                                   (:id user))]
          (q/liita-kommentti<! c id (:id kommentti))))

      (when korjaavatoimenpide
        (log/debug "Lisätään turvallisuuspoikkeamalle korjaava toimenpide, tai muokataan sitä.")

        (luo-tai-paivita-korjaavatoimenpide c user id korjaavatoimenpide))

      (hae-turvallisuuspoikkeamat c user {:urakka-id (:urakka tp) :alku (first hoitokausi) :loppu (second hoitokausi)}))))

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
