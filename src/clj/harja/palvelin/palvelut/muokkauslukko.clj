(ns harja.palvelin.palvelut.muokkauslukko
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as coerce]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.muokkauslukko :as q]))

(def suurin-sallittu-lukon-ika-minuuteissa 5)

(defn lukko-vanhentunut?
  "Kertoo onko lukon suurin salittu ikä ylittynyt. true tai false"
  [lukko]
  (log/debug "Tarkistetaan lukon ikä")
  (let [lukon-aikaleima (coerce/from-sql-time (:aikaleima lukko))
        aika-nyt (t/now)
        lukko-vanhentunut (t/after? aika-nyt (t/plus lukon-aikaleima (t/minutes suurin-sallittu-lukon-ika-minuuteissa)))]
    (if lukko-vanhentunut
      (do (log/debug "Lukko on vanhentunut")
          true)
      (do (log/debug "Lukko ei ole vanhentunut")
          false))))

(defn virkista-lukko [db user {:keys [id]}]
  (log/debug "Virkistetään lukko")
  (q/virkista-lukko! db id (:id user)))

(defn vapauta-lukko [db user {:keys [id]}]
  (log/debug "Vapautetaan lukko")
  (q/vapauta-lukko! db id (:id user)))

(defn hae-lukko-idlla
  "Hakee lukon id:llä.
  Jos lukko löytyy, palauttaa sen.
  Jos lukko löytyy, mutta se on vanhentunut, poistaa sen ja palauttaa nil
  Jos lukkoa ei löydy, palauttaa nil."
  [db user {:keys [id]}]
  (jdbc/with-db-transaction [c db]
    (log/debug "Haetaan lukko id:llä " id)
    (let [lukko (q/hae-lukko-idlla c id)]
      (log/debug "Lukko saatu: " (pr-str lukko))
      (if lukko
        (if (lukko-vanhentunut? lukko)
          (vapauta-lukko db user {:id (:id lukko)})
          lukko)))))

(defn lukitse
  "Yrittää luoda uuden lukon annetulla id:llä.
  Jos onnistuu, palauttaa lukon id:n.
  Jos epäonnistuu, palauttaa nil"
  [db user {:keys [id]}]
  (jdbc/with-db-transaction [c db]
    (log/debug "Yritetään lukita " id)
    (let [lukko (first (q/hae-lukko-idlla db id))]
      (log/debug "Tarkistettiin vanha lukko. Tulos: " (pr-str lukko))
      (if (nil? lukko)
        (do
          (log/debug "Lukitaan " id)
          (let [vastaus (q/luo-lukko<! c id (:id user))]
            (:id vastaus)))
        (do
          (if (lukko-vanhentunut? lukko)
            (do
              (vapauta-lukko db user {:id (:id lukko)})
              (q/luo-lukko<! c id (:id user)))
            (do (log/debug "Ei voida lukita " id " koska on jo lukittu!")
                nil)))))))

(defrecord Muokkauslukko []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)]
      (julkaise-palvelu http :hae-lukko-idlla
                        (fn [user tiedot]
                          (hae-lukko-idlla db user tiedot)))
      (julkaise-palvelu http :lukitse
                        (fn [user tiedot]
                          (lukitse db user tiedot)))
      (julkaise-palvelu http :vapauta-lukko
                        (fn [user tiedot]
                          (vapauta-lukko db user tiedot)))
      (julkaise-palvelu http :virkista-lukko
                        (fn [user tiedot]
                          (virkista-lukko db user tiedot)))
      this))

  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :hae-lukko-idlla
      :lukitse
      :vapauta-lukko
      :virkista-lukko)
    this))