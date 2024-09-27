(ns harja.kyselyt.talvihoitoreitit
  "Talvihoitoreitteihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
            [clojure.string :as str]
            [harja.domain.tierekisteri :as tr]
            [taoensso.timbre :as log]
            [harja.domain.tierekisteri.validointi :as tr-validointi]))

(defqueries "harja/kyselyt/talvihoitoreitit.sql"
  {:positional? true})

(declare lisaa-sijainti-talvihoitoreitille<! lisaa-kalusto-sijainnille<! lisaa-talvihoitoreitti<!
  hae-urakan-talvihoitoreitit hae-sijainti-talvihoitoreitille hae-talvihoitoreitti-ulkoisella-idlla
  hae-leikkaavat-geometriat)

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
                                    :pituus (:pituus sijainti) ;; Pituus on metreinä
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

(defn paivita-talvihoitoreitti-tietokantaan [db data urakka_id kayttaja_id]
  (let [;; Haetaan talvihoitoreitin perustiedot ulkoisen id:n perusteella
        talvihoitoreitti (first (hae-talvihoitoreitti-ulkoisella-idlla db {:urakka_id urakka_id
                                                                           :ulkoinen_id (:tunniste data)}))

        ;; Jos talvihoitoreitti löytyy, niin deletoidaan kaikki kalusto ja reitit, ja tallennetaan ne uudestaan.
        _ (when talvihoitoreitti
            (poista-talvihoitoreitin-sijainnit! db {:talvihoitoreitti_id (:id talvihoitoreitti)})
            ;; Päivitä talvihoitoreitin perustiedot
            (paivita-talvihoitoreitti<! db {:talvihoitoreitti_id (:id talvihoitoreitti)
                                            :nimi (:reittinimi data)
                                            :kayttaja_id kayttaja_id})
            ;; Lisää kalustot ja reitit
            (lisaa-kalustot-ja-reitit db (:id talvihoitoreitti) data))]
    ;; Jos ei tule erroreita, niin palautetaan ulkoinen-id
    (:tunniste data)))

(defn validoi-talvihoitoreitin-sijainnit [db rivi-indeksi talvihoitoreitti]
  (reduce (fn [virheet r]
            (let [;; Tietokantapohjainen validointi
                  tievalidointi (tieverkko-kyselyt/tieosoitteen-validointi db (:tie r) (:aosa r) (:aet r) (:losa r) (:let r))
                  tievalidointivirhe (if (and (not (nil? tievalidointi)) (not (nil? (:validaatiovirheet tievalidointi))))
                                       {:virheet (str "Rivillä " @rivi-indeksi " reitin " (:reittinimi talvihoitoreitti) ", virheet: " (str/join (vec (mapcat identity (:validaatiovirheet tievalidointi)))))}
                                       nil)

                  ;; Hoitoluokan validointi
                  hoitoluokka-vastaus (tr-validointi/validoi-hoitoluokka (:hoitoluokka r))
                  hoitoluokkavirhe (if-not (nil? hoitoluokka-vastaus)
                                     {:virheet (str "Rivillä " @rivi-indeksi " reitin " (:reittinimi talvihoitoreitti) ", hoitoluokassa virhe: " hoitoluokka-vastaus)}
                                     nil)
                  virheet (if (not (nil? tievalidointivirhe))
                            (conj virheet tievalidointivirhe)
                            virheet)
                  virheet (if (not (nil? hoitoluokkavirhe))
                            (conj virheet hoitoluokkavirhe)
                            virheet)
                  _ (swap! rivi-indeksi inc)]
              virheet))
    [] (:sijainnit talvihoitoreitti)))

(defn leikkaavat-geometriat [db talvihoitoreitti urakka-id]
  (reduce (fn [leikkaavat-geometriat r]
            (let [leikkaavat (hae-leikkaavat-geometriat db
                               {:urakka_id urakka-id
                                :tie (:tie r)
                                :aosa (:aosa r)
                                :losa (:losa r)
                                :aet (:aet r)
                                :let (:let r)})]
              (if-not (empty? leikkaavat)
                (conj leikkaavat-geometriat
                  {:leikkaavat (format "Reitin: %s, Tieosoite: %s leikkaa jo olemassa olevan talvihoitoreitin kanssa."
                                 (:reittinimi talvihoitoreitti)
                                 (tr/tr-osoite-moderni-fmt
                                   (:tie r) (:aosa r) (:aet r)
                                   (:losa r) (:let r)))})
                leikkaavat-geometriat)))
    [] (:sijainnit talvihoitoreitti)))
