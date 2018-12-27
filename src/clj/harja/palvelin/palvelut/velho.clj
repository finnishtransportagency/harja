(ns harja.palvelin.palvelut.velho
  "Velho on suunnitelma ja toteumatietovarasto sekä uusi tierekisteri"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho]
            [harja.domain.oikeudet :as oikeudet]))

(defn laheta-kohteet-velhoon
  "Lähettää annetut kohteet Velhoon."
  [velho user {:keys [urakka-id kohde-idt]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (log/debug (format "Lähetetään kohteet: %s Velhoon" kohde-idt))
  (velho/laheta-kohteet velho urakka-id kohde-idt)
  true)

(defrecord Velho []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          velho (:velho-integraatio this)]
      (julkaise-palvelu http :laheta-kohteet-velhoon
                        (fn [user data]
                          (laheta-kohteet-velhoon velho user data))))
    this)
  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :laheta-kohteet-velhoon)
    this))
