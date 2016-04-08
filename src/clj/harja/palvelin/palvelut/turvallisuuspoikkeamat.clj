(ns harja.palvelin.palvelut.turvallisuuspoikkeamat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.domain.roolit :as roolit]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.liitteet :as liitteet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.turvallisuuspoikkeamat :as q]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]))

(def turvallisuuspoikkeama-xf
  (comp (map konv/alaviiva->rakenne)
        (geo/muunna-pg-tulokset :sijainti)
        (map #(konv/array->set % :tyyppi))
        (map #(konv/string-set->keyword-set % :tyyppi))
        (map #(konv/array->set % :vahinkoluokittelu))
        (map #(konv/string-set->keyword-set % :vahinkoluokittelu))
        (map #(konv/array->set % :vahingoittuneetruumiinosat))
        (map #(konv/string-set->keyword-set % :vahingoittuneetruumiinosat))
        (map #(konv/array->set % :vammat))
        (map #(konv/string-set->keyword-set % :vammat))
        (map #(konv/string->keyword % :vakavuusaste))
        (map #(konv/string->keyword % :vaylamuoto))
        (map #(konv/string->keyword % :tyontekijanammatti))
        (map #(konv/string-polusta->keyword % [:kommentti :tyyppi]))))

(defn hae-turvallisuuspoikkeamat [db user {:keys [urakka-id alku loppu]}]
  (when urakka-id (roolit/vaadi-lukuoikeus-urakkaan user urakka-id))
  (konv/sarakkeet-vektoriin
    (into []
          turvallisuuspoikkeama-xf
          (q/hae-urakan-turvallisuuspoikkeamat db urakka-id (konv/sql-date alku) (konv/sql-date loppu)))
    {:korjaavatoimenpide :korjaavattoimenpiteet}))

(defn hae-turvallisuuspoikkeama [db user {:keys [urakka-id turvallisuuspoikkeama-id]}]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (log/debug "Haetaan turvallisuuspoikkeama " turvallisuuspoikkeama-id " urakalle " urakka-id)
  (let [tulos (-> (first (konv/sarakkeet-vektoriin (into []
                                                         turvallisuuspoikkeama-xf
                                                         (q/hae-urakan-turvallisuuspoikkeama db turvallisuuspoikkeama-id urakka-id))
                                                   {:kommentti :kommentit
                                                    :korjaavatoimenpide :korjaavattoimenpiteet
                                                    :liite :liitteet}))

                  (update-in [:kommentit]
                             (fn [kommentit]
                               (sort-by :aika (map #(if (nil? (:id (:liite %)))
                                                     (dissoc % :liite)
                                                     %)
                                                   kommentit)))))]
    tulos))

(defn luo-tai-paivita-korjaavatoimenpide
  [db user tp-id {:keys [id turvallisuuspoikkeama kuvaus suoritettu vastaavahenkilo poistettu]}]

  (log/debug "Tallennetaan korjaavatoimenpide (" id ") turvallisuuspoikkeamalle " tp-id ".")
  ;; Jos tämä assertti failaa, joku on hassusti
  (assert
    (or (nil? turvallisuuspoikkeama) (= turvallisuuspoikkeama tp-id))
    "Korjaavan toimenpiteen 'turvallisuuspoikkeama' pitäisi olla joko tyhjä (uusi korjaava), tai sama kuin parametrina
    annettu turvallisuuspoikkeaman id.")

  (if-not (or (nil? id) (neg? id))
    (q/paivita-korjaava-toimenpide<! db kuvaus (konv/sql-timestamp suoritettu) vastaavahenkilo (or poistettu false) id tp-id)

    (q/luo-korjaava-toimenpide<! db tp-id kuvaus (konv/sql-timestamp suoritettu) vastaavahenkilo)))

(def oletusparametrit {:ulkoinen_id nil
                       :ilmoittaja_etunimi nil
                       :ilmoittaja_sukunimi nil
                       :alkuosa nil
                       :numero nil
                       :alkuetaisyys nil
                       :loppuetaisyys nil
                       :loppuosa nil
                       :ilmoitukset_lahetetty nil})

(defn luo-tai-paivita-turvallisuuspoikkeama
  [db user {:keys [id urakka tapahtunut paattynyt kasitelty tyontekijanammatti tyontekijanammattimuu
                   tyotehtava kuvaus vammat sairauspoissaolopaivat sairaalavuorokaudet sijainti tr
                   vahinkoluokittelu vakavuusaste vahingoittuneetruumiinosat tyyppi
                   sairauspoissaolojatkuu seuraukset vaylamuoto toteuttaja tilaaja
                   laatijaetunimi laatijasukunimi
                   turvallisuuskoordinaattorietunimi turvallisuuskoordinaattorisukunimi
                   ilmoituksetlahetetty]}]
  (let [sijainti (and sijainti (geo/geometry (geo/clj->pg sijainti)))
        parametrit
        (merge oletusparametrit
               tr
               {:urakka urakka
                :tapahtunut (konv/sql-timestamp tapahtunut)
                :paattynyt (konv/sql-timestamp paattynyt)
                :kasitelty (konv/sql-timestamp kasitelty)
                :ammatti (some-> tyontekijanammatti name)
                :ammatti_muu tyontekijanammattimuu
                :tehtava tyotehtava
                :kuvaus kuvaus
                :vammat (konv/seq->array vammat)
                :poissa sairauspoissaolopaivat
                :sairaalassa sairaalavuorokaudet
                :tyyppi (konv/seq->array tyyppi)
                :kayttaja (:id user)
                :vahinkoluokittelu (konv/seq->array vahinkoluokittelu)
                :vakavuusaste (name vakavuusaste)
                :toteuttaja toteuttaja
                :tilaaja tilaaja
                :sijainti sijainti
                :vahingoittuneet_ruumiinosat (konv/seq->array vahingoittuneetruumiinosat)
                :sairauspoissaolo_jatkuu sairauspoissaolojatkuu
                :aiheutuneet_seuraukset seuraukset
                :vaylamuoto (name vaylamuoto)
                :laatija_etunimi laatijaetunimi
                :laatija_sukunimi laatijasukunimi
                :turvallisuuskoordinaattori_etunimi turvallisuuskoordinaattorietunimi
                :turvallisuuskoordinaattori_sukunimi turvallisuuskoordinaattorisukunimi
                :ilmoitukset_lahetetty (konv/sql-timestamp ilmoituksetlahetetty)})]
    (if id
      (do (q/paivita-turvallisuuspoikkeama! db (assoc parametrit :id id))
          id)
      (:id (q/luo-turvallisuuspoikkeama<! db parametrit)))))

(defn tallenna-turvallisuuspoikkeama [turi db user {:keys [tp korjaavattoimenpiteet uusi-kommentti hoitokausi]}]
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

      (when-not (empty? korjaavattoimenpiteet)
        (doseq [korjaavatoimenpide korjaavattoimenpiteet]
          (log/debug "Lisätään turvallisuuspoikkeamalle korjaava toimenpide, tai muokataan sitä.")

          (luo-tai-paivita-korjaavatoimenpide c user id korjaavatoimenpide)))

      (when turi (turi/laheta-turvallisuuspoikkeama turi id))

      (hae-turvallisuuspoikkeamat c user {:urakka-id (:urakka tp) :alku (first hoitokausi) :loppu (second hoitokausi)}))))

(defrecord Turvallisuuspoikkeamat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelut (:http-palvelin this)

                       :hae-turvallisuuspoikkeamat
                       (fn [user tiedot]
                         (hae-turvallisuuspoikkeamat (:db this) user tiedot))

                       :hae-turvallisuuspoikkeama
                       (fn [user tiedot]
                         (hae-turvallisuuspoikkeama (:db this) user tiedot))

                       :tallenna-turvallisuuspoikkeama
                       (fn [user tiedot]
                         (tallenna-turvallisuuspoikkeama (:turi this) (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-turvallisuuspoikkeamat
                     :tallenna-turvallisuuspoikkeama)

    this))
