(ns harja.palvelin.komponentit.todennus
  "Tämä namespace määrittelee käyttäjäidentiteetin todentamisen. Käyttäjän todentaminen WWW-palvelussa tehdään KOKA ympäristön antamilla header tiedoilla. Tämä komponentti ei huolehdi käyttöoikeuksista, vaan pelkästään tarkistaa käyttäjän identiteetin."
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]))

(defprotocol Todennus
  "Protokolla HTTP pyyntöjen käyttäjäidentiteetin todentamiseen."
  (todenna-pyynto [this req] "Todenna annetun HTTP-pyynnön käyttäjätiedot, palauttaa uuden req mäpin, jossa käyttäjän tiedot on lisätty avaimella :kayttaja."))

(defrecord HttpTodennus []
  component/Lifecycle
  (start [this]
    (log/info "Todennetaan HTTP käyttäjä KOKA headereista.")
    this)
  (stop [this]
    this)

  Todennus
  (todenna-pyynto [this req]
    (let [headerit (:headers req)
          kayttaja-id (headerit "KOKA_USER_ID")]
      (log/warn "FIXME: toteuta tämä!")
      req)))

(defrecord FeikkiHttpTodennus [kayttaja]
  component/Lifecycle
  (start [this]
    (log/warn "Käytetään FEIKKI käyttäjätodennusta, käyttäjä = " (pr-str kayttaja))
    this)
  (stop [this]
    this
    )

  Todennus
  (todenna-pyynto [this req]
    (assoc req
      :kayttaja kayttaja)))

(defn http-todennus []
  (->HttpTodennus))

(defn feikki-http-todennus [kayttaja]
  (->FeikkiHttpTodennus kayttaja))


