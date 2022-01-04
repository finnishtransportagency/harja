(ns harja.palvelin.palvelut.varuste-velho
  "Varustetoteumien haku Velhosta"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-komponentti]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-velho-varustetoteumat
  [velho user]
  ; TODO korja tämä kopioitu oikeustarkastus yha-velhosta
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user)
  (try (velho-komponentti/hae-velho-varustetoteumat velho)
       (catch Throwable t (log/error "Virhe haettaessa varustetoteumia Harjasta: " t))))

(defn tuo-uudet-varustetoteumat-velhosta
  [velho user]
  ; TODO korja tämä kopioitu oikeustarkastus yha-velhosta
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user)
  (try (velho-komponentti/tuo-uudet-varustetoteumat-velhosta velho)
       (catch Throwable t (log/error "Virhe varustetoteumien haussa: " t)))
  true)

(defrecord VarusteVelho []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          velho (:velho-integraatio this)]

      (julkaise-palvelu http :hae-ulkoiset-varustetoteumat
                        (fn [user data]
                          (println "petrisi1419: kutsutaan hae-velho-varustetoteumat")
                          (hae-velho-varustetoteumat velho user)))

      (julkaise-palvelu http :petrisi-manuaalinen-testirajapinta-varustetoteumat
                        (fn [user data]
                          (println "petrisi1111: kutsutaan hae-varustetoteumat-velhosta")
                          (tuo-uudet-varustetoteumat-velhosta velho user)))
    this))
  (stop [this]
    (let [http (:http-palvelin this)]
      (poista-palvelut http :hae-ulkoiset-varustetoteumat)
      (poista-palvelut http :petrisi-manuaalinen-testirajapinta-varustetoteumat))
    this))
