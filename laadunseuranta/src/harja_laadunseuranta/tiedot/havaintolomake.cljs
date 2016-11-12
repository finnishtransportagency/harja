(ns harja-laadunseuranta.tiedot.havaintolomake
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [reagent.ratom :as ratom]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.ui.kartta :as kartta]
            [cljs-time.local :as lt]
            [cljs-time.coerce :as tc]))

(defn sulje-lomake! []
  (reset! s/havaintolomake-auki false))

(defn tyhjenna-lomake! []
  (reset! s/havaintolomakedata {:kayttajanimi @s/kayttajanimi
                                :tr-osoite @s/tr-osoite
                                :aikaleima (l/local-now)
                                :laadunalitus? false
                                :kuvaus ""
                                :kuva nil}))

(defn tallenna-lomake! []
  (.log js/console "Tallenna lomake!")
  (reitintallennus/kirjaa-kertakirjaus
    @s/idxdb
    {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
     :aikaleima (tc/to-long (lt/local-now))
     :tarkastusajo @s/tarkastusajo-id
     :havainnot (into #{} (remove nil? (conj @s/jatkuvat-havainnot :yleishavainto)))
     :mittaukset {}
     :kuvaus (:kuvaus @s/havaintolomakedata)
     :laadunalitus (:laadunalitus? @s/havaintolomakedata)
     :kuva (:kuva @s/havaintolomakedata)})
  (kartta/lisaa-kirjausikoni "!")
  (tyhjenna-lomake!)
  (sulje-lomake!))

(defn alusta-uusi-lomake! []
  (tyhjenna-lomake!)
  s/havaintolomakedata)

(defn peruuta-lomake! []
  (.log js/console "Peru lomake!")
  (tyhjenna-lomake!)
  (sulje-lomake!))
