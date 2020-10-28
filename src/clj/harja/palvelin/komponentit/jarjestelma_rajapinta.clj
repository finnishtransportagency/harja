(ns harja.palvelin.komponentit.jarjestelma-rajapinta
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.jarjestelma-rajapinta :as rajapinta]))

(defprotocol IRajapinta
  (lisaa [this nimi f])
  (poista [this nimi]))

(defrecord Rajapintakasittelija []
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this)
  IRajapinta
  (lisaa [_ nimi f]
    (when-not (and (keyword? nimi)
                   (ifn? f))
      (throw (IllegalArgumentException. (str "Järjestelmärajapintaan voi laittaa keyword ifn parin. Nyt annettiin "
                                             (type nimi) " " (type f) " pari"))))
    (swap! rajapinta/rajapinta assoc nimi f))
  (poista [_ nimi]
    (when-not (keyword? nimi)
      (throw (IllegalArgumentException. (str "Poistettava järjestelmärajapinta pitää olla keyword. Nyt annettiin " (type nimi)))))
    (swap! rajapinta/rajapinta dissoc nimi)))
