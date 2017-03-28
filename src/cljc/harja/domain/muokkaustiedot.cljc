(ns harja.domain.muokkaustiedot
  "Yleiset muokkaustiedot"
  (:require [harja.pvm :as pvm]))


(defn lisaa-muokkaustiedot [x user]
  (if-not (::luoja x)
    ;; X on uusi, lisätään luoja ja luontiaika
    (assoc x
           ::luoja-id (:id user)
           ::luotu (pvm/nyt))

    ;; X on olemassaoleva, päivitetään muokkaustiedot
    (assoc x
           ::muokkaaja-id (:id user)
           ::muokattu (pvm/nyt))))
