(ns harja.palvelin.palvelut.velho
  "Velho on suunnitelma ja toteumatietovarasto sekä uusi tierekisteri"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+]]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho]
            [harja.domain.oikeudet :as oikeudet]))

(defn laheta-kohde-velhoon
  "Lähettää annetu kohde Velhoon."
  [db velho user {:keys [urakka-id kohde-id]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (log/debug (format "Lähetetään kohde: %s Velhoon" kohde-id))
  (let [lahetys (try+ (velho/laheta-kohde velho urakka-id kohde-id)
                      (catch [:type velho/+virhe-kohteen-lahetyksessa+] {:keys [virheet]}
                        virheet))
        lahetys-onnistui? (not (contains? lahetys :virhe))
        lahetyksen-tila (first (yllapitokohteet-q/hae-yllapitokohteen-velho-lahetyksen-tila db {:kohdeid kohde-id}))]
    (merge
      {:lahetyksen-tila lahetyksen-tila}
      (when-not lahetys-onnistui?
        lahetys))))

(defrecord Velho []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          velho (:velho-integraatio this)]
      (julkaise-palvelu http :laheta-kohde-velhoon
                        (fn [user data]
                          (laheta-kohde-velhoon db velho user data))))
    this)
  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :laheta-kohde-velhoon)
    this))
