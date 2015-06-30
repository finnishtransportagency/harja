(ns harja.palvelin.palvelut.liitteet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [cognitect.transit :as t]
            [clojure.java.io :as io]
            [clojure.java.jdbc :as jdbc]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]]
            [harja.kyselyt.liitteet :as q]
            [taoensso.timbre :as log]
            [harja.palvelin.oikeudet :as oik]
            [harja.palvelin.komponentit.liitteet :as liitteet])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)))



(defn tallenna-liite [liitteet req]
  (println "LIITEHÄN SIELTÄ TULI: " (:params req))

  (let [parametrit (:params req)
        liite (get parametrit "liite")
        urakka (Integer/parseInt (get parametrit "urakka"))]

    (oik/vaadi-lukuoikeus-urakkaan (:kayttaja req) urakka)
    (if liite
      (let [{:keys [filename content-type tempfile size]} liite
            uusi-liite (liitteet/luo-liite liitteet (:id (:kayttaja req)) urakka filename content-type size tempfile)]
        (log/debug "Tallennettu liite " filename " (" size " tavua)")
        (transit-vastaus (-> uusi-liite
                             (dissoc :liite_oid :pikkukuva :luoja :luotu))))
      {:status 400
       :body   "Ei liitettä"})))


(defn lataa-liite [liitteet req]
  (let [id (Integer/parseInt (get (:params req) "id"))
        {:keys [tyyppi koko urakka data]} (liitteet/lataa-liite liitteet id)]
    (oik/vaadi-lukuoikeus-urakkaan (:kayttaja req) urakka)
    {:status  200
     :headers {"Content-Type"   tyyppi
               "Content-Length" koko}
     :body    (ByteArrayInputStream. data)}))

(defn lataa-pikkukuva [liitteet req]
  (let [id (Integer/parseInt (get (:params req) "id"))
        {:keys [pikkukuva urakka]} (liitteet/lataa-pikkukuva liitteet id)]
    (oik/vaadi-lukuoikeus-urakkaan (:kayttaja req) urakka)
    (log/debug "Ladataan pikkukuva " id)
    (if pikkukuva
      {:status  200
       :headers {"Content-Type"   "image/png"
                 "Content-Length" (count pikkukuva)}
       :body    (ByteArrayInputStream. pikkukuva)}
      {:status 404
       :body   "Annetulle liittelle ei pikkukuvaa."})))


(defrecord Liitteet []
  component/Lifecycle
  (start [{:keys [http-palvelin] :as this}]
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
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin :tallenna-liite :lataa-liite :lataa-pikkukuva)))

