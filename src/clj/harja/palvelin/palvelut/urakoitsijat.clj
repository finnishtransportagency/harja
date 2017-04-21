(ns harja.palvelin.palvelut.urakoitsijat
  "Palvelut urakoitsijatietojen hakemiseksi.
  Urakoitsijan perustietojen haut eivät tee oikeustarkistuksia, koska
  urakoitsijat ovat julkista tietoa."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [taoensso.timbre :as log]
            [harja.domain.organisaatio :as o]
            [harja.tyokalut.spec-apurit :refer [namespacefy]]
            [harja.kyselyt.urakoitsijat :as q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.id :refer [id-olemassa?]]
            [harja.kyselyt.konversio :as konv]))

(declare hae-urakoitsijat urakkatyypin-urakoitsijat yllapidon-urakoitsijat vesivayla-urakoitsijat
         tallenna-urakoitsija!)


(defrecord Urakoitsijat []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-urakoitsijat (fn [user _]
                                          (hae-urakoitsijat (:db this) user)))
    (julkaise-palvelu (:http-palvelin this) :urakkatyypin-urakoitsijat
                      (fn [user urakkatyyppi]
                        (urakkatyypin-urakoitsijat (:db this) user urakkatyyppi)))
    (julkaise-palvelu (:http-palvelin this) :yllapidon-urakoitsijat
                      (fn [user]
                        (yllapidon-urakoitsijat (:db this) user)))
    (julkaise-palvelu (:http-palvelin this) :vesivayla-urakoitsijat
                      (fn [user _]
                        (vesivayla-urakoitsijat (:db this) user))
                      {:vastaus-spec ::o/vesivayla-urakoitsijat-vastaus})
    (julkaise-palvelu (:http-palvelin this) :tallenna-urakoitsija
                      (fn [user tiedot]
                        (tallenna-urakoitsija! (:db this) user tiedot))
                      {:kysely-spec ::o/tallenna-urakoitsija-kysely
                       :vastaus-spec ::o/tallenna-urakoitsija-vastaus})
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-urakoitsijat)
    (poista-palvelu (:http-palvelin this) :urakkatyypin-urakoitsijat)
    (poista-palvelu (:http-palvelin this) :vesivayla-urakoitsijat)
    (poista-palvelu (:http-palvelin this) :tallenna-urakoitsija)
    this))


(defn hae-urakoitsijat
  "Palvelu, joka palauttaa kaikki urakoitsijat urakkatyypistä riippumatta."
  [db user]
  (oikeudet/ei-oikeustarkistusta!)
  (-> (q/listaa-urakoitsijat db)
      vec))


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

(defn vesivayla-urakoitsijat [db user]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-vesivaylat user)
    (let [urakoitsijat (konv/sarakkeet-vektoriin
                         (into []
                               (map #(konv/alaviiva->rakenne %))
                               (q/hae-vesivayla-urakoitsijat db))
                         {:urakka :urakat})]
      (namespacefy urakoitsijat {:ns :harja.domain.organisaatio
                                 :inner {:urakat {:ns :harja.domain.urakka}}}))))

(defn tallenna-urakoitsija! [db user urakoitsija]
  (when (ominaisuus-kaytossa? :vesivayla)
    (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-vesivaylat user)
    (let [id (::o/id urakoitsija)
          nimi (::o/nimi urakoitsija)
          postinumero (::o/postinumero urakoitsija)
          katuosoite (::o/katuosoite urakoitsija)
          ytunnus (::o/ytunnus urakoitsija)]
      (let [tallennettu-urakoitsija (if (id-olemassa? id)
                                      (q/paivita-urakoitsija! db
                                                              {:id id
                                                               :nimi nimi
                                                               :ytunnus ytunnus
                                                               :katuosoite katuosoite
                                                               :postinumero postinumero
                                                               :kayttaja (:id user)})
                                      (q/luo-urakoitsija<! db
                                                           {:nimi nimi
                                                            :ytunnus ytunnus
                                                            :katuosoite katuosoite
                                                            :postinumero postinumero
                                                            :kayttaja (:id user)}))]
        {::o/id (:id tallennettu-urakoitsija)
         ::o/nimi (:nimi tallennettu-urakoitsija)
         ::o/postinumero (:postinumero tallennettu-urakoitsija)
         ::o/katuosoite (:katuosoite tallennettu-urakoitsija)
         ::o/ytunnus (:ytunnus tallennettu-urakoitsija)}))))
