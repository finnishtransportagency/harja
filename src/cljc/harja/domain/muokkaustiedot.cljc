(ns harja.domain.muokkaustiedot
  "Yleiset muokkaustiedot"
  (:require [harja.pvm :as pvm]
            [harja.id :as id]))

(def ^{:doc "Perus muokkaustiedot, jotka ovat yleiset kaikille käyttäville tauluille"}
  muokkaustiedot
  {"luoja" ::luoja-id
   "luotu" ::luotu
   "muokkaaja" ::muokkaaja-id
   "muokattu" ::muokattu})

(def ^{:doc "poistettu boolean flag sarake"}
  poistettu?-sarake
  {"poistettu" ::poistettu?})

(def ^{:doc "poistajan tunniste (käyttäjä id)"}
  poistaja-sarake
  {"poistaja" ::poistaja-id})

(def muokkauskentat
  #{:muokattu
    :muokkaaja-id
    :luotu
    :luoja-id
    :poistettu?
    :poistaja-id})

(defn lisaa-muokkaustiedot [x id-kentta user]
  (if-not (id/id-olemassa? (get x id-kentta))
    ;; X on uusi, lisätään luoja ja luontiaika
    (-> x
        (assoc ::luoja-id (:id user)
               ::luotu (pvm/nyt))
        (dissoc ::muokkaaja-id ::muokattu))

    ;; X on olemassaoleva, päivitetään muokkaustiedot
    (-> x
        (assoc ::muokkaaja-id (:id user)
               ::muokattu (pvm/nyt))
        (dissoc ::luoja-id ::luotu))))
