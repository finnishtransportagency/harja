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
            [harja.geo :as geo]))

(def turvallisuuspoikkeama-xf
  (comp (map konv/alaviiva->rakenne)
        (geo/muunna-pg-tulokset :sijainti)
        (map #(konv/array->vec % :tyyppi))
        (map #(assoc % :tyyppi (mapv keyword (:tyyppi %))))
        (map #(assoc-in % [:kommentti :tyyppi] (keyword (get-in % [:kommentti :tyyppi]))))))

(defn hae-turvallisuuspoikkeamat [db user {:keys [urakka-id alku loppu]}]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (log/debug "Haetaan turvallisuuspoikkeamia urakasta " urakka-id ", aikaväliltä " alku " - " loppu)
  (let [tulos (into []
                    turvallisuuspoikkeama-xf
                    (q/hae-urakan-turvallisuuspoikkeamat db urakka-id (konv/sql-date alku) (konv/sql-date loppu)))]
    (log/debug "Löydettiin turvallisuuspoikkeamat: " (pr-str (mapv :id tulos)))
    tulos))

(defn hae-turvallisuuspoikkeama [db user {:keys [urakka-id turvallisuuspoikkeama-id]}]
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
  (log/debug "Haetaan turvallisuuspoikkeama " turvallisuuspoikkeama-id " urakalle " urakka-id)
  (-> (first (konv/sarakkeet-vektoriin (into []
                                             turvallisuuspoikkeama-xf
                                             (q/hae-turvallisuuspoikkeama db turvallisuuspoikkeama-id urakka-id))
                                       {:kommentti :kommentit
                                        :korjaavatoimenpide :korjaavattoimenpiteet
                                        :liite :liitteet}))

      (update-in [:kommentit]
                 (fn [kommentit]
                   (sort-by :aika (map #(if (nil? (:id (:liite %)))
                                          (dissoc % :liite)
                                          %)
                                       kommentit))))))
  
(defn luo-tai-paivita-korjaavatoimenpide
  [db user tp-id {:keys [id turvallisuuspoikkeama kuvaus suoritettu vastaavahenkilo]}]

  (log/debug "Tallennetaan korjaavatoimenpide ("id") turvallisuuspoikkeamalle " tp-id ".")
  ;; Jos tämä assertti failaa, joku on hassusti
  (assert
    (or (nil? turvallisuuspoikkeama) (= turvallisuuspoikkeama tp-id))
    "Korjaavan toimenpiteen 'turvallisuuspoikkeama' pitäisi olla joko tyhjä (uusi korjaava), tai sama kuin parametrina
    annettu turvallisuuspoikkeaman id.")

  (if-not (or (nil? id) (neg? id))
    (q/paivita-korjaava-toimenpide<! db kuvaus (konv/sql-timestamp suoritettu) vastaavahenkilo id tp-id)

    (q/luo-korjaava-toimenpide<! db tp-id kuvaus (konv/sql-timestamp suoritettu) vastaavahenkilo)))

(defn luo-tai-paivita-turvallisuuspoikkeama
  [db user
   {:keys
    [id urakka tapahtunut paattynyt kasitelty tyontekijanammatti tyotehtava kuvaus vammat sairauspoissaolopaivat
     sairaalavuorokaudet sijainti tr
     tyyppi]}]

  (log/debug "tallennetaan tyypit: " (str "{" (clojure.string/join "," (map name tyyppi)) "}"))

  ;; Tässä on nyt se venäläinen homma.
  ;; Yesql <0.5 tukee ainoastaan "positional" argumentteja, joita Clojuressa voi olla max 20.
  ;; Nämä kyselyt vaativat 21 (!!) argumenttia, joten kyselyt piti katkaista kahtia.
  ;; Toteuttamisen hetkellä Yesql 0.5 oli vasta betassa. Migraatio on sen verran iso homma,
  ;; että betan vuoksi sitä ei liene järkevää tehdä.
  (let [sijainti (and sijainti (geo/geometry (geo/clj->pg sijainti)))
        tr_numero (:numero tr)
        tr_alkuetaisyys (:alkuetaisyys tr)
        tr_loppuetaisyys (:loppuetaisyys tr)
        tr_alkuosa (:alkuosa tr)
        tr_loppuosa (:loppuosa tr)]
    (if id
     (do (q/paivita-turvallisuuspoikkeama<! db urakka (konv/sql-timestamp tapahtunut) (konv/sql-timestamp paattynyt)
                                            (konv/sql-timestamp kasitelty) tyontekijanammatti tyotehtava
                                            kuvaus vammat sairauspoissaolopaivat sairaalavuorokaudet
                                            (str "{" (clojure.string/join "," (map name tyyppi)) "}")
                                            (:id user) id)
         (q/aseta-turvallisuuspoikkeaman-sijainti! db
                                                   sijainti
                                                   tr_numero tr_alkuetaisyys tr_loppuetaisyys tr_alkuosa tr_loppuosa id)
         id)

     (let [id (:id (q/luo-turvallisuuspoikkeama<! db urakka (konv/sql-timestamp tapahtunut) (konv/sql-timestamp paattynyt)
                                                  (konv/sql-timestamp kasitelty) tyontekijanammatti tyotehtava
                                                  kuvaus vammat sairauspoissaolopaivat sairaalavuorokaudet
                                                  (str "{" (clojure.string/join "," (map name tyyppi)) "}") (:id user)))]
       (q/aseta-turvallisuuspoikkeaman-sijainti! db
                                                 sijainti tr_numero tr_alkuetaisyys tr_loppuetaisyys tr_alkuosa tr_loppuosa id)
       id))))

(defn tallenna-turvallisuuspoikkeama [db user {:keys [tp korjaavattoimenpiteet uusi-kommentti hoitokausi]}]
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

      (when korjaavattoimenpiteet
        (doseq [korjaavatoimenpide korjaavattoimenpiteet]
          (log/debug "Lisätään turvallisuuspoikkeamalle korjaava toimenpide, tai muokataan sitä.")

          (luo-tai-paivita-korjaavatoimenpide c user id korjaavatoimenpide)))

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
                         (tallenna-turvallisuuspoikkeama (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-turvallisuuspoikkeamat
                     :tallenna-turvallisuuspoikkeama)

    this))
