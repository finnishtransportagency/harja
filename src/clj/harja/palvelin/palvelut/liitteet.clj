(ns harja.palvelin.palvelut.liitteet
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut transit-vastaus]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.params :refer [wrap-params]]
            [harja.kyselyt.toteumat :as tot-q]
            [harja.kyselyt.liitteet :as liitteet-q]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.domain.liite :as liite-domain]
            [specql.core :as specql]
            [harja.palvelin.palvelut.laadunseuranta :as ls]
            [harja.palvelin.palvelut.turvallisuuspoikkeamat :as turpop]
            [harja.palvelin.palvelut.toteumat :as toteumap]
            [harja.domain.turvallisuuspoikkeama :as turpo]
            [harja.domain.laadunseuranta.laatupoikkeama :as lp]
            [harja.domain.laadunseuranta.tarkastus :as tarkastus]
            [harja.domain.toteuma :as toteuma]
            [harja.domain.erilliskustannus :as erilliskustannus]
            [harja.domain.kommentti :as kommentti]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.toteumat-tarkistukset :as tarkistukset]
            [harja.tyokalut.tietoturva :as tietoturva])
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

(def domain-tiedot
  {:turvallisuuspoikkeama {:linkkitaulu ::liite-domain/turvallisuuspoikkeama<->liite
                           :linkkitaulu-domain-id ::liite-domain/turvallisuuspoikkeama-id
                           :linkkitaulu-liite-id ::liite-domain/liite-id
                           :domain-taulu ::turpo/turvallisuuspoikkeama
                           :domain-taulu-id ::turpo/id
                           :domain-taulu-urakka-id ::turpo/urakka-id
                           :oikeustarkistus (fn [_ user urakka-id _]
                                              (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-turvallisuus user urakka-id))}
   :laatupoikkeama {:linkkitaulu ::liite-domain/laatupoikkeama<->liite
                    :linkkitaulu-domain-id ::liite-domain/laatupoikkeama-id
                    :linkkitaulu-liite-id ::liite-domain/liite-id
                    :domain-taulu ::lp/laatupoikkeama
                    :domain-taulu-id ::lp/id
                    :domain-taulu-urakka-id ::lp/urakka-id
                    :oikeustarkistus (fn [_ user urakka-id _]
                                       (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id))}
   :tarkastus {:linkkitaulu ::liite-domain/tarkastus<->liite
               :linkkitaulu-domain-id ::liite-domain/tarkastus-id
               :linkkitaulu-liite-id ::liite-domain/liite-id
               :domain-taulu ::tarkastus/tarkastus
               :domain-taulu-id ::tarkastus/id
               :domain-taulu-urakka-id ::tarkastus/urakka-id
               :oikeustarkistus (fn [_ user urakka-id _]
                                  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id))}
   :toteuma {:linkkitaulu ::liite-domain/toteuma<->liite
             :linkkitaulu-domain-id ::liite-domain/toteuma-id
             :linkkitaulu-liite-id ::liite-domain/liite-id
             :domain-taulu ::toteuma/toteuma
             :domain-taulu-id ::toteuma/id
             :domain-taulu-urakka-id ::toteuma/urakka-id
             :oikeustarkistus (fn [db user urakka-id domain-id]
                                (toteumap/toteumatyypin-oikeustarkistus db user urakka-id domain-id))}
   :bonukset {:linkkitaulu ::liite-domain/erilliskustannus<->liite
              :linkkitaulu-domain-id ::liite-domain/erilliskustannus-id
              :linkkitaulu-liite-id ::liite-domain/liite-id
              :domain-taulu ::erilliskustannus/erilliskustannus
              :domain-taulu-id ::erilliskustannus/id
              :domain-taulu-urakka-id ::erilliskustannus/urakka
              :oikeustarkistus (fn [_ user urakka-id _]
                                       (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-laatupoikkeamat user urakka-id))}})

(defn- poista-kommentin-liite-linkitys [db user {:keys [urakka-id domain liite-id domain-id]}]
  ;; Etsitään kommentti, joka kuuluu annettuun domain-asiaan ja jolla on liitteenä liite-id.
  ;; Sama liite-id voi esiintyä vain yhdellä kommentilla.
  (case domain
    :laatupoikkeama-kommentti-liite
    (do (ls/vaadi-laatupoikkeama-kuuluu-urakkaan db urakka-id domain-id)
        (liitteet-q/poista-laatupoikkeaman-kommentin-liite! db {:liite liite-id
                                                                :laatupoikkeama domain-id}))
    :turvallisuuspoikkeama-kommentti-liite
    (do (turpop/vaadi-turvallisuuspoikkeama-kuuluu-urakkaan db urakka-id domain-id)

        (liitteet-q/poista-turvallisuuspoikkeaman-kommentin-liite! db {:liite liite-id
                                                                       :turvallisuuspoikkeama domain-id}))))

(defn poista-liite-linkitys
  "Poistaa liitteen linkityksen tietystä domain-asiasta. Liitettä ei näy enää missään, mutta se jää kuitenkin meille talteen."
  [db user {:keys [urakka-id domain liite-id domain-id] :as tiedot}]
  (assert (and db user urakka-id domain liite-id domain-id)
          (str "Puutteelliset argumentit liitteen poistoon, sain: " db urakka-id tiedot))
  (let [domain-tiedot (case domain
                        :laatupoikkeama-kommentti-liite (:laatupoikkeama domain-tiedot)
                        :turvallisuuspoikkeama-kommentti-liite (:turvallisuuspoikkeama domain-tiedot)
                        (domain domain-tiedot))
        oikeustarkistus-fn (:oikeustarkistus domain-tiedot)]
    (oikeustarkistus-fn db user urakka-id domain-id)
    (case domain
      ;; Kommenttien poisto vaatii oman custom-käsittelyn
      :laatupoikkeama-kommentti-liite (poista-kommentin-liite-linkitys db user tiedot)
      :turvallisuuspoikkeama-kommentti-liite (poista-kommentin-liite-linkitys db user tiedot)
      ;; Muuten voidaan poistaa geneerisesti käyttäen linkkitaulua ja domainin omaa taulua
      (do (tietoturva/vaadi-linkitys db
                                     (:domain-taulu domain-tiedot)
                                     (:domain-taulu-id domain-tiedot)
                                     domain-id
                                     (:domain-taulu-urakka-id domain-tiedot)
                                     urakka-id)
          (specql/delete! db
                          (:linkkitaulu domain-tiedot)
                          {(:linkkitaulu-domain-id domain-tiedot) domain-id
                           (:linkkitaulu-liite-id domain-tiedot) liite-id})))))

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
                      (fn [user {:keys [domain liite-id domain-id urakka-id]}]
                        (poista-liite-linkitys db user {:urakka-id urakka-id
                                                        :domain domain
                                                        :liite-id liite-id
                                                        :domain-id domain-id})))
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
                     :tallenna-liite
                     :lataa-liite
                     :lataa-pikkukuva
                     :poista-liite-linkki)
    this))
