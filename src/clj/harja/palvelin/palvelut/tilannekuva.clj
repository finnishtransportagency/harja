(ns harja.palvelin.palvelut.tilannekuva
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]

            [harja.kyselyt.kayttajat :as kayttajat-q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.tilannekuva :as q]
            [harja.geo :as geo]))

(defn kayttajan-urakoiden-idt
  [db user urakka-id hallintayksikko alku loppu]
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
  (let [haettavat (into {} (filter (fn [[avain arvo]] (when arvo avain)) tyypit))]
    (when-not (empty? haettavat)
      (let [suljetut? (if (:suljetut tilat) true false)
            avoimet? (if (:avoimet tilat) true false)]
        (mapv
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
            {:kuittaus :kuittaukset}))))))

(defn- hae-paallystystyot
  [db user {:keys [alku loppu yllapito]} urakat]
  (when (:paallystys yllapito)))

(defn- hae-paikkaustyot
  [db user {:keys [alku loppu yllapito]} urakat]
  (when (:paikkaus yllapito)))

(defn- hae-laatupoikkeamat
  [db user {:keys [alku loppu laadunseuranta]} urakat]
  (when (:laatupoikkeamat laadunseuranta)
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
            (q/hae-laatupoikkeamat db urakat alku loppu)))))

(defn- hae-tarkastukset
  [db user {:keys [alku loppu laadunseuranta]} urakat]
  (when (:tarkastukset laadunseuranta)
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
          (q/hae-tarkastukset db urakat alku loppu))))

(defn- hae-turvallisuuspoikkeamat
  [db user {:keys [alku loppu turvallisuus]} urakat]
  (when (:turvallisuuspoikkeamat turvallisuus)
    (konv/sarakkeet-vektoriin
      (into []
            (comp
              (map konv/alaviiva->rakenne)
              (geo/muunna-pg-tulokset :sijainti)
              (map #(konv/array->vec % :tyyppi))
              (map #(assoc % :tyyppi (mapv keyword (:tyyppi %)))))
            (q/hae-turvallisuuspoikkeamat db urakat alku loppu))
      {:korjaavatoimenpide :korjaavattoimenpiteet})))

(defn- hae-toimenpiteiden-reitit
  [db user {:keys [alue alku loppu talvi kesa]} urakat]
  (let [haettavat-toimenpiteet (into {} (filter (fn [[avain arvo]] (when arvo avain)) (merge talvi kesa)))]
    (when-not (empty? haettavat-toimenpiteet))))

(defn hae-tilannekuvaan
  [db user tiedot]
  (let [yhdista (fn [& tulokset]
                  (apply (comp vec concat) tulokset))
        urakat (kayttajan-urakoiden-idt db user (:urakka-id tiedot)
                                        (:hallintayksikko tiedot) (:alku tiedot) (:loppu tiedot))]
    (when-not (empty? urakat)
      (yhdista
        (hae-toimenpiteiden-reitit db user tiedot urakat)
        (hae-turvallisuuspoikkeamat db user tiedot urakat)
        (hae-tarkastukset db user tiedot urakat)
        (hae-laatupoikkeamat db user tiedot urakat)
        (hae-paikkaustyot db user tiedot urakat)
        (hae-paallystystyot db user tiedot urakat)
        (hae-ilmoitukset db user tiedot urakat)))))

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