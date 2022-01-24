(ns harja.palvelin.palvelut.varuste-ulkoiset
  "Varustetoteumien backend"
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-komponentti]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.domain.oikeudet :as oikeudet]))

(defn hae-urakan-varustetoteuma-ulkoiset
  [db user {:keys [urakka-id kuntoluokka] :as tiedot}]
  (when (nil? urakka-id) (throw (IllegalArgumentException. "urakka-id on pakollinen")))
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (let [toteumat (toteumat-q/hae-urakan-uusimmat-varustetoteuma-ulkoiset db {:urakka urakka-id
                                                                             :kuntoluokka kuntoluokka})
        _ (println "petrisi1337: " toteumat)]
    {:urakka-id urakka-id :toteumat toteumat}))

(defn tuo-uudet-varustetoteumat-velhosta
  "Integraation kutsu selaimen avulla. Tämä on olemassa vain testausta varten."
  [velho user]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user)
  (try (velho-komponentti/tuo-uudet-varustetoteumat-velhosta velho)
       (catch Throwable t (log/error "Virhe varustetoteumien haussa: " t)))
  true)

(defrecord VarusteVelho []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          velho (:velho-integraatio this)]

      (julkaise-palvelu http :hae-urakan-varustetoteuma-ulkoiset
                        (fn [user {:keys [urakka-id] :as tiedot}]
                          (println "petrisi1103: Haetaan urakan " urakka-id " tiedot")
                          (hae-urakan-varustetoteuma-ulkoiset (:db this) user tiedot)))

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
