(ns harja.palvelin.palvelut.varuste-velho
  "Varustetoteumien haku Velhosta"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-komponentti]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-varustetoteumat-velhosta
  "Hakee uudet varustetoteumat Velhosta"
  [velho user]
  (log/debug "Haetaan varustetoteumat Velhosta")
  ; TODO korja tämä kopioitu oikeustarkastus yha-velhosta
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user)
  (try (velho-komponentti/hae-varustetoteumat velho)
       (catch Throwable t (log/error "Virhe varustetoteumien haussa: " t)))
  true)

; TODO ***************************************
; TODO PETRISI ÄLÄ JULKAISE TÄTÄ TUOTANTOON!!!
; TODO ***************************************

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
