(ns harja.palvelin.palvelut.tilannekuva
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]

            [harja.kyselyt.kayttajat :as kayttajat-q]
            [harja.kyselyt.urakat :as urakat-q]))

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
  [db user {:keys [hallintayksikko urakka-id alku loppu ilmoitukset]}]
  (let [haettavat (into {} (filter (fn [[_ arvo :as v]] (when arvo v)) ilmoitukset))]
    (when-not (empty? haettavat)
      (let [urakat (kayttajan-urakoiden-idt db user urakka-id hallintayksikko alku loppu)]))))

(defn- hae-paallystystyot
  [db user {:keys [hallintayksikko urakka-id alku loppu yllapito]}]
  (when (:paallystys yllapito)
    (let [urakat (kayttajan-urakoiden-idt db user urakka-id hallintayksikko alku loppu)])))

(defn- hae-paikkaustyot
  [db user {:keys [hallintayksikko urakka-id alku loppu yllapito]}]
  (when (:paikkaus yllapito)))

(defn- hae-havainnot
  [db user {:keys [hallintayksikko urakka-id alku loppu laadunseuranta]}]
  (when (:havainnot laadunseuranta)
    (let [urakat (kayttajan-urakoiden-idt db user urakka-id hallintayksikko alku loppu)])))

(defn- hae-tarkastukset
  [db user {:keys [hallintayksikko urakka-id alku loppu laadunseuranta]}]
  (when (:tarkastukset laadunseuranta)
    (let [urakat (kayttajan-urakoiden-idt db user urakka-id hallintayksikko alku loppu)])))

(defn- hae-turvallisuuspoikkeamat
  [db user {:keys [hallintayksikko urakka-id alku loppu turvallisuus]}]
  (when (:turvallisuuspoikkeamat turvallisuus)
    (let [urakat (kayttajan-urakoiden-idt db user urakka-id hallintayksikko alku loppu)])))

(defn- hae-toimenpiteiden-reitit
  [db user {:keys [hallintayksikko urakka-id alue alku loppu talvi kesa]}]
  (let [haettavat-toimenpiteet (into {} (filter (fn [[_ arvo :as v]] (when arvo v)) (merge talvi kesa)))]
    (when-not (empty? haettavat-toimenpiteet)
      (let [urakat (kayttajan-urakoiden-idt db user urakka-id hallintayksikko alku loppu)]))))

(defn hae-tilannekuvaan
  [db user tiedot]
  (let [yhdista (fn [& tulokset]
                  (apply (comp vec concat) tulokset))]
    (yhdista
      (hae-toimenpiteiden-reitit db user tiedot)
      (hae-turvallisuuspoikkeamat db user tiedot)
      (hae-tarkastukset db user tiedot)
      (hae-havainnot db user tiedot)
      (hae-paikkaustyot db user tiedot)
      (hae-paallystystyot db user tiedot)
      (hae-ilmoitukset db user tiedot))))

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