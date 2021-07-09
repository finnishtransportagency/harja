(ns tarkkailija.palvelin.komponentit.jarjestelma-rajapinta
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.jarjestelma-rajapinta :as rajapinta]
            [tarkkailija.palvelin.rajapinta-protokolla :as p]))

(defrecord Rajapintakasittelija []
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this)
  p/IRajapinta
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
