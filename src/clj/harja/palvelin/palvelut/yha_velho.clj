(ns harja.palvelin.palvelut.yha-velho
  "Päällystysilmoituksen lähetys yhaan ja velhoon yhdellä pyynnöllä"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+]]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.palvelin.palvelut.yha-apurit :as yha-apurit]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho]
            [harja.domain.oikeudet :as oikeudet]))

(defn laheta-pot-yhaan-ja-velhoon
  "Lähettää annettu pot YHAan ja Velhoon."
  [db yha velho kehitysmoodi? user {:keys [urakka-id kohde-id paallystetoteuma-url]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yha-apurit/tarkista-lahetettavat-kohteet db [kohde-id])
  (log/debug (format "Lähetetään kohde: %s YHAan ja Velhoon" kohde-id))
  (let [yha-lahetys (try+ (yha/laheta-kohteet yha urakka-id [kohde-id])
                          (catch [:type yha/+virhe-kohteen-lahetyksessa+] {:keys [virheet]}
                            virheet))
        ;; Velho-lähetys toistaiseksi pois päältä. Testattu enimmäkseen toimivaksi testiympäristössä. On vielä selvityksessä, otetaanko Velho-lähetys käyttöön.
        #_velho-lahetys #_(when kehitysmoodi?
                        (try+ (velho/laheta-kohde velho urakka-id kohde-id)
                          (catch [:type yha/+virhe-kohteen-lahetyksessa+] {:keys [virheet]}
                            virheet)))
        tila (first (q-yllapitokohteet/hae-yha-velho-lahetyksen-tila db {:kohde-id kohde-id}))]
    tila))

(defrecord YhaVelho [asetukset]
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          yha (:yha-integraatio this)
          velho (:velho-integraatio this)]
      (julkaise-palvelu http :laheta-pot-yhaan-ja-velhoon
                        (fn [user data]
                          (laheta-pot-yhaan-ja-velhoon db yha velho (:kehitysmoodi asetukset) user data))))
    this)
  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :laheta-pot-yhaan-ja-velhoonn)
    this))
