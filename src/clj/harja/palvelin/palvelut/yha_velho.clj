(ns harja.palvelin.palvelut.yha-velho
  "Päällystysilmoituksen lähetys yhaan ja velhoon yhdellä pyynnöllä"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [harja.palvelin.integraatiot.yha-velho.yha-velho-komponentti :as yha-velho]
            [harja.domain.oikeudet :as oikeudet]))

(defn laheta-pot-yhaan-ja-velhoon
  "Lähettää annetut kohteet Velhoon."
  [velho user {:keys [urakka-id kohde-id]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (log/debug (format "Lähetetään kohde: %s Velhoon" kohde-id))
  (yha-velho/laheta-pot velho urakka-id kohde-id)
  true)




(defn laheta-pot-yhaan-ja-velhoon
  "Lähettää annetu kohde yhaan ja velhoon, kutsumalla yha ja velho lähetys palvelut"
  [yla-velho user {:keys [urakka-id kohde-id]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (tarkista-lahetettavat-kohteet db kohde-idt)
  (log/debug (format "Lähetetään kohteet: %s YHAan ja velhoon" kohde-id))
  (let [lahetys (try+ (yha/laheta-kohteet yha urakka-id kohde-idt)
                      (catch [:type yha/+virhe-kohteen-lahetyksessa+] {:keys [virheet]}
                        virheet))
        lahetys-onnistui? (not (contains? lahetys :virhe))
        paivitetyt-ilmoitukset (paallystys-q/hae-urakan-paallystysilmoitukset-kohteineen db urakka-id sopimus-id vuosi)]
    (merge
      {:paallystysilmoitukset paivitetyt-ilmoitukset}
      (when-not lahetys-onnistui?
        lahetys))))



(defrecord YhaVelho []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          yha-velho (:yha-velho-integraatio this)]
      (julkaise-palvelu http :laheta-pot-yhaan-ja-velhoon
                        (fn [user data]
                          (laheta-pot-yhaan-ja-velhoon yha-velho user data))))
    this)
  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :laheta-pot-yhaan-ja-velhoon)
    this))
