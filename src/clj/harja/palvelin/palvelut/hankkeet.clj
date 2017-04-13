(ns harja.palvelin.palvelut.hankkeet
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.hankkeet :as q]
            [harja.domain.hanke :as hanke]
            [harja.id :as id]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [clojure.java.jdbc :as jdbc]))

(defn hae-paattymattomat-vesivaylahankkeet [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (q/hae-paattymattomat-vesivaylahankkeet db)))

(defn hae-harjassa-luodut-hankkeet [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (q/hae-harjassa-luodut-hankkeet db)))

(defn tallenna-hanke [db user {:keys [nimi alkupvm loppupvm] :as hanke}]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
    (jdbc/with-db-transaction [db db]
      (let [tallennus-params {:nimi nimi
                              :alkupvm alkupvm
                              :loppupvm loppupvm}]
        (if (id/id-olemassa? hanke)
         (q/paivita-hanke<! db (assoc tallennus-params :id (:id hanke)))
         (q/luo-hanke<! db tallennus-params)))

      (hae-harjassa-luodut-hankkeet db user))))

(defrecord Hankkeet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelut
      http
      :hae-paattymattomat-vesivaylahankkeet
      (fn [user _]
        (hae-paattymattomat-vesivaylahankkeet db user))
      :hae-harjassa-luodut-hankkeet
      (fn [user _]
        (hae-harjassa-luodut-hankkeet db user))
      {:vastaus-spec ::hanke/hae-harjassa-luodut-hankkeet-vastaus}
      :tallenna-hanke
      (fn [user _]
        (tallenna-hanke db user))
      {:kysely-spec ::hanke/tallenna-hanke-vastaus-kysely
       :vastaus-spec ::hanke/tallenna-hanke-vastaus})

    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-paattymattomat-vesivaylahankkeet
                     :hae-harjassa-luodut-hankkeet
                     :tallenna-hanke)

    this))