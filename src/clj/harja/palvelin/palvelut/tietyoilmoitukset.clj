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
            [specql.core :refer [fetch upsert!]]
            [clojure.spec :as s]
            [clj-time.coerce :as c]
            [harja.pvm :as pvm]
            [specql.op :as op]))

(defn aikavaliehto [vakioaikavali alkuaika loppuaika]
  (when (not (:ei-rajausta? vakioaikavali))
    (if-let [tunteja (:tunteja vakioaikavali)]
      [(c/to-date (pvm/tuntia-sitten tunteja)) (pvm/nyt)]
      [alkuaika loppuaika])))

(defn- urakat [db user oikeus]
  (kayttajatiedot/kayttajan-urakka-idt-aikavalilta
   db
   user
   (partial oikeus oikeudet/ilmoitukset-ilmoitukset)))

(defn hae-tietyoilmoitukset [db user {:keys [luotu-alkuaika
                                             luotu-loppuaika
                                             luotu-vakioaikavali
                                             kaynnissa-alkuaika
                                             kaynnissa-loppuaika
                                             kaynnissa-vakioaikavali
                                             sijainti
                                             urakka
                                             vain-kayttajan-luomat]
                                      :as hakuehdot}
                             max-maara]
  (let [kayttajan-urakat (urakat db user oikeudet/voi-lukea?)
        alkuehto (fn [aikavali] (when (first aikavali) (konv/sql-timestamp (first aikavali))))
        loppuehto (fn [aikavali] (when (second aikavali) (konv/sql-timestamp (second aikavali))))
        luotu-aikavali (aikavaliehto luotu-vakioaikavali luotu-alkuaika luotu-loppuaika)
        luotu-alku (alkuehto luotu-aikavali)
        luotu-loppu (loppuehto luotu-aikavali)
        kaynnissa-aikavali (aikavaliehto kaynnissa-vakioaikavali kaynnissa-alkuaika kaynnissa-loppuaika)
        kaynnissa-alku (alkuehto kaynnissa-aikavali)
        kaynnissa-loppu (loppuehto kaynnissa-aikavali)
        kyselyparametrit {:luotu-alku luotu-alku
                          :luotu-loppu luotu-loppu
                          :kaynnissa-alku kaynnissa-alku
                          :kaynnissa-loppu kaynnissa-loppu
                          :urakat (if (and urakka (not (str/blank? urakka)))
                                    [(Integer/parseInt urakka)]
                                    kayttajan-urakat)
                          :luojaid (when vain-kayttajan-luomat (:id user))
                          :sijainti (when sijainti (geo/geometry (geo/clj->pg sijainti)))
                          :maxmaara max-maara
                          :organisaatio (:id (:organisaatio user))}
        tietyoilmoitukset (q-tietyoilmoitukset/hae-ilmoitukset db kyselyparametrit)]
    tietyoilmoitukset))

(defn tallenna-tietyoilmoitus [db user ilmoitus]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/ilmoitukset-ilmoitukset user (::t/urakka-id ilmoitus))
  (upsert! db ::t/ilmoitus
           ilmoitus
           {::t/urakka-id (op/in (urakat db user oikeudet/voi-kirjoittaa?))}))


(defn tietyoilmoitus-pdf [db user params]
  (pdf/tietyoilmoitus-pdf
    (first (fetch db ::t/ilmoitus+pituus
                  q-tietyoilmoitukset/ilmoitus-pdf-kentat
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
    (julkaise-palvelu http :tallenna-tietyoilmoitus
                      (fn [user ilmoitus]
                        (tallenna-tietyoilmoitus db user ilmoitus))
                      {:kysely-spec ::t/ilmoitus
                       :vastaus-spec ::t/ilmoitus})
    (when pdf
      (pdf-vienti/rekisteroi-pdf-kasittelija!
        pdf :tietyoilmoitus (partial #'tietyoilmoitus-pdf db)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-tietyoilmoitukset
                     :tallenna-tietyoilmoitus)
    (when (:pdf-vienti this)
      (pdf-vienti/poista-pdf-kasittelija! (:pdf-vienti this) :tietyoilmoitus))
    this))
