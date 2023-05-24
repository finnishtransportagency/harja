(ns harja.palvelin.palvelut.turvallisuuspoikkeamat
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as log]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.liitteet :as liitteet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.turvallisuuspoikkeamat :as q]
            [harja.kyselyt.urakan-tyotunnit :as urakan-tyotunnit-q]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit-d]
            [harja.domain.tierekisteri :as tr]
            [harja.geo :as geo]
            [harja.palvelin.integraatiot.turi.turi-komponentti :as turi]
            [harja.domain.oikeudet :as oikeudet]
            [clj-time.core :as t]
            [harja.id :refer [id-olemassa?]]
            [harja.palvelin.asetukset :as asetukset]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm])
  (:import (java.util Date)))

(defn kasittele-vain-yksi-vamma-ja-ruumiinosa [turpo]
  ;; Aiemmin oli mahdollista kirjata useampi ruumiinosa tai vamma, nyt vain yksi
  ;; Kantaan tukee edelleen useaa arvoa tässä
  ;; (vanhan datan takia ja jos tulevaisuudessa halutaankin kirjata useampi).
  ;; Palautetaan satunnainen ensimmäinen arvo.
  (-> turpo
      (assoc :vammat (first (:vammat turpo)))
      (assoc :vahingoittuneetruumiinosat (first (:vahingoittuneetruumiinosat turpo)))))

