(ns harja.palvelin.palvelut.liitteet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]]
            [harja.kyselyt.toteumat :as tot-q]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.toteumat-tarkistukset :as tarkistukset])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)))

(defn tallenna-liite [liitteet req]
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
       :body   "Ei liitettä"})))


(defn lataa-liite [liitteet req]
  (let [id (Integer/parseInt (get (:params req) "id"))
        {:keys [tyyppi koko urakka data]} (liitteet/lataa-liite liitteet id)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-liitteet (:kayttaja req) urakka)
    {:status  200
     :headers {"Content-Type"   tyyppi
               "Content-Length" koko}
     :body    (ByteArrayInputStream. data)}))

(defn lataa-pikkukuva [liitteet req]
  (let [id (Integer/parseInt (get (:params req) "id"))
        {:keys [pikkukuva urakka]} (liitteet/lataa-pikkukuva liitteet id)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-liitteet (:kayttaja req) urakka)
    (log/debug "Ladataan pikkukuva " id)
    (if pikkukuva
      {:status  200
       :headers {"Content-Type"   "image/png"
                 "Content-Length" (count pikkukuva)}
       :body    (ByteArrayInputStream. pikkukuva)}
      {:status 404
       :body   "Annetulle liittelle ei pikkukuvaa."})))

(defn- hae-toteuman-liitteet [db user {:keys [urakka-id toteuma-id oikeus]}]
  (let [nst-joista-saa-kutsua-toteuman-liitteden-hakua #{'urakat-toteumat-varusteet}] ;; voit lisätä oikeuksia tarpeen mukaan
    (when (nst-joista-saa-kutsua-toteuman-liitteden-hakua oikeus)
      (tarkistukset/vaadi-toteuma-kuuluu-urakkaan db toteuma-id urakka-id)
      (oikeudet/vaadi-lukuoikeus (deref (ns-resolve 'harja.domain.oikeudet oikeus)) user urakka-id)
      (tot-q/hae-toteuman-liitteet db toteuma-id))))

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
    (julkaise-palvelu http-palvelin :hae-toteuman-liitteet
                      (fn [user {:keys [urakka-id toteuma-id oikeus]}]
                        (hae-toteuman-liitteet db user {:urakka-id urakka-id
                                                        :toteuma-id toteuma-id
                                                        :oikeus oikeus})))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin :tallenna-liite :lataa-liite :lataa-pikkukuva :hae-toteuman-liitteet)
    this))
