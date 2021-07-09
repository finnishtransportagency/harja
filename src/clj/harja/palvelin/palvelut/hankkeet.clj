(ns harja.palvelin.palvelut.hankkeet
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.hankkeet :as q]
            [taoensso.timbre :as log]
            [harja.domain.hanke :as hanke]
            [harja.id :as id]
            [namespacefy.core :refer [namespacefy]]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.konversio :as konv]))

(defn hae-harjassa-luodut-hankkeet [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (let [hankkeet (into []
                         (map konv/alaviiva->rakenne)
                         (q/hae-harjassa-luodut-hankkeet db))]
      (namespacefy hankkeet {:ns :harja.domain.hanke
                             :inner {:urakka {:ns :harja.domain.urakka}}}))))

(defn tallenna-hanke
  "Tallentaa yksittäisen hankkeen ja palauttaa sen tiedot"
  [db user hanke]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
    (jdbc/with-db-transaction [db db]
      (let [tallennus-params {:nimi (::hanke/nimi hanke)
                              :alkupvm (::hanke/alkupvm hanke)
                              :loppupvm (::hanke/loppupvm hanke)
                              :kayttaja (:id user)}
            {:keys [id nimi alkupvm loppupvm] :as tallennettu-hanke}
            (if (id/id-olemassa? (::hanke/id hanke))
              (q/paivita-harjassa-luotu-hanke<! db (assoc tallennus-params :id (::hanke/id hanke)))
              (q/luo-harjassa-luotu-hanke<! db tallennus-params))]
        {::hanke/id id
         ::hanke/nimi nimi
         ::hanke/alkupvm alkupvm
         ::hanke/loppupvm loppupvm}))))

(defrecord Hankkeet []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    (julkaise-palvelu http
                      :hae-harjassa-luodut-hankkeet
                      (fn [user _]
                        (hae-harjassa-luodut-hankkeet db user))
                      {:vastaus-spec ::hanke/hae-harjassa-luodut-hankkeet-vastaus})

    (julkaise-palvelu http
                      :tallenna-hanke
                      (fn [user tiedot]
                        (tallenna-hanke db user tiedot))
                      {:kysely-spec ::hanke/tallenna-hanke-kysely
                       :vastaus-spec ::hanke/tallenna-hanke-vastaus})
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-harjassa-luodut-hankkeet
                     :tallenna-hanke)

    this))
