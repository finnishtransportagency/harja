(ns harja.palvelin.palvelut.api-jarjestelmatunnukset
  (:require [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.api-jarjestelmatunnukset :as q]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [taoensso.timbre :as log]
            [harja.id :refer [id-olemassa?]]
            [clojure.java.jdbc :as jdbc]))

(defn hae-jarjestelmatunnuksen-lisaoikeudet [db user {:keys [kayttaja-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (into [] (q/hae-jarjestelmatunnuksen-lisaoikeudet db {:kayttaja kayttaja-id})))

(defn hae-urakat-lisaoikeusvalintaan [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (into [] (q/hae-urakat-lisaoikeusvalintaan db)))

(defn hae-jarjestelmatunnukset [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (into []
    (comp (map konv/alaviiva->rakenne)
      (map #(konv/array->vec % :urakat))
      (map #(konv/array->vec % :oikeudet)))
    (q/hae-jarjestelmatunnukset db)))

(defn hae-mahdolliset-api-oikeudet [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (into [] (q/hae-mahdolliset-api-oikeudet db)))

(defn lisaa-kayttajalle-oikeus [db user payload]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (q/lisaa-kayttajalle-oikeus! db {:oikeus (:oikeus payload)
                                   :kayttajanimi (:kayttajanimi payload)}))

(defn poista-kayttajalta-oikeus [db user payload]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (q/poista-kayttajalta-oikeus! db {:oikeus (:oikeus payload)
                                    :kayttajanimi (:kayttajanimi payload)}))

(defn tallenna-jarjestelmatunnukset [db user tunnukset]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (jdbc/with-db-transaction [c db]
    (doseq [{:keys [id kayttajanimi kuvaus organisaatio poistettu]} tunnukset]
      (if poistettu
        (q/poista-jarjestelmatunnus! c {:id id})
        (if-not (id-olemassa? id)
          (q/luo-jarjestelmatunnus<! c {:kayttajanimi kayttajanimi
                                        :kuvaus kuvaus
                                        :organisaatio (:id organisaatio)})
          (q/paivita-jarjestelmatunnus! c {:kayttajanimi kayttajanimi
                                           :kuvaus kuvaus
                                           :organisaatio (:id organisaatio)
                                           :id id})))))
  (hae-jarjestelmatunnukset db user))

(defn tallenna-jarjestelmatunnuksen-lisaoikeudet [db user {:keys [oikeudet kayttaja-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (jdbc/with-db-transaction [c db]
    (doseq [{:keys [id urakka-id poistettu]} oikeudet]
      (if poistettu
        (q/poista-jarjestelmatunnuksen-lisaoikeus-urakkaan! c {:id id})
        (if-not (id-olemassa? id)
          (q/luo-jarjestelmatunnukselle-lisaoikeus-urakkaan<! c {:kayttaja kayttaja-id
                                                                 :urakka urakka-id})
          (q/paivita-jarjestelmatunnuksen-lisaoikeus-urakkaan! c {:urakka urakka-id
                                                                  :id id})))))
  (hae-jarjestelmatunnuksen-lisaoikeudet db user {:kayttaja-id kayttaja-id}))

(defrecord APIJarjestelmatunnukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    
    (julkaise-palvelu http :hae-jarjestelmatunnukset
      (fn [user _] (hae-jarjestelmatunnukset db user)))
    
    (julkaise-palvelu http :lisaa-kayttajalle-oikeus
      (fn [user payload] (lisaa-kayttajalle-oikeus db user payload)))
    
    (julkaise-palvelu http :poista-kayttajalta-oikeus
      (fn [user payload] (poista-kayttajalta-oikeus db user payload)))
    
    (julkaise-palvelu http :hae-mahdolliset-api-oikeudet
      (fn [user _] (hae-mahdolliset-api-oikeudet db user)))
    
    (julkaise-palvelu http :hae-jarjestelmatunnuksen-lisaoikeudet
      (fn [user payload] (hae-jarjestelmatunnuksen-lisaoikeudet db user payload)))
    
    (julkaise-palvelu http :hae-urakat-lisaoikeusvalintaan
      (fn [user _] (hae-urakat-lisaoikeusvalintaan db user)))
    
    (julkaise-palvelu http :tallenna-jarjestelmatunnukset
      (fn [user payload] (tallenna-jarjestelmatunnukset db user payload)))
    
    (julkaise-palvelu http :tallenna-jarjestelmatunnuksen-lisaoikeudet
      (fn [user payload] (tallenna-jarjestelmatunnuksen-lisaoikeudet db user payload)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :hae-mahdolliset-api-oikeudet
      :lisaa-kayttajalle-oikeus
      :poista-kayttajalta-oikeus
      :hae-jarjestelmatunnukset
      :hae-jarjestelmatunnuksen-lisaoikeudet
      :hae-urakat-lisaoikeusvalintaan
      :tallenna-jarjestelmatunnukset
      :tallenna-jarjestelmatunnuksen-lisaoikeudet)
    this))
