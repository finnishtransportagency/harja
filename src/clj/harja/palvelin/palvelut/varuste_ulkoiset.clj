(ns harja.palvelin.palvelut.varuste-ulkoiset
  "Varustetoteumien backend"
  (:require [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [slingshot.slingshot :refer [try+]]
            [harja.palvelin.integraatiot.velho.velho-komponentti :as velho-komponentti]
            [harja.palvelin.integraatiot.velho.varusteet :as varusteet]
            [harja.kyselyt.toteumat :as toteumat-q]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]))

(defn luo-pvm-oikein [vuosi kuukausi paiva]
  (pvm/luo-pvm vuosi (- kuukausi 1) paiva))

(defn hae-urakan-varustetoteuma-ulkoiset
  [db user {:keys [urakka-id hoitovuosi kuntoluokka toteuma] :as tiedot}]
  (when (nil? urakka-id) (throw (IllegalArgumentException. "urakka-id on pakollinen")))
  (when (nil? hoitovuosi) (throw (IllegalArgumentException. "hoitovuosi on pakollinen")))
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-toteumat-varusteet user urakka-id)
  (let [hoitokauden-alkupvm (luo-pvm-oikein hoitovuosi 10 01)
        hoitokauden-loppupvm (luo-pvm-oikein (+ 1 hoitovuosi) 9 30)
        toteumat (toteumat-q/hae-urakan-uusimmat-varustetoteuma-ulkoiset db {:urakka urakka-id
                                                                             :hoitokauden_alkupvm (konv/sql-date hoitokauden-alkupvm)
                                                                             :hoitokauden_loppupvm (konv/sql-date hoitokauden-loppupvm)
                                                                             :kuntoluokka kuntoluokka
                                                                             :toteuma toteuma})]
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
