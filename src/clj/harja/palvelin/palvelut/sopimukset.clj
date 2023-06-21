(ns harja.palvelin.palvelut.sopimukset
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.sopimukset :as q]
            [harja.domain.sopimus :as sopimus]
            [namespacefy.core :refer [namespacefy]]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konv]
            [clojure.java.jdbc :as jdbc]
            [harja.id :refer [id-olemassa?]]))

(defn hae-harjassa-luodut-sopimukset [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (let [sopimukset (into []
                           (map konv/alaviiva->rakenne)
                           (q/hae-harjassa-luodut-sopimukset db))
          diaarinumerot (q/hae-sopimusten-reimari-diaarinumerot db)
          sopimuksen-diaarinro (fn [id] (:reimari-diaarinro (first (filter #(= (:harja-sopimus-id %) id) diaarinumerot))))
          sopimukset (mapv (fn [m] (assoc m :reimari-diaarinro (sopimuksen-diaarinro (:id m))))  sopimukset)
          vastaus (namespacefy sopimukset {:ns :harja.domain.sopimus
                                           :inner {:urakka {:ns :harja.domain.urakka}}})]
        vastaus)))

(defn vesivayla-kanavien-hoito-sopimukset-vastaus
  [db sopimus-id urakka-id]
  (let [sopimukset (into [] (q/hae-vesivayla-kanavien-hoito-sopimukset db {:sopimus-id sopimus-id
                                                                           :urakka-id urakka-id}))]
    (namespacefy sopimukset
      {:ns :harja.domain.sopimus
       :inner {:urakka {:ns :harja.domain.urakka}}})))

(defn hae-vesivayla-kanavien-hoito-sopimukset [db user {:keys [sopimus-id urakka-id]}]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/voi-lukea? oikeudet/urakat-kanavat-kanavakohteet urakka-id user)
    (vesivayla-kanavien-hoito-sopimukset-vastaus db sopimus-id urakka-id)))

(defn- paivita-sopimusta! [db user sopimus]
  (let [id (::sopimus/id sopimus)
        nimi (::sopimus/nimi sopimus)
        reimari-diaarinro (::sopimus/reimari-diaarinro sopimus)
        alkupvm (::sopimus/alkupvm sopimus)
        loppupvm (::sopimus/loppupvm sopimus)
        paasopimus-id (::sopimus/paasopimus-id sopimus)
        harjassa-luotu-sopimus (q/paivita-harjassa-luotu-sopimus<! db {:kayttaja (:id user)
                                                                       :id id
                                                                       :nimi nimi
                                                                       :alkupvm alkupvm
                                                                       :loppupvm loppupvm
                                                                       :paasopimus paasopimus-id})
        harjassa-luotu-reimari-diaarinumero (if (empty? (q/hae-sopimuksen-reimari-diaarinumero db id))
                                              (q/luo-reimari-diaarinumero-linkki<! db {:harja-sopimus-id id
                                                                                       :reimari-diaarinro reimari-diaarinro})
                                              (q/paivita-reimari-diaarinumero-linkki<! db {:harja-sopimus-id id
                                                                                           :reimari-diaarinro reimari-diaarinro}))]
      (assoc harjassa-luotu-sopimus :reimari-diaarinro (:reimari-diaarinro harjassa-luotu-reimari-diaarinumero))))

(defn luo-uusi-sopimus! [db user sopimus]
  (let [nimi (::sopimus/nimi sopimus)
        reimari-diaarinro (::sopimus/reimari-diaarinro sopimus)
        alkupvm (::sopimus/alkupvm sopimus)
        loppupvm (::sopimus/loppupvm sopimus)
        paasopimus-id (::sopimus/paasopimus-id sopimus)
        harjassa-luotu-sopimus (q/luo-harjassa-luotu-sopimus<! db {:kayttaja (:id user)
                                                                   :nimi nimi
                                                                   :alkupvm alkupvm
                                                                   :loppupvm loppupvm
                                                                   :paasopimus paasopimus-id})
        harjassa-luotu-reimari-diaarinumero (q/luo-reimari-diaarinumero-linkki<! db {:harja-sopimus-id (:id harjassa-luotu-sopimus)
                                                                                     :reimari-diaarinro reimari-diaarinro})]
    (assoc harjassa-luotu-sopimus :reimari-diaarinro (:reimari-diaarinro harjassa-luotu-reimari-diaarinumero))))

(defn tallenna-sopimus [db user sopimus]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)

    (jdbc/with-db-transaction [db db]
      (let [sopimus (if (= "" (::sopimus/reimari-diaarinro sopimus))
                      (assoc sopimus ::sopimus/reimari-diaarinro nil)
                      sopimus)
            tallennettu-sopimus (if (id-olemassa? (::sopimus/id sopimus))
                                  (paivita-sopimusta! db user sopimus)
                                  (luo-uusi-sopimus! db user sopimus))]

        {::sopimus/id (:id tallennettu-sopimus)
         ::sopimus/nimi (:nimi tallennettu-sopimus)
         ::sopimus/reimari-diaarinro (:reimari-diaarinro tallennettu-sopimus)
         ::sopimus/alkupvm (:alkupvm tallennettu-sopimus)
         ::sopimus/loppupvm (:loppupvm tallennettu-sopimus)
         ::sopimus/paasopimus-id (:paasopimus-id tallennettu-sopimus)}))))

(defn tallenna-ketjutus [db user {:keys [tiedot kaytossa?] :as asd}]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)

    (let [sopimus_id (::sopimus/id tiedot)
          sampoid (::sopimus/sampoid tiedot)]

      (if kaytossa?
        (q/luo-tai-paivita-ketjutus! db {:sopimus_id sopimus_id
                                         :sampoid sampoid
                                         :ketjutus_kaytossa kaytossa?})
        (q/poista-sopimuksen-liikenne-ketjutus! db {:sopimus_id sopimus_id
                                                    :ketjutus_kaytossa kaytossa?}))
      (vesivayla-kanavien-hoito-sopimukset-vastaus db nil nil))))

(defrecord Sopimukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]

    (julkaise-palvelu http
      :hae-harjassa-luodut-sopimukset
      (fn [user _]
        (hae-harjassa-luodut-sopimukset db user))
      {:vastaus-spec ::sopimus/hae-harjassa-luodut-sopimukset-vastaus})

    (julkaise-palvelu http
      :hae-vesivayla-kanavien-hoito-sopimukset
      (fn [user tiedot]
        (hae-vesivayla-kanavien-hoito-sopimukset db user tiedot))
      {:vastaus-spec ::sopimus/hae-vesivayla-kanavien-hoito-sopimukset-vastaus})

    (julkaise-palvelu http
      :tallenna-sopimus
      (fn [user tiedot]
        (tallenna-sopimus db user tiedot))
      {:kysely-spec ::sopimus/tallenna-sopimus-kysely
       :vastaus-spec ::sopimus/tallenna-sopimus-vastaus})

    (julkaise-palvelu http
      :tallenna-ketjutus
      (fn [user tiedot]
        (tallenna-ketjutus db user tiedot)))

    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut
      http
      :hae-harjassa-luodut-sopimukset
      :hae-vesivayla-kanavien-hoito-sopimukset
      :tallenna-sopimus
      :tallenna-ketjutus)

    this))
