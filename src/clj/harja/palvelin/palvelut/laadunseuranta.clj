(ns harja.palvelin.palvelut.laadunseuranta
  "Laadunseuranta: Tarkastukset, Havainnot ja Sanktiot"

  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]

            [harja.kyselyt.havainnot :as havainnot]
            [harja.kyselyt.kommentit :as kommentit]
            [harja.kyselyt.liitteet :as liitteet]
            
            [harja.palvelin.oikeudet :as oik]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.roolit :as roolit]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]))

(def havainto-xf (comp
                  (map konv/alaviiva->rakenne)
                  (map #(assoc % :tekija (keyword (:tekija %))))))

(defn hae-urakan-havainnot [db user {:keys [urakka-id alku loppu]}]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        havainto-xf
        (havainnot/hae-kaikki-havainnot db urakka-id (konv/sql-timestamp alku) (konv/sql-timestamp loppu))))

(defn- luo-tai-paivita-havainto
  "Luo uuden havainnon tai päivittää olemassaolevan havainnon perustiedot. Palauttaa havainnon id:n."
  [db user {:keys [id kohde tekija toimenpideinstanssi aika] :as havainto}]
  (if id
    (do (havainnot/paivita-havainnon-perustiedot! db toimenpideinstanssi
                                                  (konv/sql-timestamp aika) (name tekija) kohde
                                                  (:id user)
                                                  id)
        id)
    
    (:id (havainnot/luo-havainto<! db toimenpideinstanssi (konv/sql-timestamp aika) (name tekija) kohde (:id user)))))


(defn hae-havainnon-tiedot
  "Hakee yhden havainnon kaiken tiedon muokkausnäkymää varten: havainnon perustiedot, kommentit ja liitteet, päätös.
   Ottaa urakka-id:n ja havainto-id:n. Urakka id:tä käytetään oikeustarkistukseen, havainnon tulee olla annetun urakan
   toimenpiteeseen kytketty."
  [db user urakka-id havainto-id]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (let [havainto (first (into []
                              havainto-xf
                              (havainnot/hae-havainnon-tiedot db urakka-id havainto-id)))]
    (when havainto
      (assoc havainto
        :kommentit (into []
                         (comp (map konv/alaviiva->rakenne)
                               (map #(assoc % :tekija (name (:tekija %))))
                               (map (fn [{:keys [liite] :as kommentti}]
                                      (if (:id liite)
                                        kommentti
                                        (dissoc kommentti :liite)))))
                         (havainnot/hae-havainnon-kommentit db havainto-id))))))

   

(defn tallenna-havainto [db user {:keys [urakka] :as havainto}]
  (log/info "Tuli havainto: " havainto)
  (oik/vaadi-rooli-urakassa user roolit/havaintojen-kirjaus urakka)
  (jdbc/with-db-transaction [c db]
    (let [id (luo-tai-paivita-havainto c user havainto)]
      ;; Luodaan uudet kommentit
      (when-let [uusi-kommentti (:uusi-kommentti havainto)]
        (log/info "UUSI KOMMENTTI: " uusi-kommentti)
        (let [liite (some->> uusi-kommentti
                             :liite
                             :id
                             (liitteet/hae-urakan-liite-id c urakka)
                             first
                             :id)
              kommentti (kommentit/luo-kommentti<! c
                                                   (name (:tekija havainto))
                                                   (:kommentti uusi-kommentti)
                                                   liite
                                                   (:id user))]
          ;; Liitä kommentti havaintoon
          (havainnot/liita-kommentti<! c id (:id kommentti))
          ))
      
        

      (when (:paatos (:paatos havainto))
        ;; Urakanvalvoja voi kirjata päätöksen
        (oik/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka)
        (log/info "Kirjataan päätös havainnolle: " id ", päätös: " (:paatos havainto))
        (let [{:keys [kasittelyaika paatos perustelu kasittelytapa muukasittelytapa]} (:paatos havainto)]
          (havainnot/kirjaa-havainnon-paatos! c
                                              (konv/sql-timestamp kasittelyaika)
                                              (name paatos) perustelu
                                              (name kasittelytapa) muukasittelytapa
                                              (:id user)
                                              id)))
      
      (hae-havainnon-tiedot c user urakka id)
      havainto)))


(defrecord Laadunseuranta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakan-havainnot
                      (fn [user tiedot]
                        (hae-urakan-havainnot db user tiedot)))
    (julkaise-palvelu http-palvelin :tallenna-havainto
                      (fn [user havainto]
                        (tallenna-havainto db user havainto)))
    (julkaise-palvelu http-palvelin :hae-havainnon-tiedot
                      (fn [user {:keys [urakka-id havainto-id]}]
                        (hae-havainnon-tiedot db user urakka-id havainto-id)))
                           
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
                     :hae-urakan-havainnot
                     :tallenna-havainto
                     :hae-havainnon-tiedot)
    this))
            