(defn hae-urakan-turvallisuuspoikkeamat [db user {:keys [urakka-id alku loppu]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-turvallisuus user urakka-id)
  (let [turpot (konv/sarakkeet-vektoriin
                 (into []
                       q/turvallisuuspoikkeama-xf
                       (q/hae-urakan-turvallisuuspoikkeamat db urakka-id (konv/sql-date alku) (konv/sql-date loppu)))
                 {:korjaavatoimenpide :korjaavattoimenpiteet})]
    (mapv kasittele-vain-yksi-vamma-ja-ruumiinosa turpot)))

(defn- hae-vastuuhenkilon-tiedot [db kayttaja-id]
  (when kayttaja-id
    (first (q/hae-vastuuhenkilon-tiedot db kayttaja-id))))

(defn hae-urakan-turvallisuuspoikkeama [db user {:keys [urakka-id turvallisuuspoikkeama-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-turvallisuus user urakka-id)
  (log/debug "Haetaan turvallisuuspoikkeama " turvallisuuspoikkeama-id " urakalle " urakka-id)
  (let [tyotunnit (::urakan-tyotunnit-d/tyotunnit (urakan-tyotunnit-q/hae-kuluvan-vuosikolmanneksen-tyotunnit db urakka-id))
        tulos (as-> (first (konv/sarakkeet-vektoriin (into []
                                                           q/turvallisuuspoikkeama-xf
                                                           (q/hae-urakan-turvallisuuspoikkeama db turvallisuuspoikkeama-id urakka-id))
                                                     {:kommentti :kommentit
                                                      :korjaavatoimenpide :korjaavattoimenpiteet
                                                      :liite :liitteet}))
                  turpo
                (kasittele-vain-yksi-vamma-ja-ruumiinosa turpo)
                (assoc turpo :korjaavattoimenpiteet
                       (mapv #(assoc % :vastuuhenkilo
                                     (hae-vastuuhenkilon-tiedot db (:vastuuhenkilo %)))
                             (:korjaavattoimenpiteet turpo)))
                (assoc turpo :liitteet (into [] (q/hae-turvallisuuspoikkeaman-liitteet db turvallisuuspoikkeama-id)))
                (assoc turpo :urakan-tyotunnit tyotunnit)
                (update-in turpo [:kommentit]
                           (fn [kommentit]
                             (sort-by :aika (map #(if (nil? (:id (:liite %)))
                                                    (dissoc % :liite)
                                                    %)
                                                 kommentit)))))]
    (log/debug "Tulos: " (pr-str tulos))
    tulos))

(defn- luo-tai-paivita-korjaavatoimenpide
  [db user tp-id {:keys [id turvallisuuspoikkeama kuvaus suoritettu poistettu
                         otsikko tila vastuuhenkilo toteuttaja] :as korjaavatoimenpide}
   urakka]

  (log/debug "Tallennetaan korjaavatoimenpide (" id ") turvallisuuspoikkeamalle " tp-id ".")
  (assert
    (or (nil? turvallisuuspoikkeama) (= turvallisuuspoikkeama tp-id))
    "Korjaavan toimenpiteen 'turvallisuuspoikkeama' pitäisi olla joko tyhjä (uusi korjaava), tai sama kuin parametrina
    annettu turvallisuuspoikkeaman id.")

  (log/debug "Tallenna korjaava toimenpide " (pr-str korjaavatoimenpide))
  (if (id-olemassa? id)
    (q/paivita-korjaava-toimenpide<!
      db
      {:otsikko otsikko
       :tila (name tila)
       :vastuuhenkilo (:id vastuuhenkilo)
       :toteuttaja toteuttaja
       :kuvaus kuvaus
       :suoritettu (when suoritettu
                     (konv/sql-timestamp suoritettu))
       :laatija (:id user)
       :poistettu (or poistettu false)
       :id id
       :tp tp-id
       :urakka urakka})
    (q/luo-korjaava-toimenpide<! db {:tp tp-id
                                     :otsikko otsikko
                                     :tila (name tila)
                                     :vastuuhenkilo (:id vastuuhenkilo)
                                     :toteuttaja toteuttaja
                                     :kuvaus kuvaus
                                     :suoritettu (when suoritettu
                                                   (konv/sql-timestamp suoritettu))
                                     :laatija (:id user)})))

(defn- luo-tai-paivita-korjaavat-toimenpiteet [db user korjaavattoimenpiteet tp-id urakka]
  (when-not (empty? korjaavattoimenpiteet)
    (doseq [korjaavatoimenpide korjaavattoimenpiteet]
      (log/debug "Lisätään turvallisuuspoikkeamalle korjaava toimenpide, tai muokataan sitä.")
      (luo-tai-paivita-korjaavatoimenpide db user tp-id korjaavatoimenpide urakka))))

(def oletusparametrit {:ulkoinen_id nil
                       :ilmoittaja_etunimi nil
                       :ilmoittaja_sukunimi nil
                       :alkuosa nil
                       :numero nil
                       :alkuetaisyys nil
                       :loppuetaisyys nil
                       :loppuosa nil
                       :ilmoitukset_lahetetty nil
                       :lahde "harja-ui"})

(defn- juurisyy-kentat [turvallisuuspoikkeama]
  {:juurisyy1 (some-> turvallisuuspoikkeama :juurisyy1 name)
   :juurisyy1-selite (:juurisyy1-selite turvallisuuspoikkeama)
   :juurisyy2 (some-> turvallisuuspoikkeama :juurisyy2 name)
   :juurisyy2-selite (:juurisyy2-selite turvallisuuspoikkeama)
   :juurisyy3 (some-> turvallisuuspoikkeama :juurisyy3 name)
   :juurisyy3-selite (:juurisyy3-selite turvallisuuspoikkeama)})

(defn- luo-tai-paivita-turvallisuuspoikkeama
  [db user {:keys [id urakka tapahtunut tyontekijanammatti tyontekijanammattimuu
                   kuvaus vammat sairauspoissaolopaivat sairaalavuorokaudet sijainti tr
                   vahinkoluokittelu vakavuusaste vahingoittuneetruumiinosat tyyppi
                   sairauspoissaolojatkuu seuraukset vaylamuoto toteuttaja tilaaja
                   otsikko paikan-kuvaus vaaralliset-aineet
                   turvallisuuskoordinaattorietunimi turvallisuuskoordinaattorisukunimi
                   ilmoituksetlahetetty tila]
            :as turvallisuuspoikkeama}]
  (let [sijainti (and sijainti (geo/geometry (geo/clj->pg sijainti)))
        vaarallisten-aineiden-kuljetus? (boolean (some #{:vaarallisten-aineiden-kuljetus}
                                                       vaaralliset-aineet))
        vaarallisten-aineiden-vuoto? (boolean (some #{:vaarallisten-aineiden-vuoto}
                                                    vaaralliset-aineet))
        parametrit
        (merge oletusparametrit
               tr
               {:urakka urakka
                :tapahtunut (konv/sql-timestamp tapahtunut)
                :kasitelty (when (= tila :suljettu)
                             (konv/sql-timestamp (c/to-date (t/now))))
                :ammatti (some-> tyontekijanammatti name)
                :ammatti_muu tyontekijanammattimuu
                :kuvaus kuvaus
                :vammat (konv/seq->array [vammat])
                :poissa sairauspoissaolopaivat
                :sairaalassa sairaalavuorokaudet
                :tyyppi (konv/seq->array tyyppi)
                :kayttaja (:id user)
                :vahinkoluokittelu (konv/seq->array vahinkoluokittelu)
                :vakavuusaste (name vakavuusaste)
                :toteuttaja toteuttaja
                :tilaaja tilaaja
                :sijainti sijainti
                :vahingoittuneet_ruumiinosat (konv/seq->array [vahingoittuneetruumiinosat])
                :sairauspoissaolo_jatkuu sairauspoissaolojatkuu
                :aiheutuneet_seuraukset seuraukset
                :vaylamuoto (name vaylamuoto)
                :laatija (:id user)
                :turvallisuuskoordinaattori_etunimi turvallisuuskoordinaattorietunimi
                :turvallisuuskoordinaattori_sukunimi turvallisuuskoordinaattorisukunimi
                :tapahtuman_otsikko otsikko
                :paikan_kuvaus paikan-kuvaus
                :vaarallisten_aineiden_kuljetus vaarallisten-aineiden-kuljetus?
                :vaarallisten_aineiden_vuoto (if (not vaarallisten-aineiden-kuljetus?)
                                               false
                                               vaarallisten-aineiden-vuoto?)
                :tila (name tila)
                :ilmoitukset_lahetetty (when ilmoituksetlahetetty
                                         (konv/sql-timestamp ilmoituksetlahetetty))}
               (juurisyy-kentat turvallisuuspoikkeama))]
    (if (id-olemassa? id)
      (do (q/paivita-turvallisuuspoikkeama! db (assoc parametrit :id id))
          id)
      (:id (q/luo-turvallisuuspoikkeama<! db parametrit)))))

(defn- tallenna-turvallisuuspoikkeaman-kommentti [db user uusi-kommentti urakka tp-id]
  (when uusi-kommentti
    (log/debug "Turvallisuuspoikkeamalle lisätään uusi kommentti.")
    (let [liite (some->> uusi-kommentti
                         :liite
                         :id
                         (liitteet/hae-urakan-liite-id db urakka)
                         first
                         :id)
          kommentti (kommentit/luo-kommentti<! db
                                               nil
                                               (:kommentti uusi-kommentti)
                                               liite
                                               (:id user))]
      (q/liita-kommentti<! db tp-id (:id kommentti)))))

(defn tallenna-turvallisuuspoikkeaman-liite [db turvallisuuspoikkeama]
  (when-let [uusi-liite (:uusi-liite turvallisuuspoikkeama)]
    (log/info "UUSI LIITE: " uusi-liite)
    (q/liita-liite<! db (:id turvallisuuspoikkeama) (:id uusi-liite))))

(defn tallenna-turvallisuuspoikkeama-kantaan [db user tp korjaavattoimenpiteet uusi-kommentti urakka]
  (jdbc/with-db-transaction [db db]
    (let [tp-id (luo-tai-paivita-turvallisuuspoikkeama db user tp)
          tyotunnit (:urakan-tyotunnit tp)]
      (when tyotunnit
        (urakan-tyotunnit-q/paivita-urakan-kuluvan-vuosikolmanneksen-tyotunnit db urakka tyotunnit))
      (tallenna-turvallisuuspoikkeaman-kommentti db user uusi-kommentti (:urakka tp) tp-id)
      (tallenna-turvallisuuspoikkeaman-liite db tp)
      (luo-tai-paivita-korjaavat-toimenpiteet db user korjaavattoimenpiteet tp-id urakka)
      tp-id)))

(defn vaadi-turvallisuuspoikkeama-kuuluu-urakkaan [db urakka-id turvallisuuspoikkeama-id]
  (when (id-olemassa? turvallisuuspoikkeama-id)
    (let [turpon-todellinen-urakka-id (:urakka (first
                                                 (q/hae-turvallisuuspoikkeaman-urakka db turvallisuuspoikkeama-id)))]
      (log/debug "Tarkistetaan, että väitetty urakka-id " urakka-id " = " turpon-todellinen-urakka-id)
      (when (not= turpon-todellinen-urakka-id urakka-id)
        (throw (SecurityException. "Annettu turvallisuuspoikkeama ei kuulu väitettyyn urakkaan."))))))

(defn turvallisuuspoikkeaman-data-validi?
  [{:keys [otsikko tapahtunut tyyppi vahinkoluokittelu vakavuusaste
           tr tila kuvaus juurisyy1]}]
  (and (not (empty? otsikko))
       (instance? Date tapahtunut)
       (not (empty? tyyppi))
       (not (empty? vahinkoluokittelu))
       (not (nil? vakavuusaste))
       (tr/validi-osoite? tr)
       (not (nil? tila))
       (not (empty? kuvaus))
       (or (not (contains? tyyppi :tyotapaturma))
           (not (nil? juurisyy1)))))

(defn tallenna-turvallisuuspoikkeama [turi db user {:keys [tp korjaavattoimenpiteet uusi-kommentti hoitokausi]}]
  (let [{:keys [id urakka urakan-tyotunnit]} tp]
    (log/debug "Tallennetaan turvallisuuspoikkeama " id " urakkaan " urakka)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-turvallisuus user urakka)
    ;; Tarkista kaiken varalta, että annettu turpo-id kuuluu annettuun urakkaan
    (vaadi-turvallisuuspoikkeama-kuuluu-urakkaan db urakka id)

    (let [id (tallenna-turvallisuuspoikkeama-kantaan db user tp korjaavattoimenpiteet uusi-kommentti urakka)]
      (when turi
        ;; Turi-lähetystä ei pidä sitoa transaktioon, muuten voi jäädä jumiin.
        (turi/laheta-turvallisuuspoikkeama turi id)
        (when (and (asetukset/ominaisuus-kaytossa? :urakan-tyotunnit) urakan-tyotunnit)
          (let [kolmannes (urakan-tyotunnit-d/kuluva-vuosikolmannes)]
            (turi/laheta-urakan-vuosikolmanneksen-tyotunnit
              turi
              urakka
              (::urakan-tyotunnit-d/vuosi kolmannes)
              (::urakan-tyotunnit-d/vuosikolmannes kolmannes))))))

    (hae-urakan-turvallisuuspoikkeamat db
                                       user
                                       {:urakka-id urakka
                                        :alku (first hoitokausi)
                                        :loppu (second hoitokausi)})))

(defn hae-hakulomakkeen-kayttajat [db user hakuehdot]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-turvallisuus user (:urakka-id hakuehdot))
  (log/debug "Haetaan käyttäjät hakuehdoilla: " (pr-str hakuehdot))
  (jdbc/with-db-transaction [db db]
    (into [] (q/hae-kayttajat-parametreilla db {:kayttajanimi (or (:kayttajanimi hakuehdot) "")
                                                :etunimi (or (:etunimi hakuehdot) "")
                                                :sukunimi (or (:sukunimi hakuehdot) "")}))))

(defrecord Turvallisuuspoikkeamat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelut (:http-palvelin this)
                       :hae-turvallisuuspoikkeamat
                       (fn [user tiedot]
                         (hae-urakan-turvallisuuspoikkeamat (:db this) user tiedot))

                       :hae-turvallisuuspoikkeaman-hakulomakkeen-kayttajat
                       (fn [user hakuehdot]
                         (hae-hakulomakkeen-kayttajat (:db this) user hakuehdot))

                       :hae-turvallisuuspoikkeama
                       (fn [user tiedot]
                         (hae-urakan-turvallisuuspoikkeama (:db this) user tiedot))

                       :tallenna-turvallisuuspoikkeama
                       (fn [user tiedot]
                         (if (turvallisuuspoikkeaman-data-validi? (:tp tiedot))
                           (tallenna-turvallisuuspoikkeama (:turi this) (:db this) user tiedot)
                           {:virhe "Kaikkia pakollisia tietoja ei ole annettu"})))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-turvallisuuspoikkeamat
                     :tallenna-turvallisuuspoikkeama)

    this))
