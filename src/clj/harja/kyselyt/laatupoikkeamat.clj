(ns harja.kyselyt.laatupoikkeamat
  "Laatupoikkeamiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/laatupoikkeamat.sql"
  {:positional? true})

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))

(defn tarkista-yllapitokohteen-urakka [db urakka-id yllapitokohde]
  "Tarkistaa, että ylläpitokohde kuuluu urakkaan. Jos ei kuulu, heittää poikkeuksen."
  (let [kohteen-urakka (:id (first (hae-yllapitokohteen-urakka-id db yllapitokohde)))]
    (when-not (= kohteen-urakka urakka-id)
      (throw (RuntimeException. (str "Ylläpitokohde " yllapitokohde " ei kuulu valittuun urakkaan "
                                     urakka-id " vaan urakkaan " kohteen-urakka))))))

(defn luo-tai-paivita-laatupoikkeama
  "Luo uuden laatupoikkeaman tai päivittää olemassaolevan laatupoikkeaman perustiedot. Palauttaa laatupoikkeaman id:n."
  [db user {:keys [id kohde tekija urakka aika selvitys-pyydetty kuvaus sijainti tr yllapitokohde]}]
  (let [{:keys [numero alkuosa loppuosa alkuetaisyys loppuetaisyys]} tr]
    (when yllapitokohde
      (tarkista-yllapitokohteen-urakka db urakka yllapitokohde))
    (if id
     (do

       (paivita-laatupoikkeaman-perustiedot<! db
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
                                                yllapitokohde
                                                id
                                                urakka)
         id)

     (do (log/debug (str "Luodaan: " (pr-str id) " ja " urakka))
         (:id (luo-laatupoikkeama<! db
                                 "harja-ui"
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
                                 yllapitokohde
                                 nil))))))
