(ns harja.kyselyt.tarkastukset
  (:require [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konv]
            [harja.geo :as geo]))

(defqueries "harja/kyselyt/tarkastukset.sql")

(defn luo-tai-paivita-tarkastus
  "Luo uuden tarkastuksen, palauttaa id:n."
  [db user urakka-id {:keys [id aika tr tyyppi tarkastaja mittaaja sijainti]} havainto]
  (if (nil? id)
    (:id (luo-tarkastus<! db
                          urakka-id (konv/sql-timestamp aika)
                          (:numero tr) (:alkuosa tr) (:alkuetaisyys tr) (:loppuosa tr) (:loppuetaisyys tr)
                          (and sijainti (geo/luo-point sijainti)) ;; sijainti haetaan VKM:stä frontilla
                          tarkastaja mittaaja (name tyyppi) havainto (:id user)))
    
    (do (log/info "TARKASTUS PÄIVITETÄÄN: " id)
        (paivita-tarkastus! db
                            (konv/sql-timestamp aika)
                            (:numero tr) (:alkuosa tr) (:alkuetaisyys tr) (:loppuosa tr) (:loppuetaisyys tr)
                            (and sijainti (geo/luo-point sijainti))
                            tarkastaja mittaaja (name tyyppi) (:id user)
                            urakka-id id)
        id)))

(defn luo-tai-paivita-talvihoitomittaus [db tarkastus uusi?
                                         {:keys [talvihoitoluokka lumimaara epatasaisuus
                                                 kitka lampotila ajosuunta] :as talvihoitomittaus}]
  (if uusi?
    (do (log/info "PARAMS:"  db
                  (or talvihoitoluokka "") lumimaara epatasaisuus
                  kitka lampotila (or ajosuunta 0)
                  tarkastus)
        (luo-talvihoitomittaus<! db
                                 (or talvihoitoluokka "") lumimaara epatasaisuus
                                 kitka lampotila (or ajosuunta 0)
                                 tarkastus))
    (paivita-talvihoitomittaus! db
                                (or talvihoitoluokka "") lumimaara epatasaisuus
                                kitka lampotila (or ajosuunta 0)
                                tarkastus)))

(defn luo-tai-paivita-soratiemittaus [db tarkastus uusi?
                                      {:keys [hoitoluokka tasaisuus kiinteys polyavyys sivukaltevuus]}]
  (if uusi?
    (luo-soratiemittaus<! db
                          hoitoluokka tasaisuus
                          kiinteys polyavyys
                          sivukaltevuus
                          tarkastus)
    (paivita-soratiemittaus! db
                             hoitoluokka tasaisuus
                             kiinteys polyavyys
                             sivukaltevuus
                             tarkastus)))
