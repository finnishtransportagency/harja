(ns harja.palvelin.palvelut.yllapitokohteet.paallystyskohteen-korjausluokka-test
  (:require [clojure.test :refer :all]
            [harja.testi :refer :all]))

;; Tehdään yllapitokohdeosa, josta tiedetään, että se on 100m feikki korjausluokan päällä ja verrataan geometrioista, että tuleeko sama mitta
(deftest yllapitokohdeosan-pituus-feikki-luokka-testi
  (let [;; Luodaan feikki korjausluokka tauluun - Määritellään sen pituudeksi annetulla tiellä 400m
        pk {:tie 18747 :aosa 1 :losa 1 :aet 0 :let 4000}
        ;; Tämä tr osoite on sitä varten, että mitataan, kuinka pitkästti pk luokkaa tällä tieosalla on. Pitäisi olla tuo 400m
        tr {:tie 18747 :aosa 1 :losa 1 :aet 0 :let 7590}

        pkgeom_format (format "(SELECT tr_osoitteelle_viiva3(%s::INTEGER,  %s::INTEGER, %s::INTEGER, %s::INTEGER, %s::INTEGER)::geometry)",
                        (:tie pk) (:aosa pk) (:aet pk) (:losa pk) (:let pk))

        trgeom_format (format "(SELECT tr_osoitteelle_viiva3(%s::INTEGER,  %s::INTEGER, %s::INTEGER, %s::INTEGER, %s::INTEGER)::geometry)",
                        (:tie tr) (:aosa tr) (:aet tr) (:losa tr) (:let tr))

        ;; Lisätään itse keksitty päällysteen_korjausluokka
        sql_str (format "INSERT INTO paallysteen_korjausluokka (tie, aosa, aet, losa, let, korjausluokka, paivitetty, geometria)
         VALUES (%s, %s, %s, %s, %s, '%s', NOW(), %s)",
               (:tie pk) (:aosa pk) (:aet pk) (:losa pk) (:let pk) "PK4" pkgeom_format)
        ;; Tehdään itse haku
        vastaus (u sql_str)

        ;; Lasketaan pkgeomin pituus
        pkgeom_pituus (:pituus (first (q-map (format "SELECT ROUND(COALESCE(st_length(%s),0)::NUMERIC,0) AS pituus" pkgeom_format))))
        trgeom_pituus (:pituus (first (q-map (format "SELECT ROUND(COALESCE(st_length(%s),0)::NUMERIC,0) AS pituus" trgeom_format))))

        ;; Mitataan funktion laske_yllapitokohdeosan_pk_pituudet koodeilla pk luokan pituus
        erotus (:erotus (first (q-map (format "SELECT ROUND(COALESCE(st_length (st_difference (%s, %s)), 0)::numeric,0) AS erotus", trgeom_format pkgeom_format))))]
    (is (=marginaalissa? (BigDecimal. (:let pk)) pkgeom_pituus 20))
    (is (=marginaalissa? (BigDecimal. (:let tr)) trgeom_pituus 20))
    ;; Ihan metrilleen ei saada tarkkoja lukuja, kun tieosoitteen paaluvälit ei ole ihan metrejä, joten sallitaan pieni marginaali
    (is (=marginaalissa?  (BigDecimal. (- (:let tr) (:let pk))) erotus 20))))
