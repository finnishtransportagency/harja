(ns harja.palvelin.palvelut.siltatarkastukset
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.siltatarkastukset :as q]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]))

;; Parsii array_agg haulla haetut kohteet {kohde [tulos lisätieto] ...} mäpiksi
(def kohteet-xf
  (comp (map konv/alaviiva->rakenne)
        (map (fn [rivi]
               (if-let [kohteet (:kohteet (konv/array->vec rivi :kohteet))]
                 (assoc rivi
                   :kohteet
                   (into {}
                         (map (fn [kohde]
                                (let [[_ nro tulos lisatieto] (re-matches #"^(\d+)=(A|B|C|D|BC|BD|CD|BCD|-| ):(.*)$"
                                                                          kohde)]
                                  [(Integer/parseInt nro) [(into #{}
                                                                 (if (= " " tulos)
                                                                   nil
                                                                   tulos)) lisatieto]]))
                              kohteet)))
                 rivi)))))

(defn hae-urakan-sillat
  "Hakee annetun urakan alueen sillat sekä niiden viimeisimmän tarkastuspäivän ja tarkastajan.
  Listaus parametri määrittelee minkä haun mukaan sillat haetaan:

  :kaikki    hakee kaikki sillat (ei kohteita mukana)
  :puutteet  hakee sillat, joilla on viimeisimmässä tarkastuksessa puutteuta
             mukana :kohteet avaimella kohteet, joissa puutteuta
  :korjatut  hakee sillat, joilla on ollut puutteita ja jotka on korjattu"

  [db user urakka-id listaus]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-siltatarkastukset user urakka-id)
  (case listaus
    :kaikki
    (into []
          (geo/muunna-pg-tulokset :alue)
          (q/hae-urakan-sillat db urakka-id))

    :urakan-korjattavat
    (into []
          (comp (geo/muunna-pg-tulokset :alue)
                kohteet-xf
                (filter #(not (empty? (:kohteet %)))))
          (q/hae-urakan-sillat-korjattavat db urakka-id))

    :korjaus-ohjelmoitava
    (into []
          (comp (geo/muunna-pg-tulokset :alue)
                kohteet-xf
                (filter #(not (empty? (:kohteet %)))))
          (q/hae-urakan-sillat-ohjelmoitavat db urakka-id))

    :urakassa-korjatut
    (into []
          (comp (geo/muunna-pg-tulokset :alue)
                kohteet-xf
                (filter #(and (not= (:rikki-ennen %) 0)
                              (= (:rikki-nyt %) 0))))
          (q/hae-urakan-sillat-korjatut db urakka-id))

    ;; DEPRECATED
    :puutteet
    (into []
          (comp (geo/muunna-pg-tulokset :alue)
                kohteet-xf
                (filter #(not (empty? (:kohteet %)))))
          (q/hae-urakan-sillat-puutteet db urakka-id))

    :korjatut
    (into []
          (comp (geo/muunna-pg-tulokset :alue)
                kohteet-xf
                (filter #(and (not= (:rikki-ennen %) 0)
                              (= (:rikki-nyt %) 0))))
          (q/hae-urakan-sillat-korjatut db urakka-id))))

(defn hae-siltatarkastus [db id]
  (first (konv/sarakkeet-vektoriin
           (into []
                 kohteet-xf
                 (q/hae-siltatarkastus db id))
           {:liite :liitteet})))

(defn hae-sillan-tarkastukset
  "Hakee annetun sillan siltatarkastukset"
  [db user {:keys [urakka-id silta-id] :as tiedot}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-siltatarkastukset user urakka-id)
  (konv/sarakkeet-vektoriin
    (into []
          kohteet-xf
          (q/hae-sillan-tarkastukset db silta-id))
    {:liite :liitteet}))

(defn paivita-siltatarkastuksen-kohteet!
  "Päivittää siltatarkastuksen kohteet"
  [db urakka-id {:keys [id kohteet] :as siltatarkastus}]

  (doseq [[kohde [tulos lisatieto]] kohteet]
    (q/paivita-siltatarkastuksen-kohteet! db
                                          (konv/seq->array (sort tulos))
                                          lisatieto
                                          id
                                          kohde
                                          urakka-id)
    )
  siltatarkastus)

(defn paivita-siltatarkastus!
  [db user urakka-id {:keys [id tarkastusaika tarkastaja] :as siltatarkastus}]
  (log/debug "Päivitetään siltatarkastus.")
  (q/paivita-siltatarkastus! db tarkastaja tarkastusaika (:id user) id urakka-id)
  (paivita-siltatarkastuksen-kohteet! db urakka-id siltatarkastus))

(defn- luo-siltatarkastus [db user {:keys [silta-id urakka-id tarkastaja tarkastusaika kohteet]}]
  (let [luotu-tarkastus (q/luo-siltatarkastus<! db silta-id urakka-id (konv/sql-date tarkastusaika)
                                                tarkastaja (:id user) nil "harja-ui")
        id (:id luotu-tarkastus)]
    (doseq [[kohde [tulos lisatieto]] kohteet]
      (q/luo-siltatarkastuksen-kohde<! db
                                       (konv/seq->array (sort tulos))
                                       lisatieto
                                       id
                                       kohde))
    (assoc luotu-tarkastus
      :kohteet kohteet)))

(defn- vaadi-silta-kuuluu-urakkaan [db urakka-id silta-id]
  (when silta-id
    (let [sillan-urakat (konv/pgarray->vector (q/hae-sillan-urakat db silta-id))]
      (log/debug "Tarkistetaan, että silta " silta-id " kuuluu väitettyyn urakkaan " urakka-id)
      (when (or (empty? sillan-urakat)
                (not (some #(= urakka-id %) sillan-urakat)))
        (throw (SecurityException.
                 (format "Siltatarkastusta ei voi kirjata sillalle (%s), koska se ei kuulu urakkaan (%s).
                          Silta on merkitty kuuluvaksi urakoille: %s"
                         silta-id
                         urakka-id
                         sillan-urakat)))))))

(defn tallenna-siltatarkastuksen-liitteet [db tarkastus kohteiden-liitteet]
  (log/debug "Tallenna siltatarkastuksen liitteet: " (pr-str kohteiden-liitteet))
  (doseq [[kohdenumero liitteet] kohteiden-liitteet]
    (doseq [liite liitteet]
      (log/debug "Tallenna liite: " (pr-str (:nimi liite)))
      (q/lisaa-liite-siltatarkastuskohteelle<! db (:id tarkastus) kohdenumero (:id liite))))
  (log/debug "Liitteet tallennettu!"))

(defn tallenna-siltatarkastus!
  "Tallentaa tai päivittäää siltatarkastuksen tiedot."
  [db user {:keys [id tarkastaja silta-id urakka-id tarkastusaika kohteet uudet-liitteet] :as siltatarkastus}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-siltatarkastukset user urakka-id)
  (log/debug "Tallennetaan siltatarkastus: " (pr-str siltatarkastus))
  (jdbc/with-db-transaction [db db]
                            (vaadi-silta-kuuluu-urakkaan db urakka-id silta-id)
                            (let [tarkastus (if id
                                              ;; Olemassaoleva tarkastus, päivitetään kohteet
                                              (paivita-siltatarkastus! db user urakka-id siltatarkastus)

                                              ;; Ei id:tä, kyseessä on uusi siltatarkastus, tallennetaan uusi tarkastus
                                              ;; ja sen kohteet.
                                              (luo-siltatarkastus db user siltatarkastus))]
                              (log/debug "Kohteet tallennettu!")
                              (tallenna-siltatarkastuksen-liitteet db tarkastus uudet-liitteet)
                              (hae-siltatarkastus db (:id tarkastus)))))

(defn poista-siltatarkastus!
  "Merkitsee siltatarkastuksen poistetuksi"
  [db user {:keys [urakka-id silta-id siltatarkastus-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-siltatarkastukset user urakka-id)
  (jdbc/with-db-transaction [c db]
                            (do
                              (log/info "  päivittyi: " (q/poista-siltatarkastus! c siltatarkastus-id urakka-id)))
                            (hae-sillan-tarkastukset c user {:urakka-id urakka-id
                                                             :silta-id  silta-id})))

(defrecord Siltatarkastukset []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)]
      (julkaise-palvelu http :hae-urakan-sillat
                        (fn [user {:keys [urakka-id listaus]}]
                          (hae-urakan-sillat db user urakka-id listaus)))
      (julkaise-palvelu http :hae-sillan-tarkastukset
                        (fn [user tiedot]
                          (hae-sillan-tarkastukset db user tiedot)))
      (julkaise-palvelu http :tallenna-siltatarkastus
                        (fn [user tiedot]
                          (tallenna-siltatarkastus! db user tiedot)))
      (julkaise-palvelu http :poista-siltatarkastus
                        (fn [user tiedot]
                          (poista-siltatarkastus! db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut (:http-palvelin this) :hae-urakan-sillat)
    (poista-palvelut (:http-palvelin this) :hae-sillan-tarkastukset)
    (poista-palvelut (:http-palvelin this) :tallenna-siltatarkastus)
    (poista-palvelut (:http-palvelin this) :poista-siltatarkastus)
    this))
