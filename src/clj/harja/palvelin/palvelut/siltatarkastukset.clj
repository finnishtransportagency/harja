(ns harja.palvelin.palvelut.siltatarkastukset
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.siltatarkastukset :as q]
            [harja.palvelin.oikeudet :as oik]
            [harja.domain.roolit :as roolit]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

;; Parsii array_agg haulla haetut kohteet {kohde [tulos lisätieto] ...} mäpiksi 
(def kohteet-xf (map (fn [rivi]
                       (if-let [kohteet (:kohteet (konv/array->vec rivi :kohteet))]
                         (assoc rivi
                           :kohteet (into {}
                                          (map (fn [kohde]
                                                 (let [[_ nro tulos lisatieto] (re-matches #"^(\d+)=(A|B|C|D):(.*)$" kohde)]
                                                   [(Integer/parseInt nro) [tulos lisatieto]]))
                                               kohteet)))
                         rivi))))

(defn hae-urakan-sillat
  "Hakee annetun urakan alueen sillat sekä niiden viimeisimmän tarkastuspäivän ja tarkastajan.
Listaus parametri määrittelee minkä haun mukaan sillat haetaan:

 :kaikki    hakee kaikki sillat (ei kohteita mukana)
 :puutteet  hakee sillat, joilla on viimeisimmässä tarkastuksessa puutteuta
            mukana :kohteet avaimella kohteet, joissa puutteuta
  :korjatut  hakee sillat, joilla on ollut puutteita ja jotka on korjattu"
  
  [db user urakka-id listaus]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (comp (geo/muunna-pg-tulokset :alue)
              kohteet-xf
              (filter (if (not= :kaikki listaus)
                        #(not (empty? (:kohteet %)))
                        (constantly true))))
        (case listaus
          :kaikki (q/hae-urakan-sillat db urakka-id)
          :puutteet (q/hae-urakan-sillat-puutteet db urakka-id)
          :korjatut (throw (RuntimeException. "FIXME: implementoi")))))

                  
(defn hae-siltatarkastus [db id]
  (first (into []
               kohteet-xf
               (q/hae-siltatarkastus db id))))

(defn hae-sillan-tarkastukset
  "Hakee annetun sillan siltatarkastukset"
  [db user silta-id]
  ;; FIXME: tarkista oikeudet
  (into []
        kohteet-xf
        (q/hae-sillan-tarkastukset db silta-id)))


(defn paivita-siltatarkastuksen-kohteet!
  "Päivittää siltatarkastuksen kohteet"
  [db {:keys [id kohteet]}]
  (doseq [[kohde [tulos lisatieto]] kohteet]
    (do
      (q/paivita-siltatarkastuksen-kohteet! db tulos lisatieto id kohde))))

(defn- luo-siltatarkastus [db user {:keys [silta-id urakka-id tarkastaja tarkastusaika kohteet]}]
  (q/luo-siltatarkastus<! silta-id urakka-id (konv/sql-date tarkastusaika) tarkastaja (:id user)))
              
(defn tallenna-siltatarkastus!
  "Tallentaa tai päivittäää siltatarkastuksen tiedot."
  [db user {:keys [id tarkastaja silta-id urakka-id tarkastusaika kohteet] :as siltatarkastus}]
  (oik/vaadi-rooli-urakassa user roolit/toteumien-kirjaus urakka-id)
  (jdbc/with-db-transaction [c db]
    (let [tarkastus (if id
                      siltatarkastus
                      
                      ;; Ei id:tä, kyseessä on uusi siltatarkastus
                      (assoc (luo-siltatarkastus c user siltatarkastus)
                        :kohteet kohteet))]
      (paivita-siltatarkastuksen-kohteet! c tarkastus)
      (hae-siltatarkastus c (:id tarkastus)))))

      

(defn poista-siltatarkastus!
  "Merkitsee siltatarkastuksen poistetuksi"
  [db user {:keys [urakka-id silta-id siltatarkastus-id]}]
  (oik/vaadi-rooli-urakassa user oik/rooli-urakanvalvoja urakka-id)
  (jdbc/with-db-transaction [c db]
                              (do
                                (log/info "  päivittyi: " (q/poista-siltatarkastus! c siltatarkastus-id)))
                            (hae-sillan-tarkastukset c user silta-id)))

(defrecord Siltatarkastukset []
  component/Lifecycle
  (start [this]
    (let [db (:db this)
          http (:http-palvelin this)]
      (julkaise-palvelu http :hae-urakan-sillat
                        (fn [user {:keys [urakka-id listaus]}]
                          (hae-urakan-sillat db user urakka-id listaus)))
      (julkaise-palvelu http :hae-sillan-tarkastukset
                        (fn [user silta-id]
                          (hae-sillan-tarkastukset db user silta-id)))
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
    (poista-palvelut (:http-palvelin this) :poista-siltatarkastus)))
