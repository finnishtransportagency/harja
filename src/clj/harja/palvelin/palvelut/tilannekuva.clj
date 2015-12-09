(ns harja.palvelin.palvelut.tilannekuva
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]

            [taoensso.timbre :as log]

            [harja.kyselyt.kayttajat :as kayttajat-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.tilannekuva :as q]
            [harja.geo :as geo]))

(defn tulosta-virhe! [asiat e]
  (log/error (str "Yritettiin hakea tilannekuvaan " asiat ", mutta virhe tapahtui: " (.getMessage e))))

(defn tulosta-tulos! [asiaa tulos]
  (log/debug (str "  - " (count tulos) " " asiaa))
  tulos)

(defn kayttajan-urakoiden-idt
  [db user urakka-id hallintayksikko alku loppu]
  (log/debug urakka-id (:roolit user) alku loppu)
  (if-not (nil? urakka-id)
    (when (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
      (if (vector? urakka-id) urakka-id [urakka-id]))

    (let [urakat
          (if (get (:roolit user) "jarjestelmavastuuhenkilo")
            (if (and alku loppu)
              (mapv :id (urakat-q/hae-kaikki-urakat-aikavalilla db (konv/sql-date alku) (konv/sql-date loppu)))
              (mapv :id (urakat-q/hae-kaynnissa-olevat-urakat db)))
            (mapv :urakka_id (kayttajat-q/hae-kayttajan-urakka-roolit db (:id user))))]
      (if (nil? hallintayksikko)
        urakat

        (vec (clojure.set/intersection
               (into #{} (map :id (urakat-q/hae-hallintayksikon-urakat db hallintayksikko)))
               (into #{} urakat)))))))

(defn- hae-ilmoitukset
  [db user {{:keys [tyypit tilat]} :ilmoitukset :as tiedot} urakat]
  (let [haettavat (keep (fn [[avain arvo]] (when arvo avain)) tyypit)]
    (when-not (empty? haettavat)
      (try
        (let [suljetut? (if (:suljetut tilat) true false)
              avoimet? (if (:avoimet tilat) true false)
              tulos (mapv
                      #(assoc % :uusinkuittaus
                                (when-not (empty? (:kuittaukset %))
                                  (:kuitattu (last (sort-by :kuitattu (:kuittaukset %))))))
                      (konv/sarakkeet-vektoriin
                        (into []
                              (comp
                                (geo/muunna-pg-tulokset :sijainti)
                                (map konv/alaviiva->rakenne)
                                (map #(assoc % :urakkatyyppi (keyword (:urakkatyyppi %))))
                                (map #(konv/array->vec % :selitteet))
                                (map #(assoc % :selitteet (mapv keyword (:selitteet %))))
                                (map #(assoc-in % [:kuittaus :kuittaustyyppi] (keyword (get-in % [:kuittaus :kuittaustyyppi]))))
                                (map #(assoc % :ilmoitustyyppi (keyword (:ilmoitustyyppi %))))
                                (map #(assoc-in % [:ilmoittaja :tyyppi] (keyword (get-in % [:ilmoittaja :tyyppi])))))
                              (q/hae-ilmoitukset db urakat avoimet? suljetut? (mapv name haettavat)))
                        {:kuittaus :kuittaukset}))]
          ;;(log/debug "Löydettiin seuraavat ilmoitukset (+ kuittausten lkm):")
          ;;(doall (map #(log/debug (:id %) " (" (count (:kuittaukset %)) ")") tulos))
          tulos)
        (catch Exception e
          (tulosta-virhe! "ilmoituksia" e)
          nil)))))

(defn- hae-paallystystyot
  [db user {:keys [alku loppu yllapito]} urakat]
  (when (:paallystys yllapito)))

(defn- hae-paikkaustyot
  [db user {:keys [alku loppu yllapito]} urakat]
  (when (:paikkaus yllapito)))

(defn- hae-laatupoikkeamat
  [db user {:keys [alku loppu laadunseuranta]} urakat]
  (when (:laatupoikkeamat laadunseuranta)
    (try
      (into []
            (comp
              (geo/muunna-pg-tulokset :sijainti)
              (map konv/alaviiva->rakenne)
              (map #(assoc % :selvitys-pyydetty (:selvityspyydetty %)))
              (map #(dissoc % :selvityspyydetty))
              (map #(assoc % :tekija (keyword (:tekija %))))
              (map #(update-in % [:paatos :paatos]
                               (fn [p]
                                 (when p (keyword p)))))
              (map #(update-in % [:paatos :kasittelytapa]
                               (fn [k]
                                 (when k (keyword k)))))
              (map #(if (nil? (:kasittelyaika (:paatos %)))
                     (dissoc % :paatos)
                     %))
              (q/hae-laatupoikkeamat db urakat alku loppu)))
      (catch Exception e
        (tulosta-virhe! "laatupoikkeamia" e)
        nil))))

(defn- hae-tarkastukset
  [db user {:keys [alku loppu laadunseuranta]} urakat]
  (when (:tarkastukset laadunseuranta)
    (try
      (into []
            (comp
              (geo/muunna-pg-tulokset :sijainti)
              (map konv/alaviiva->rakenne)
              (map (fn [tarkastus]
                     (condp = (:tyyppi tarkastus)
                       :talvihoito (dissoc tarkastus :soratiemittaus)
                       :soratie (dissoc tarkastus :talvihoitomittaus)
                       :tiesto (dissoc tarkastus :soratiemittaus :talvihoitomittaus)
                       :laatu (dissoc tarkastus :soratiemittaus :talvihoitomittaus)
                       :pistokoe (dissoc tarkastus :soratiemittaus :talvihoitomittaus)))))
            (q/hae-tarkastukset db urakat alku loppu))
      (catch Exception e
        (tulosta-virhe! "tarkastuksia" e)
        nil))))

(defn- hae-turvallisuuspoikkeamat
  [db user {:keys [alku loppu turvallisuus]} urakat]
  (when (:turvallisuuspoikkeamat turvallisuus)
    (try
      (konv/sarakkeet-vektoriin
        (into []
              (comp
                (map konv/alaviiva->rakenne)
                (geo/muunna-pg-tulokset :sijainti)
                (map #(konv/array->vec % :tyyppi))
                (map #(assoc % :tyyppi (mapv keyword (:tyyppi %)))))
              (q/hae-turvallisuuspoikkeamat db urakat alku loppu))
        {:korjaavatoimenpide :korjaavattoimenpiteet})
      (catch Exception e
        (tulosta-virhe! "turvallisuuspoikkeamia" e)
        nil))))

(defn- hae-toimenpiteiden-reitit
  [db user {:keys [alue alku loppu talvi kesa]} urakat]
  (let [haettavat-toimenpiteet (into {} (filter (fn [[avain arvo]] (when arvo avain)) (merge talvi kesa)))]
    (when-not (empty? haettavat-toimenpiteet))))

(defn hae-tilannekuvaan
  [db user tiedot]
  (let [urakat (kayttajan-urakoiden-idt db user (:urakka-id tiedot)
                                        (:hallintayksikko tiedot) (:alku tiedot) (:loppu tiedot))]

    ;; Huomaa, että haku voidaan tehdä, vaikka urakoita ei löytyisi: silloin haetaan ainoastaan julkista tietoa!
    (log/debug "Löydettiin tilannekuvaan sisältöä urakoista: " (pr-str urakat))
    {:toimenpiteet           (tulosta-tulos! "toimenpidettä"
                                             (hae-toimenpiteiden-reitit db user tiedot urakat))
     :turvallisuuspoikkeamat (tulosta-tulos! "turvallisuuspoikkeamaa"
                                             (hae-turvallisuuspoikkeamat db user tiedot urakat))
     :tarkastukset           (tulosta-tulos! "tarkastusta"
                                             (hae-tarkastukset db user tiedot urakat))
     :laatupoikkemat         (tulosta-tulos! "laatupoikkeamaa"
                                             (hae-laatupoikkeamat db user tiedot urakat))
     :paikkaus               (tulosta-tulos! "paikkausta"
                                             (hae-paikkaustyot db user tiedot urakat))
     :paallystys             (tulosta-tulos! "paallystysta"
                                             (hae-paallystystyot db user tiedot urakat))
     :ilmoitukset            (tulosta-tulos! "ilmoitusta"
                                             (hae-ilmoitukset db user tiedot urakat))}))

(defrecord Tilannekuva []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-tilannekuvaan
                      (fn [user tiedot]
                        (hae-tilannekuvaan (:db this) user tiedot)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-tilannekuvaan)

    this))