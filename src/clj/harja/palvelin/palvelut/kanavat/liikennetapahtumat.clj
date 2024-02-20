(ns harja.palvelin.palvelut.kanavat.liikennetapahtumat
  (:require [jeesql.core :refer [defqueries]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.kanavat.liikennetapahtumat :as q]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]))

(defqueries "harja/kyselyt/kanavat/urakat.sql")

(defn tallenna-liikennetapahtuma [db user tiedot]
  (assert (::lt/urakka-id tiedot) "Urakka id puuttuu, ei voi tallentaa liikennetapahtumaa!")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kanavat-liikenne user (::lt/urakka-id tiedot))
  (q/tallenna-liikennetapahtuma! db user (dissoc tiedot :hakuparametrit))
  (q/hae-liikennetapahtumat db user (:hakuparametrit tiedot)))

(defn hae-liikennetapahtumat [db user tiedot]
  (doseq [urakka-id (:urakka-idt tiedot)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-liikenne user urakka-id))
  (q/hae-liikennetapahtumat db user tiedot))

(defn hae-edelliset-tapahtumat [db user tiedot]
  (assert (::lt/urakka-id tiedot) "Urakka id puuttuu, ei voida hakea edellisiä tapahtumia!")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-liikenne user (::lt/urakka-id tiedot))
  (q/hae-edelliset-tapahtumat db tiedot))

(defn poista-ketjutus [db user tiedot]
  (assert (::lt/urakka-id tiedot) "Urakka id puuttuu, ei voida poistaa ketjutustietoja!")
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-kanavat-liikenne user (::lt/urakka-id tiedot))
  (q/poista-ketjutus! db (::lt-alus/id tiedot) (::lt/urakka-id tiedot))
  true)

(defn- hae-kayttajan-kanavaurakat
  "Hakee kaikki kanavaurakat hallintayksikön mukaan joihin käyttäjällä on lukuoikeus"
  [db user tiedot]
  (let [urakka-id (:urakka-id tiedot)
        hallintayksikko (:hallintayksikko tiedot)
        hallintayksikko_annettu (some? (:hallintayksikko tiedot))
        oikeustarkastus-fn (partial oikeudet/voi-lukea? oikeudet/urakat)]
    (oikeudet/vaadi-lukuoikeus oikeudet/urakat-kanavat-liikenne user urakka-id)
    (konv/sarakkeet-vektoriin
      (into []
        ;; Filtteröi urakat joihin ei oikeuksia 
        (comp
          (filter (fn [{:keys [urakka_id]}]
                    (oikeustarkastus-fn urakka_id user)))
          (map #(assoc % :tyyppi (keyword (:tyyppi %))))
          (map konv/alaviiva->rakenne))

        (let [hallintayksikko (cond
                                (nil? hallintayksikko) nil
                                (vector? hallintayksikko) hallintayksikko
                                :else [hallintayksikko])]
          (hae-kanavaurakat db
            {:hallintayksikko_annettu hallintayksikko_annettu
             :hallintayksikko hallintayksikko})))
      {:urakka :urakat}
      (juxt :tyyppi (comp :id :hallintayksikko)))))

(defrecord Liikennetapahtumat []
  component/Lifecycle
  (start [{http :http-palvelin
           db :db :as this}]
    
    (julkaise-palvelu http
      :hae-liikennetapahtumat
      (fn [user tiedot]
        (hae-liikennetapahtumat db user tiedot))
      {:kysely-spec ::lt/hae-liikennetapahtumat-kysely
       :vastaus-spec ::lt/hae-liikennetapahtumat-vastaus})
    
    (julkaise-palvelu http
      :hae-edelliset-tapahtumat
      (fn [user tiedot]
        (hae-edelliset-tapahtumat db user tiedot))
      {:kysely-spec ::lt/hae-edelliset-tapahtumat-kysely
       :vastaus-spec ::lt/hae-edelliset-tapahtumat-vastaus})
    
    (julkaise-palvelu http
      :tallenna-liikennetapahtuma
      (fn [user tiedot]
        (tallenna-liikennetapahtuma db user tiedot))
      {:kysely-spec ::lt/tallenna-liikennetapahtuma-kysely
       :vastaus-spec ::lt/tallenna-liikennetapahtuma-vastaus})
    
    (julkaise-palvelu http
      :poista-ketjutus
      (fn [user tiedot]
        (poista-ketjutus db user tiedot))
      {:kysely-spec ::lt/poista-ketjutus-kysely
       :vastaus-spec ::lt/poista-ketjutus-vastaus})
    
    (julkaise-palvelu http
      :hae-kayttajan-kanavaurakat
      (fn [user tiedot]
        (hae-kayttajan-kanavaurakat db user tiedot)))
    this)

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-liikennetapahtumat
      :hae-edelliset-tapahtumat
      :hae-kayttajan-kanavaurakat
      :tallenna-liikennetapahtuma
      :poista-ketjutus)
    this))

