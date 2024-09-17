(ns harja.kyselyt.talvihoitoreitit
  "Talvihoitoreitteihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/talvihoitoreitit.sql"
  {:positional? true})

(declare lisaa-sijainti-talvihoitoreitille<! lisaa-kalusto-sijainnille<! lisaa-talvihoitoreitti<!)

(defn lisaa-kalustot-ja-reitit [db talvihoitoreitti-id data]
  ;; Lisää reitit
  (doseq [sijainti (remove nil? (:sijainnit data))
          :let [sijainti-id (:id (lisaa-sijainti-talvihoitoreitille<! db
                                   {:talvihoitoreitti_id talvihoitoreitti-id
                                    :tie (:tie sijainti)
                                    :alkuosa (:aosa sijainti)
                                    :alkuetaisyys (:aet sijainti)
                                    :loppuosa (:losa sijainti)
                                    :loppuetaisyys (:let sijainti)
                                    :pituus (:pituus sijainti)
                                    :hoitoluokka (:hoitoluokka sijainti)}))
                _ (doseq [kalusto (:kalustot sijainti)]
                    (lisaa-kalusto-sijainnille<! db
                      {:sijainti_id sijainti-id
                       :maara (:kalusto-lkm kalusto)
                       :kalustotyyppi (:kalustotyyppi kalusto)}))]]))

(defn lisaa-talvihoitoreitti-tietokantaan [db data urakka_id kayttaja_id]
  (lisaa-talvihoitoreitti<! db {:nimi (:reittinimi data)
                                :ulkoinen_id (:tunniste data)
                                :urakka_id urakka_id
                                :kayttaja_id kayttaja_id}))
