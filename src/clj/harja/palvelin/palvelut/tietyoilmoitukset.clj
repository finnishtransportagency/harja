(ns harja.palvelin.palvelut.tietyoilmoitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin
             :refer [julkaise-palvelu poista-palvelut async]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [clj-time.coerce :refer [from-sql-time]]
            [harja.kyselyt.tietyoilmoitukset :as q-tietyoilmoitukset]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
            [harja.geo :as geo]))

(defn hae-tietyoilmoitukset [db user {:keys [alku loppu] :as hakuehdot} max-maara]
  (let [kayttajan-urakat (kayttajatiedot/kayttajan-urakka-idt-aikavalilta
                           db
                           user
                           (fn [urakka-id kayttaja]
                             (oikeudet/voi-lukea? oikeudet/ilmoitukset-ilmoitukset urakka-id kayttaja)))
        tietyoilmoitukset (q-tietyoilmoitukset/hae-tietyoilmoitukset (:db harja.palvelin.main/harja-jarjestelma)
                                                                     {:alku (konv/sql-timestamp alku)
                                                                      :loppu (konv/sql-timestamp loppu)
                                                                      :urakat kayttajan-urakat
                                                                      :max-maara max-maara})
        tulos (mapv (fn [tietyilmoitus]
                      (as-> tietyilmoitus t
                            (update t :sijainti geo/pg->clj)
                            (konv/array->vec t :tyotyypit)
                            (assoc t :tyotyypit (mapv #(konv/pgobject->map % :tyyppi :string :selite :string) (:tyotyypit t)))
                            (konv/array->vec t :tienpinnat)
                            (assoc t :tienpinnat (mapv #(konv/pgobject->map % :materiaali :string :matka :long) (:tienpinnat t)))
                            (konv/array->vec t :kiertotienpinnat)
                            (assoc t :kiertotienpinnat (mapv #(konv/pgobject->map % :materiaali :string :matka :long) (:kiertotienpinnat t)))
                            (konv/array->vec t :nopeusrajoitukset)
                            (assoc t :nopeusrajoitukset (mapv #(konv/pgobject->map % :nopeusrajoitus :long :matka :long) (:nopeusrajoitukset t)))))
                    tietyoilmoitukset)]
    tulos))

(defrecord Tietyoilmoitukset []
  component/Lifecycle
  (start [{db :db
           tloik :tloik
           http :http-palvelin
           :as this}]
    (julkaise-palvelu http :hae-tietyoilmoitukset
                      (fn [user tiedot]
                        (hae-tietyoilmoitukset db user tiedot 501)))
    this)

  (stop [this]
    (poista-palvelut (:http-palvelin this)
                     :hae-tietyoilmoitukset)
    this))
