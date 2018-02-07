(ns harja.palvelin.palvelut.liitteet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]]
            [harja.kyselyt.toteumat :as tot-q]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.domain.liite :as liite-domain]
            [specql.core :as specql]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.toteumat-tarkistukset :as tarkistukset])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)))

(defn tallenna-liite
  "Tallentaa liitteen kantaan, mutta ei linkitä sitä mihinkään domain-asiaan."
  [liitteet req]
  (let [parametrit (:params req)
        liite (get parametrit "liite")
        urakka (Integer/parseInt (get parametrit "urakka"))]

    (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-liitteet (:kayttaja req) urakka)
    (if liite
      (let [{:keys [filename content-type tempfile size kuvaus]} liite
            uusi-liite (liitteet/luo-liite liitteet (:id (:kayttaja req)) urakka filename content-type size tempfile kuvaus "harja-ui")]
        (log/debug "Tallennettu liite " filename " (" size " tavua)")
        (transit-vastaus (-> uusi-liite
                             (dissoc :liite_oid :pikkukuva :luoja :luotu))))
      {:status 400
       :body "Ei liitettä"})))


(defn lataa-liite [liitteet req]
  (let [id (Integer/parseInt (get (:params req) "id"))
        {:keys [tyyppi koko urakka data]} (liitteet/lataa-liite liitteet id)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-liitteet (:kayttaja req) urakka)
    {:status 200
     :headers {"Content-Type" tyyppi
               "Content-Length" koko}
     :body (ByteArrayInputStream. data)}))

(defn lataa-pikkukuva [liitteet req]
  (let [id (Integer/parseInt (get (:params req) "id"))
        {:keys [pikkukuva urakka]} (liitteet/lataa-pikkukuva liitteet id)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-liitteet (:kayttaja req) urakka)
    (log/debug "Ladataan pikkukuva " id)
    (if pikkukuva
      {:status 200
       :headers {"Content-Type" "image/png"
                 "Content-Length" (count pikkukuva)}
       :body (ByteArrayInputStream. pikkukuva)}
      {:status 404
       :body "Annetulle liittelle ei pikkukuvaa."})))

(def liitteen-poisto-domainin-mukaan
  {:turvallisuuspoikkeama {:taulu ::liite-domain/turvallisuuspoikkeama<->liite
                           :domain-sarake ::liite-domain/turvallisuuspoikkeama-id
                           :liite-sarake ::liite-domain/liite-id}})

(defn poista-liite-linkitys
  "Poistaa liitteen linkityksen tietystä domain-asiasta. Liitettä ei näy enää missään, mutta se jää kuitenkin meille talteen."
  [db user {:keys [domain liite-id domain-id]}]
  (let [domain-tiedot (domain liitteen-poisto-domainin-mukaan)]
    ;; TODO Tarkistettava domain-kohtainen kirjoitusoikeus (sama, jolla lomakkeen tms. saa tallentaa)
    (specql/delete! db
                    (:taulu domain-tiedot)
                    {(:domain-sarake domain-tiedot) domain-id
                     (:liite-sarake domain-tiedot) liite-id})))

(defrecord Liitteet []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :tallenna-liite
                      (wrap-multipart-params (fn [req] (tallenna-liite (:liitteiden-hallinta this) req)))
                      {:ring-kasittelija? true})
    (julkaise-palvelu http-palvelin :lataa-liite
                      (wrap-params (fn [req]
                                     (lataa-liite (:liitteiden-hallinta this) req)))
                      {:ring-kasittelija? true})
    (julkaise-palvelu http-palvelin :lataa-pikkukuva
                      (wrap-params (fn [req]
                                     (lataa-pikkukuva (:liitteiden-hallinta this) req)))
                      {:ring-kasittelija? true})
    (julkaise-palvelu http-palvelin :poista-liite-linkki
                      (fn [user {:keys [domain liite-id domain-id]}]
                        (poista-liite-linkitys db user {:domain domain
                                                        :liite-id liite-id
                                                        :domain-id domain-id})))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin :tallenna-liite :lataa-liite :lataa-pikkukuva :poista-liite-linkki)
    this))
