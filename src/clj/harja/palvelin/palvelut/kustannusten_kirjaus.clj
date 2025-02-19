(ns harja.palvelin.palvelut.kustannusten-kirjaus
  "Palvelut kustannusten kirjauksille ja hauille"
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.kustannusten-kirjaus :as q]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]))

(defn hae-tiemerkinta-kustannuskirjaukset
  "Hakee tiemerkintäurakan kustannuskirjaukset"
  [db user urakka-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinnan user urakka-id)
  (let [vastaus (into []
                  (map konv/alaviiva->rakenne)
                  (q/hae-tiemerkinnan-kustannuskirjaukset db urakka-id))]
    vastaus))

(defn tallenna-tiemerkinta-kustannuskirjaukset
  "Tallentaa tiemerkintäurakan kustannuskirjaukset"
  [db user urakka-id];; {:keys [kustannukset pk1 pk2 pk3] :as tiedot}]
  (println "test!")
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-tiemerkinnan user)
  (let [vastaus (into []
                  (map konv/alaviiva->rakenne)
                  (q/hae-tiemerkinnan-kustannuskirjaukset db urakka-id))]
    vastaus))

(defrecord TiemerkinnanKustannusKirjaukset []
  component/Lifecycle
  (start [this]
      (julkaise-palvelu (:http-palvelin this)
        :tallenna-tiemerkinta-kustannuskirjaukset
        (fn [kayttaja urakka-id tiedot]
          (println "kayttaja: " kayttaja " urakka-id: " urakka-id " tiedot:  " tiedot)
          (tallenna-tiemerkinta-kustannuskirjaukset (:db this) kayttaja urakka-id)))
      (julkaise-palvelu (:http-palvelin this)
        :hae-tiemerkinta-kustannuskirjaukset
        (fn [kayttaja urakka-id]
          (println "kayttaja: " kayttaja " urakka : " urakka-id)
          (hae-tiemerkinta-kustannuskirjaukset (:db this) kayttaja urakka-id)))
    this)

    (stop [this]
      (poista-palvelut (:http-palvelin this)
        :hae-tiemerkinta-kustannuskirjaukset
        :tallenna-tiemerkinta-kustannuskirjaukset)
      this))


