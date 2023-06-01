(ns harja.palvelin.palvelut.palauteluokitukset
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.palautevayla :as q]
            [harja.domain.oikeudet :as oikeudet]))

(defn- hae-palauteluokitukset [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/ilmoitukset-ilmoitukset kayttaja)
  (q/hae-aiheet-ja-tarkenteet db))

(defrecord Palauteluokitukset []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelu http-palvelin :hae-palauteluokitukset
      (fn [kayttaja _]
        (hae-palauteluokitukset db kayttaja)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-palauteluokitukset)
    this))
