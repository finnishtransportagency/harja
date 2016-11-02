(ns harja.palvelin.palvelut.api-jarjestelmatunnukset
  (:require [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.api-jarjestelmatunnukset :as q]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]))

(defn hae-jarjestelmatunnuksen-lisaoikeudet [db user {:keys [kayttaja-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (into [] (q/hae-jarjestelmatunnuksen-lisaoikeudet db {:kayttaja kayttaja-id})))

(defn hae-jarjestelmatunnukset [db user]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (into []
        (comp (map konv/alaviiva->rakenne)
              (map #(konv/array->vec % :urakat)))
        (q/hae-jarjestelmatunnukset db)))

(defn tallenna-jarjestelmatunnukset [db user tunnukset]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-api-jarjestelmatunnukset user)
  (jdbc/with-db-transaction [c db]
    (doseq [{:keys [id kayttajanimi kuvaus organisaatio poistettu]} tunnukset]
      (if poistettu
        (q/poista-jarjestelmatunnus! c {:id id})
        (q/luo-jarjestelmatunnus<! c {:kayttajanimi kayttajanimi
                                      :kuvaus kuvaus
                                      :organisaatio (:id organisaatio)}))))
  (hae-jarjestelmatunnukset db user))

(defrecord APIJarjestelmatunnukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db :as this}]
    (julkaise-palvelu http :hae-jarjestelmatunnukset
                      (fn [user payload]
                        (hae-jarjestelmatunnukset db user)))
    (julkaise-palvelu http :hae-jarjestelmatunnuksen-lisaoikeudet
                      (fn [user payload]
                        (hae-jarjestelmatunnuksen-lisaoikeudet db user payload)))
    (julkaise-palvelu http :tallenna-jarjestelmatunnukset
                      (fn [user payload]
                        (tallenna-jarjestelmatunnukset db user payload)))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-jarjestelmatunnukset
                     :hae-jarjestelmatunnuksen-lisaoikeudet
                     :tallenna-jarjestelmatunnukset)
    this))
