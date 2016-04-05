(ns harja.kyselyt.laatupoikkeamat
  "Laatupoikkeamiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/laatupoikkeamat.sql"
  {:positional? true})

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))

(defn luo-tai-paivita-laatupoikkeama
  "Luo uuden laatupoikkeaman tai päivittää olemassaolevan laatupoikkeaman perustiedot. Palauttaa laatupoikkeaman id:n."
  [db user {:keys [id kohde tekija urakka aika selvitys-pyydetty kuvaus sijainti tr]}]
  (let [{:keys [numero alkuosa loppuosa alkuetaisyys loppuetaisyys]} tr]
    (if id
     (do (paivita-laatupoikkeaman-perustiedot<! db
                                                (konv/sql-timestamp aika) (when tekija (name tekija)) kohde
                                                (if selvitys-pyydetty true false)
                                                (:id user)
                                                kuvaus
                                                (when sijainti (geo/geometry (geo/clj->pg sijainti)))
                                                numero
                                                alkuosa
                                                loppuosa
                                                alkuetaisyys
                                                loppuetaisyys
                                                id)
         id)

     (:id (luo-laatupoikkeama<! db
                                urakka
                                (konv/sql-timestamp aika)
                                (when tekija (name tekija))
                                kohde
                                (if selvitys-pyydetty true false)
                                (:id user)
                                kuvaus
                                (when sijainti (geo/geometry (geo/clj->pg sijainti)))
                                numero
                                alkuosa
                                loppuosa
                                alkuetaisyys
                                loppuetaisyys
                                nil)))))
