(ns harja.palvelin.palvelut.siltatarkastukset
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.siltatarkastukset :as q]
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
  (roolit/vaadi-lukuoikeus-urakkaan user urakka-id)
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
                (filter #(and (not= (:rikki_ennen %) 0)
                              (= (:rikki_nyt %) 0))))
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
                (filter #(and (not= (:rikki_ennen %) 0)
                              (= (:rikki_nyt %) 0))))
          (q/hae-urakan-sillat-korjatut db urakka-id))))


                  
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
  [db {:keys [id kohteet] :as siltatarkastus}]
  (doseq [[kohde [tulos lisatieto]] kohteet]
    (q/paivita-siltatarkastuksen-kohteet! db tulos lisatieto id kohde))
  siltatarkastus)

(defn- luo-siltatarkastus [db user {:keys [silta-id urakka-id tarkastaja tarkastusaika kohteet]}]
  (let [luotu-tarkastus (q/luo-siltatarkastus<! db silta-id urakka-id (konv/sql-date tarkastusaika) tarkastaja (:id user))
        id (:id luotu-tarkastus)]
    (doseq [[kohde [tulos lisatieto]] kohteet]
      (q/luo-siltatarkastuksen-kohde<! db tulos lisatieto id kohde))
    (assoc luotu-tarkastus
      :kohteet kohteet)))
  
(defn tallenna-siltatarkastus!
  "Tallentaa tai päivittäää siltatarkastuksen tiedot."
  [db user {:keys [id tarkastaja silta-id urakka-id tarkastusaika kohteet] :as siltatarkastus}]
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus urakka-id)
  (jdbc/with-db-transaction [c db]
    (let [tarkastus (if id
                      ;; Olemassaoleva tarkastus, päivitetään kohteet
                      (paivita-siltatarkastuksen-kohteet! c siltatarkastus)
                      
                      ;; Ei id:tä, kyseessä on uusi siltatarkastus, tallennetaan uusi tarkastus
                      ;; ja sen kohteet
                      (luo-siltatarkastus c user siltatarkastus))]
      
      (hae-siltatarkastus c (:id tarkastus)))))

      

(defn poista-siltatarkastus!
  "Merkitsee siltatarkastuksen poistetuksi"
  [db user {:keys [urakka-id silta-id siltatarkastus-id]}]
  (roolit/vaadi-rooli-urakassa user roolit/toteumien-kirjaus urakka-id)
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
