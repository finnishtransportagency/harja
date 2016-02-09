(ns harja.kyselyt.tarkastukset
  (:require [yesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.kyselyt.konversio :as konv]
            [harja.geo :as geo])
  (:import (org.postgis PGgeometry)))

(defqueries "harja/kyselyt/tarkastukset.sql")

(defn luo-tai-paivita-tarkastus
  "Luo uuden tai päivittää tarkastuksen ja palauttaa id:n."
  [db user urakka-id {:keys [id aika tr tyyppi tarkastaja sijainti ulkoinen-id havainnot] :as tarkastus}]
  (log/info "tarkastus: " tarkastus)
  (let [sijainti (if (instance? PGgeometry sijainti)
                   sijainti
                   (and sijainti (geo/geometry (geo/clj->pg sijainti))))]
    (if (nil? id)
      (do
        (log/debug "Luodaan uusi tarkastus")
        (:id (luo-tarkastus<! db
                              urakka-id (konv/sql-timestamp aika)
                              (:numero tr) (:alkuosa tr) (:alkuetaisyys tr) (:loppuosa tr) (:loppuetaisyys tr)
                              sijainti tarkastaja (name tyyppi) (:id user) ulkoinen-id havainnot)))

      (do (log/debug (format "Päivitetään tarkastus id: %s " id))
          (paivita-tarkastus! db
                              (konv/sql-timestamp aika)
                              (:numero tr) (:alkuosa tr) (:alkuetaisyys tr) (:loppuosa tr) (:loppuetaisyys tr)
                              sijainti tarkastaja (name tyyppi) (:id user)
                              havainnot
                              urakka-id id)
          id))))

(defn luo-tai-paivita-talvihoitomittaus [db tarkastus uusi?
                                         {:keys [hoitoluokka lumimaara tasaisuus
                                                 kitka lampotilaIlma lampotilaTie ajosuunta] :as talvihoitomittaus}]
  (log/debug "Talvihoitoluokka" hoitoluokka)
  (if uusi?
    (do (log/info "PARAMS:" db
                  (or hoitoluokka "") lumimaara tasaisuus
                  kitka lampotilaIlma lampotilaTie (or ajosuunta 0)
                  tarkastus)
        (luo-talvihoitomittaus<! db
                                 (or hoitoluokka "") lumimaara tasaisuus
                                 kitka lampotilaIlma lampotilaTie (or ajosuunta 0)
                                 tarkastus))
    (paivita-talvihoitomittaus! db
                                (or hoitoluokka "") lumimaara tasaisuus
                                kitka lampotilaIlma lampotilaTie (or ajosuunta 0)
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
