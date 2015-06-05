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


(defn hae-urakan-havainnot [db user {:keys [urakka-id alku loppu]}]
  (oik/vaadi-lukuoikeus-urakkaan user urakka-id)
  (into []
        (havainnot/hae-kaikki-havainnot db urakka-id (konv/sql-timestamp alku) (konv/sql-timestamp loppu))))

(defn- luo-tai-paivita-havainto
  "Luo uuden havainnon tai päivittää olemassaolevan havainnon perustiedot. Palauttaa havainnon id:n."
  [db {:keys [id kohde tekija toimenpideinstanssi aika] :as havainto}]
  (if id
    (do (havainnot/paivita-havainnon-perustiedot! db toimenpideinstanssi (konv/sql-timestamp aika) (name tekija) kohde id)
        id)
    
    (:id (havainnot/luo-havainto<! db toimenpideinstanssi (konv/sql-timestamp aika) (name tekija) kohde))))


(defn hae-havainnon-tiedot [db user havainto-id]
  ;; tämän tulisi palauttaa havainnon kaikki tiedot näkymää varten,
  ;; mukaanlukien kommentit, niiden liitteet sekä sanktiot

  ;; tarkista lukuoikeus urakkaan
  )

(defn tallenna-havainto [db user {:keys [urakka] :as havainto}]
  (log/info "Tuli havainto: " havainto)
  (oik/vaadi-rooli-urakassa user roolit/havaintojen-kirjaus urakka)
  (jdbc/with-db-transaction [c db]
    (let [id (luo-tai-paivita-havainto c havainto)]
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
      
        

      (when (:paatos havainto)
        ;; Urakanvalvoja voi kirjata päätöksen
        (oik/vaadi-rooli-urakassa user roolit/urakanvalvoja urakka)
        (log/info "Kirjataan päätös havainnolle: " id ", päätös: " (:paatos havainto))
        (let [{:keys [kasittelyaika paatos selitys kasittelytapa kasittelytapa-selite]} (:paatos havainto)]
          (havainnot/kirjaa-havainnon-paatos! c
                                              (konv/sql-timestamp kasittelyaika)
                                              (name paatos) selitys
                                              (name kasittelytapa) kasittelytapa-selite
                                              (:id user)
                                              id)))
      
      #_(hae-havainnon-tiedot c user id)
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
                           
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
                     :hae-urakan-havainnot)
    this))
            
