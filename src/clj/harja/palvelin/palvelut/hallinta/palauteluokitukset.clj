(ns harja.palvelin.palvelut.hallinta.palauteluokitukset
  (:require [com.stuartsierra.component :as component]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.integraatiot.palautejarjestelma.palautejarjestelma-komponentti :as palautejarjestelma-integraatio]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn- paivita-palauteluokitukset [palautejarjestelma kayttaja]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-palautevayla kayttaja)
  (palautejarjestelma-integraatio/paivita-aiheet-ja-tarkenteet palautejarjestelma))

(defrecord PalauteluokitustenHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin palautejarjestelma db] :as this}]
    (julkaise-palvelu http-palvelin :paivita-palauteluokitukset
      (fn [kayttaja _]
        (paivita-palauteluokitukset palautejarjestelma kayttaja)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-palauteluokitukset)
    this))
