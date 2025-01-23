(ns harja.kyselyt.talvihoitoreitit
  "Talvihoitoreitteihin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.tieverkko :as tieverkko-kyselyt]
            [clojure.string :as str]
            [harja.kyselyt.konversio :as konv]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.laadunseuranta.talvihoitoreitit-domain :as talvihoitoreitit-domain]
            [taoensso.timbre :as log]
            [harja.domain.tierekisteri.validointi :as tr-validointi]))

(defqueries "harja/kyselyt/talvihoitoreitit.sql"
  {:positional? true})

(declare lisaa-sijainti-talvihoitoreitille<! lisaa-kalusto-sijainnille<! lisaa-talvihoitoreitti<!
  hae-urakan-talvihoitoreitit hae-sijainti-talvihoitoreitille hae-talvihoitoreitti-ulkoisella-idlla
  hae-leikkaavat-geometriat)

(defn- hoitoluokkaryhma
  "Hoitoluokat kuuluvat UI:lla kolmeen ryhmään: Kävelyn ja pyöräilyn väylät, Maantiet ja Huoltoaukot ja pysäköintialueet.
   Ryhmitellään hoitoluokat näiden ryhmien mukaan."
  [hoitoluokka]
  (case hoitoluokka
    "Talvihoito" :huoltoaukot
    "Hoito osin" :huoltoaukot
    "Ei talvihoitoa" :huoltoaukot
    :maantiet))

(defn lisaa-reitit [db talvihoitoreitti-id data]
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
                                    :hoitoluokka (:hoitoluokka sijainti)}))]]))

(defn lisaa-talvihoitoreitti-tietokantaan [db data urakka_id kayttaja_id]
  ;; Generoidaan talvihoitoreitille värikoodi tietokantaan, jotta se ei vaihdu, kun käyttöliittymässä piirretään
  ;; Talvihoitoreitti kartalle
  (lisaa-talvihoitoreitti<! db {:nimi (:reittinimi data)
                                :ulkoinen_id (:tunniste data)
                                :urakka_id urakka_id
                                :kayttaja_id kayttaja_id
                                :tr_maara (get-in data [:kalustot :tr_maara])
                                :kup_maara (get-in data [:kalustot :kup_maara])
                                :ka_maara (get-in data [:kalustot :ka_maara])
                                :varikoodi (talvihoitoreitit-domain/anna-random-vari nil)}))

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
                                            :tr_maara (get-in data [:kalustot :tr_maara])
                                            :kup_maara (get-in data [:kalustot :kup_maara])
                                            :ka_maara (get-in data [:kalustot :ka_maara])
                                            :kayttaja_id kayttaja_id})
            ;; Lisää kalustot ja reitit
            (lisaa-reitit db (:id talvihoitoreitti) data))]
    ;; Jos ei tule erroreita, niin palautetaan ulkoinen-id
    (:tunniste data)))

(defn validoi-talvihoitoreitin-sijainnit [db talvihoitoreitti]
  (reduce (fn [virheet r]
            (let [;; Tietokantapohjainen validointi
                  tievalidointi (tieverkko-kyselyt/tieosoitteen-validointi db (:tie r) (:aosa r) (:aet r) (:losa r) (:let r))
                  tievalidointivirhe (if (and (not (nil? tievalidointi)) (not (nil? (:validaatiovirheet tievalidointi))))
                                       {:virheet (str "Reitin " (:reittinimi talvihoitoreitti) ", virheet: " (str/join (vec (mapcat identity (:validaatiovirheet tievalidointi)))))}
                                       nil)

                  ;; Hoitoluokan validointi
                  hoitoluokka-vastaus (tr-validointi/validoi-hoitoluokka (:hoitoluokka r))
                  hoitoluokkavirhe (if-not (nil? hoitoluokka-vastaus)
                                     {:virheet (str "Reitin " (:reittinimi talvihoitoreitti) ", hoitoluokassa virhe: " hoitoluokka-vastaus)}
                                     nil)
                  virheet (if (not (nil? tievalidointivirhe))
                            (conj virheet tievalidointivirhe)
                            virheet)
                  virheet (if (not (nil? hoitoluokkavirhe))
                            (conj virheet hoitoluokkavirhe)
                            virheet)]
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
                                :let (:let r)
                                :ulkoinen-id (:ulkoinen_id talvihoitoreitti)})]
              (if-not (empty? leikkaavat)
                (conj leikkaavat-geometriat
                  {:leikkaavat (format "Reitin: %s, Tieosoite: %s leikkaa jo olemassa olevan talvihoitoreitin kanssa."
                                 (:reittinimi talvihoitoreitti)
                                 (tr/tr-osoite-moderni-fmt
                                   (:tie r) (:aosa r) (:aet r)
                                   (:losa r) (:let r)))})
                leikkaavat-geometriat)))
    [] (:sijainnit talvihoitoreitti)))

(defn hae-ja-muokkaa-talvihoitoreitit [db urakka-id]
  (let [urakan-talvihoitoreitit (hae-urakan-talvihoitoreitit db {:urakka_id urakka-id})
        _ (log/debug "hae-urakan-talvihoitoreitit :: urakan-talvihoitoreitit" urakan-talvihoitoreitit)
        talvihoitoreitit (mapv (fn [rivi]
                                 (let [;; Hae reitit erikseen
                                       reitit (hae-sijainti-talvihoitoreitille db {:talvihoitoreitti_id (:id rivi)})
                                       ;; Formatoi käyttöliittymälle valmiiksi
                                       reitit (map (fn [r]
                                                     (-> r
                                                       (assoc :sijainti (:reitti r))
                                                       (assoc :formatoitu-tr (tr/osoiteosat-moderni-fmt
                                                                               (:alkuosa r) (:alkuetaisyys r)
                                                                               (:loppuosa r) (:loppuetaisyys r)))
                                                       (dissoc :reitti))) reitit)

                                       ;; Jaotellaan reitti hoitoluokittan UI:ta varten
                                       hoitoluokat (vec (vals (group-by :hoitoluokka (map (fn [r]
                                                                                            (dissoc r :sijainti :tie :alkuosa
                                                                                              :alkuetaisyys :loppuosa :loppuetaisyys
                                                                                              :id :formatoitu-tr)) reitit))))

                                       ;; Lasketaan jokaiselle hoitoluokalle pituus
                                       hoitoluokat (mapv (fn [hoitoluokka-vec]
                                                           {:ryhma (hoitoluokkaryhma (:hoitoluokka (first hoitoluokka-vec)))
                                                            :hoitoluokka (:hoitoluokka (first hoitoluokka-vec))
                                                            :pituus (reduce + (map :laskettu_pituus hoitoluokka-vec))})
                                                     hoitoluokat)
                                       ;; Ryhmitellään lopuksi hoitoluokat ryhmän mukaan
                                       hoitoluokat (group-by :ryhma hoitoluokat)
                                       rivi (-> rivi
                                              (assoc :reitit reitit)
                                              (assoc :laskettu_pituus (reduce + (map :laskettu_pituus reitit)))
                                              (assoc :hoitoluokat hoitoluokat)
                                              (dissoc :muokkaaja :muokattu :luotu :luoja))]
                                   rivi))
                           urakan-talvihoitoreitit)]
    talvihoitoreitit))
