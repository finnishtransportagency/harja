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
            [specql.core :refer [fetch]]))

(defn- muunna-tietyoilmoitus [tietyoilmoitus]
  ;; FIXME: korvaa tämä specql datalla...
  (as-> tietyoilmoitus t
        (update t :sijainti geo/pg->clj)
        (konv/array->vec t :tyotyypit)
        (assoc t :tyotyypit (mapv #(konv/pgobject->map % :tyyppi :string :selite :string) (:tyotyypit t)))
        (konv/array->vec t :tienpinnat)
        (assoc t :tienpinnat (mapv #(konv/pgobject->map % :materiaali :string :matka :long) (:tienpinnat t)))
        (konv/array->vec t :kiertotienpinnat)
        (assoc t :kiertotienpinnat (mapv #(konv/pgobject->map % :materiaali :string :matka :long) (:kiertotienpinnat t)))
        (konv/array->vec t :nopeusrajoitukset)
        (assoc t :nopeusrajoitukset (mapv #(konv/pgobject->map % :nopeusrajoitus :long :matka :long) (:nopeusrajoitukset t)))
        (konv/array->vec t :tyoajat)
        (update t :tyoajat (fn [tyoajat]
                             (mapv #(-> %
                                        (konv/pgobject->map :alku :date :loppu :date :viikonpaivat :string)
                                        (update :viikonpaivat
                                                (fn [viikonpaivat-str]
                                                  (into []
                                                        (str/split (subs viikonpaivat-str
                                                                         1 (dec (count viikonpaivat-str)))
                                                                   #",")))))

                                   tyoajat)))
        ))

(defn hae-tietyoilmoitukset [db
                             user
                             {:keys [alkuaika
                                     loppuaika
                                     urakka
                                     sijainti
                                     vain-kayttajan-luomat]
                              :as hakuehdot}
                             max-maara]
  ;; todo: hae myös käyttäjän organisaation tekemät ilmoitukset
  (let [kayttajan-urakat (kayttajatiedot/kayttajan-urakka-idt-aikavalilta
                           db
                           user
                           (fn [urakka-id kayttaja]
                             (oikeudet/voi-lukea? oikeudet/ilmoitukset-ilmoitukset urakka-id kayttaja))
                           nil nil nil nil #inst "1900-01-01" #inst "2100-01-01")
        sql-parametrit {:alku (konv/sql-timestamp alkuaika)
                        :loppu (konv/sql-timestamp loppuaika)
                        :urakat (if (and urakka (not (str/blank? urakka))) [(Integer/parseInt urakka)] kayttajan-urakat)
                        :luojaid (when vain-kayttajan-luomat (:id user))
                        :sijainti (when sijainti (geo/geometry (geo/clj->pg sijainti)))
                        :maxmaara max-maara}
        tietyoilmoitukset (map (fn [tietyoilmoitus]
                                 (let [tietyoilmoitus (muunna-tietyoilmoitus tietyoilmoitus)
                                       vaiheet (q-tietyoilmoitukset/hae-tietyoilmoituksen-vaiheet
                                                 db
                                                 {:paatietyoilmoitus (:id tietyoilmoitus)})
                                       vaiheet (mapv (fn [vaihe] (muunna-tietyoilmoitus vaihe)) vaiheet)]
                                   (assoc tietyoilmoitus :vaiheet vaiheet)))
                               (q-tietyoilmoitukset/hae-tietyoilmoitukset db sql-parametrit))]
    tietyoilmoitukset))

(defn tietyoilmoitus-pdf [db user params]
  (println "MUODOSTA PDF: " params)
  (pdf/tietyoilmoitus-pdf
   (first (fetch db ::t/ilmoitus q-tietyoilmoitukset/kaikki-ilmoituksen-kentat
                 {::t/id (:id params)}))))

(defrecord Tietyoilmoitukset []
  component/Lifecycle
  (start [{db :db
           tloik :tloik
           http :http-palvelin
           pdf :pdf-vienti
           :as this}]
    (julkaise-palvelu http :hae-tietyoilmoitukset
                      (fn [user tiedot]
                        (hae-tietyoilmoitukset db user tiedot 501)))
    (pdf-vienti/rekisteroi-pdf-kasittelija!
     pdf :tietyoilmoitus (partial #'tietyoilmoitus-pdf db))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-tietyoilmoitukset)
    (pdf-vienti/poista-pdf-kasittelija! (:pdf-vienti this) :tietyoilmoitus)
    this))
