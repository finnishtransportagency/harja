(ns harja.kyselyt.havainnot
  "Havaintoihin liittyvät tietokantakyselyt"
  (:require [yesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(defqueries "harja/kyselyt/havainnot.sql")

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))

(defn luo-tai-paivita-havainto
  "Luo uuden havainnon tai päivittää olemassaolevan havainnon perustiedot. Palauttaa havainnon id:n."
  [db user {:keys [id kohde tekija urakka aika selvitys-pyydetty kuvaus] :as havainto}]
  (if id
    (do (paivita-havainnon-perustiedot<! db
                                        (konv/sql-timestamp aika) (name tekija) kohde
                                        (if selvitys-pyydetty true false)
                                        (:id user)
                                        kuvaus
                                        id)
        id)

    (:id (luo-havainto<! db urakka (konv/sql-timestamp aika) (name tekija) kohde
                         (if selvitys-pyydetty true false) (:id user) kuvaus
                         nil nil nil nil nil nil nil nil))))
