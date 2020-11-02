(ns harja.palvelin.palvelut.urakoitsijat
  "Palvelut urakoitsijatietojen hakemiseksi.
  Urakoitsijan perustietojen haut eivät tee oikeustarkistuksia, koska
  urakoitsijat ovat julkista tietoa."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [taoensso.timbre :as log]
            [harja.domain.organisaatio :as o]
            [namespacefy.core :refer [namespacefy]]
            [harja.kyselyt.urakoitsijat :as q]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt.konversio :as konv]))

(declare hae-urakoitsijat urakkatyypin-urakoitsijat yllapidon-urakoitsijat vesivaylaurakoitsijat
         tallenna-urakoitsija! hae-urakoitsijat-urakkatietoineen)


(defrecord Urakoitsijat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakoitsijat
                      (fn [user _]
                        (hae-urakoitsijat (:db this) user)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakoitsijat-urakkatietoineen
                      (fn [user _]
                        (hae-urakoitsijat-urakkatietoineen (:db this) user)))
    (julkaise-palvelu (:http-palvelin this) :urakkatyypin-urakoitsijat
                      (fn [user urakkatyyppi]
                        (urakkatyypin-urakoitsijat (:db this) user urakkatyyppi)))
    (julkaise-palvelu (:http-palvelin this) :yllapidon-urakoitsijat
                      (fn [user]
                        (yllapidon-urakoitsijat (:db this) user)))
    (julkaise-palvelu (:http-palvelin this) :vesivaylaurakoitsijat
                      (fn [user _]
                        (vesivaylaurakoitsijat (:db this) user))
                      {:vastaus-spec ::o/vesivaylaurakoitsijat-vastaus})
    (julkaise-palvelu (:http-palvelin this) :tallenna-urakoitsija
                      (fn [user tiedot]
                        (tallenna-urakoitsija! (:db this) user tiedot))
                      {:kysely-spec ::o/tallenna-urakoitsija-kysely
                       :vastaus-spec ::o/tallenna-urakoitsija-vastaus})
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-urakoitsijat)
    (poista-palvelu (:http-palvelin this) :hae-urakoitsijat-urakkatietoineen)
    (poista-palvelu (:http-palvelin this) :urakkatyypin-urakoitsijat)
    (poista-palvelu (:http-palvelin this) :yllapidon-urakoitsijat)
    (poista-palvelu (:http-palvelin this) :vesivaylaurakoitsijat)
    (poista-palvelu (:http-palvelin this) :tallenna-urakoitsija)
    this))


(defn hae-urakoitsijat
  [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (vec (q/listaa-urakoitsijat db)))

(defn hae-urakoitsijat-urakkatietoineen [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (let [urakoitsijat (konv/sarakkeet-vektoriin
                       (into []
                             (map #(konv/alaviiva->rakenne %))
                             (q/hae-urakoitsijat-urakkatietoineen db))
                       {:urakka :urakat})]
    (namespacefy urakoitsijat {:ns :harja.domain.organisaatio
                               :inner {:urakat {:ns :harja.domain.urakka}}})))


(defn urakkatyypin-urakoitsijat [db user urakkatyyppi]
  (log/debug "Haetaan urakkatyypin " urakkatyyppi " urakoitsijat")
  (oikeudet/ei-oikeustarkistusta!)
  (->> (q/hae-urakkatyypin-urakoitsijat db (name urakkatyyppi))
       (map :id)
       (into #{})))

(defn yllapidon-urakoitsijat [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (log/debug "Haetaan ylläpidon urakoitsijat")
  (->> (q/hae-yllapidon-urakoitsijat db)
       (map :id)
       (into #{})))

(defn vesivaylaurakoitsijat [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (let [urakoitsijat (konv/sarakkeet-vektoriin
                         (into []
                               (map #(konv/alaviiva->rakenne %))
                               (q/hae-vesivaylaurakoitsijat db))
                         {:urakka :urakat})]
      (namespacefy urakoitsijat {:ns :harja.domain.organisaatio
                                 :inner {:urakat {:ns :harja.domain.urakka}}}))))

(defn tallenna-urakoitsija! [db user urakoitsija]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
    (let [id (::o/id urakoitsija)
          nimi (::o/nimi urakoitsija)
          postinumero (::o/postinumero urakoitsija)
          postitoimipaikka (::o/postitoimipaikka urakoitsija)
          katuosoite (::o/katuosoite urakoitsija)
          ytunnus (::o/ytunnus urakoitsija)]
      (let [tallennettu-urakoitsija (if (id-olemassa? id)
                                      (q/paivita-urakoitsija<! db
                                                               {:id id
                                                                :nimi nimi
                                                                :ytunnus ytunnus
                                                                :katuosoite katuosoite
                                                                :postinumero postinumero
                                                                :postitoimipaikka postitoimipaikka
                                                                :kayttaja (:id user)})
                                      (q/luo-urakoitsija<! db
                                                           {:nimi nimi
                                                            :ytunnus ytunnus
                                                            :katuosoite katuosoite
                                                            :postinumero postinumero
                                                            :postitoimipaikka postitoimipaikka
                                                            :kayttaja (:id user)}))]
        {::o/id (:id tallennettu-urakoitsija)
         ::o/nimi (:nimi tallennettu-urakoitsija)
         ::o/postinumero (:postinumero tallennettu-urakoitsija)
         ::o/katuosoite (:katuosoite tallennettu-urakoitsija)
         ::o/postitoimipaikka (:postitoimipaikka tallennettu-urakoitsija)
         ::o/ytunnus (:ytunnus tallennettu-urakoitsija)}))))

(defn vaadi-urakoitsija-kuuluu-urakkaan [db vaitetty-urakoitsija-id vaitetty-urakka-id]
  (log/debug "Tarkikistetaan, että urakoitsija " vaitetty-urakoitsija-id " kuuluu väitettyyn urakkaan " vaitetty-urakka-id)
  (assert vaitetty-urakka-id "Urakka id puuttuu!")
  (when vaitetty-urakoitsija-id
    (let [urakan-todellinen-urakoitsija (:urakoitsija (first (urakat-q/hae-urakan-urakoitsija db vaitetty-urakka-id)))]
      (when (and (some? urakan-todellinen-urakoitsija)
                 (not= urakan-todellinen-urakoitsija vaitetty-urakoitsija-id))
        (throw (SecurityException. (str "Urakan " vaitetty-urakka-id " urakoitsija ei ole " vaitetty-urakoitsija-id
                                        " vaan " urakan-todellinen-urakoitsija)))))))
