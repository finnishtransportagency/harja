(ns harja.kyselyt.laatupoikkeamat
  "Laatupoikkeamiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.palvelin.palvelut.yllapitokohteet.yllapitokohteet :as yllapitokohteet]
            [harja.geo :as geo]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]))

(defqueries "harja/kyselyt/laatupoikkeamat.sql"
  {:positional? true})

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))

(defn luo-tai-paivita-laatupoikkeama
  "Luo uuden laatupoikkeaman tai päivittää olemassaolevan laatupoikkeaman perustiedot. Palauttaa laatupoikkeaman id:n."
  [db user {:keys [id kohde tekija urakka aika selvitys-pyydetty kuvaus
                   sijainti tr yllapitokohde poistettu]}]
  (let [{:keys [numero alkuosa loppuosa alkuetaisyys loppuetaisyys]} tr]
    (when yllapitokohde
      (yy/vaadi-yllapitokohde-kuuluu-urakkaan-tai-on-suoritettavana-tiemerkintaurakassa db urakka yllapitokohde))
    (if id
      (do
       (paivita-laatupoikkeaman-perustiedot<! db
                                                (konv/sql-timestamp aika)
                                                (when tekija (name tekija))
                                                kohde
                                                (boolean selvitys-pyydetty)
                                                (:id user)
                                                kuvaus
                                                (when sijainti (geo/geometry (geo/clj->pg sijainti)))
                                                numero
                                                alkuosa
                                                loppuosa
                                                alkuetaisyys
                                                loppuetaisyys
                                                yllapitokohde
                                                (boolean poistettu)
                                                id
                                                urakka)
         id)

     (:id (luo-laatupoikkeama<! db
                                 "harja-ui"
                                 urakka
                                 (konv/sql-timestamp aika)
                                 (when tekija (name tekija))
                                 kohde
                                 (boolean selvitys-pyydetty)
                                 (:id user)
                                 kuvaus
                                 (when sijainti (geo/geometry (geo/clj->pg sijainti)))
                                 numero
                                 alkuosa
                                 loppuosa
                                 alkuetaisyys
                                 loppuetaisyys
                                 yllapitokohde
                                 nil)))))
