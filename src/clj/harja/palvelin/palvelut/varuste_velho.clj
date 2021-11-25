(ns harja.palvelin.palvelut.varuste-velho
  "Varustetoteumien haku Velhosta"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-komponentti]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-varustetoteumat-velhosta
  "Hakee uudet varustetoteumat Velhosta"
  [velho user]
  (log/debug "Haetaan varustetoteumat Velhosta")
  ; TODO korja tämä kopioitu oikeustarkastus yha-velhosta
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user)
  (let [velho-lahetys (try+ (velho-komponentti/hae-varustetoteumat velho)
                            (catch [:type velho-komponentti/+virhe-varustetoteuma-haussa+] {:keys [virheet]}
                              virheet))
        tila true                                           ;#_(first (q-yllapitokohteet/hae-varuste-velho-lahetyksen-tila db {:kohde-id kohde-id})
        ]
    tila))

; TODO ÄLÄ JULKAISE TÄTÄ TUOTANTOON!!!
(defrecord VarusteVelho []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          velho (:velho-integraatio this)]
      (julkaise-palvelu http :petrisi-varustetoteumat
                        (fn [user data]
                          (println "petrisi1111: kutsutaan hae-varustetoteumat-velhosta o7")
                          (hae-varustetoteumat-velhosta velho user)))
    this))
  (stop [this]
    (let [http (:http-palvelin this)]
      (poista-palvelut http :hae-varustetoteumat))
    this))
