(ns harja.palvelin.palvelut.raportit
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.raportointi :refer [hae-raportit suorita-raportti]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.raportit :as q]
            [harja.kyselyt.konversio :as konv]))

(defn rooliksi [rooli]
  (case rooli
    :ely-kayttaja "ELY_Kayttaja"
    :ely-paakayttaja "ELY_Paakayttaja"
    :jvh "Jarjestelmavastaava"

    nil))

(defn urakkarooliksi [rooli]
  (case rooli
    :urakanvalvoja "ELY_Urakanvalvoja"
    :rakennuttajakonsultti "Rakennuttajakonsultti"
    :urak-vastuuhenkilo "vastuuhenkilo"

    nil) )

(defn organisaatiorooliksi [rooli]
  (case rooli
    :urak-paakayttaja "Paakayttaja"

    nil))


(defn hae-raporttien-suoritustiedot 
  [db user {:keys [alkupvm loppupvm raportti rooli formaatti] :as parametrit}]
  ;; käytetään hallintapaneelissa olevan indeksisivun oikeuksia, käytännössä siis
  ;; Harjan pääkäyttäjät vain pääsevät tähän tietoon toistaiseksi
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-indeksit user)
  (let [yleinen-rooli (rooliksi rooli)
        urakkarooli (urakkarooliksi rooli)
        organisaatiorooli (organisaatiorooliksi rooli)
        tiedot (into []
                 (comp
                   (map #(konv/jsonb->clojuremap % :parametrit)))
                     (q/hae-raporttien-suoritustiedot db {:alkupvm alkupvm
                                                          :loppupvm loppupvm
                                                          :raportti raportti
                                                          :rooli yleinen-rooli
                                                          :urakkarooli urakkarooli
                                                          :organisaatiorooli organisaatiorooli
                                                          :formaatti (when formaatti
                                                                       (name formaatti))}))]
    tiedot))

(defrecord Raportit []
  component/Lifecycle
  (start [{raportointi :raportointi
           http        :http-palvelin
           db          :db
           pdf-vienti  :pdf-vienti
           :as         this}]

    (julkaise-palvelu http
                      :hae-raportit
                      (fn [user]
                        (oikeudet/ei-oikeustarkistusta!)
                        (reduce-kv (fn [acc nimi raportti]
                                     ;; Otetaan suoritus fn ja koodi pois frontille lähetettävästä
                                     (assoc acc nimi (dissoc raportti :suorita :koodi)))
                                   {}
                                   (hae-raportit raportointi))))

    (julkaise-palvelu http :suorita-raportti
                      (fn [user raportti]
                        (suorita-raportti raportointi user raportti))
                      {:trace false})
    
    (julkaise-palvelu http :hae-raporttien-suoritustiedot 
                      (fn [user parametrit]
                        (hae-raporttien-suoritustiedot db user parametrit)))

    this)

  (stop [{http :http-palvelin pdf-vienti :pdf-vienti :as this}]
    (poista-palvelut http
                     :hae-raportit
                     :hae-raporttien-suoritustiedot
                     :suorita-raportti)
    this))
