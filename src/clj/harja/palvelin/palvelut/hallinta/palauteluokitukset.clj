(ns harja.palvelin.palvelut.hallinta.palauteluokitukset
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.integraatiot.palautevayla.palautevayla-komponentti :as palautevayla-integraatio]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn- paivita-palauteluokitukset [palautevayla kayttaja]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-palautevayla kayttaja)
  (palautevayla-integraatio/paivita-aiheet-ja-tarkenteet palautevayla))

(defrecord PalauteluokitustenHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin palautevayla db] :as this}]
    (julkaise-palvelu http-palvelin :paivita-palauteluokitukset
      (fn [kayttaja _]
        (paivita-palauteluokitukset palautevayla kayttaja)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-palauteluokitukset)
    this))
