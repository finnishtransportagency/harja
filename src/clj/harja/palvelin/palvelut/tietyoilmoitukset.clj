(ns harja.palvelin.palvelut.tietyoilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut async]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clj-time.coerce :refer [from-sql-time]]
            [harja.kyselyt.tietyoilmoitukset :as q-tietyoilmoitukset]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
            [harja.geo :as geo]
            [clojure.string :as str]
            [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.palvelin.palvelut.tietyoilmoitukset.pdf :as pdf]
            [harja.domain.tietyoilmoitukset :as t]
            [specql.core :refer [fetch]]
            [clojure.spec :as s]))

(defn hae-tietyoilmoitukset [db user {:keys [alkuaika
                                             loppuaika
                                             urakka
                                             sijainti
                                             vain-kayttajan-luomat]
                                      :as hakuehdot}
                             max-maara]
  (let [kayttajan-urakat (kayttajatiedot/kayttajan-urakka-idt-aikavalilta
                           db
                           user
                           (fn [urakka-id kayttaja]
                             (oikeudet/voi-lukea? oikeudet/ilmoitukset-ilmoitukset urakka-id kayttaja))
                           nil nil nil nil #inst "1900-01-01" #inst "2100-01-01")
        kyselyparametrit {:alku (konv/sql-timestamp alkuaika)
                          :loppu (konv/sql-timestamp loppuaika)
                          :urakat (if (and urakka (not (str/blank? urakka)))
                                    [(Integer/parseInt urakka)]
                                    kayttajan-urakat)
                          :luojaid (when vain-kayttajan-luomat (:id user))
                          :sijainti (when sijainti (geo/geometry (geo/clj->pg sijainti)))
                          :maxmaara max-maara
                          :organisaatio (:id (:organisaatio user))}
        tietyoilmoitukset (q-tietyoilmoitukset/hae-ilmoitukset db kyselyparametrit)]
    (println "--->" tietyoilmoitukset)
    tietyoilmoitukset))

(defn tietyoilmoitus-pdf [db user params]
  (println "MUODOSTA PDF: " params)
  (pdf/tietyoilmoitus-pdf
    (first (fetch db ::t/ilmoitus q-tietyoilmoitukset/kaikki-ilmoituksen-kentat
                  {::t/id (:id params)}))))

(s/def ::tietyoilmoitukset (s/coll-of ::t/ilmoitus))

(defrecord Tietyoilmoitukset []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin
           pdf :pdf-vienti
           :as this}]
    (julkaise-palvelu http :hae-tietyoilmoitukset
                      (fn [user tiedot]
                        (hae-tietyoilmoitukset db user tiedot 501))
                      {:vastaus-spec ::tietyoilmoitukset})
    (when pdf
      (pdf-vienti/rekisteroi-pdf-kasittelija!
        pdf :tietyoilmoitus (partial #'tietyoilmoitus-pdf db)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-tietyoilmoitukset)
    (when (:pdf-vienti this)
      (pdf-vienti/poista-pdf-kasittelija! (:pdf-vienti this) :tietyoilmoitus))
    this))
