(ns harja.palvelin.palvelut.sopimukset
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.sopimukset :as q]
            [harja.domain.sopimus :as sopimus]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [harja.id :refer [id-olemassa?]]))

(defn hae-harjassa-luodut-sopimukset [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (log/debug "PALAUTA SOPPARIT:" (pr-str (into []
                                        (map konv/alaviiva->rakenne)
                                        (q/hae-harjassa-luodut-sopimukset db))))
    (into []
          (map konv/alaviiva->rakenne)
          (q/hae-harjassa-luodut-sopimukset db))))

(defn- paivita-sopimusta! [db user {:keys [id nimi alkupvm loppupvm paasopimus]}]
  (log/debug "Päivitetään sopimusta " nimi)
  (q/paivita-harjassa-luotu-sopimus<! db {:kayttaja (:id user)
                                          :id id
                                          :nimi nimi
                                          :alkupvm alkupvm
                                          :loppupvm loppupvm
                                          :paasopimus paasopimus}))

(defn luo-uusi-sopimus! [db user {:keys [nimi alkupvm loppupvm paasopimus]}]
  (log/debug "Luodaan uusi sopimus nimellä " nimi)
  (q/luo-harjassa-luotu-sopimus<! db {:kayttaja (:id user)
                                      :nimi nimi
                                      :alkupvm alkupvm
                                      :loppupvm loppupvm
                                      :paasopimus paasopimus}))

(defn tallenna-sopimus [db user sopimus]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)

    (jdbc/with-db-transaction [db db]
      (if (id-olemassa? (:id sopimus))
        (paivita-sopimusta! db user sopimus)
        (luo-uusi-sopimus! db user sopimus)))))

(defrecord Sopimukset []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu http
      :hae-harjassa-luodut-sopimukset
      (fn [user _]
        (hae-harjassa-luodut-sopimukset db user))
      {:vastaus-spec ::sopimus/hae-harjassa-luodut-sopimukset-vastaus})
    (julkaise-palvelu http
      :tallenna-sopimus
      (fn [user tiedot]
        (tallenna-sopimus db user tiedot)))

    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-harjassa-luodut-sopimukset)

    this))