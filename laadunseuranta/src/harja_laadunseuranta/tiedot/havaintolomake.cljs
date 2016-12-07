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
  (reset! s/havaintolomakedata
          {:kayttajanimi nil
           :tr-osoite {:tie nil
                       :aosa nil
                       :aet nil
                       :losa nil
                       :let nil}
           :aikaleima (l/local-now)
           :laadunalitus? false
           :kuvaus ""
           :kuva nil
           :esikatselukuva nil})
  s/havaintolomakedata)


(defn alusta-uusi-lomake! []
  (reset! s/havaintolomakedata
          (merge
            @s/havaintolomakedata
            {:kayttajanimi @s/kayttajanimi
             :tr-osoite @s/tr-osoite
             :aikaleima (l/local-now)
             :laadunalitus? false
             :kuvaus ""}))
  s/havaintolomakedata)

(defn tallenna-lomake! []
  (when (reitintallennus/kirjaa-lomake!
          {:idxdb @s/idxdb
           :sijainti s/sijainti
           :tarkastusajo-id s/tarkastusajo-id
           :jatkuvat-havainnot s/jatkuvat-havainnot
           :kuvaus (:kuvaus @s/havaintolomakedata)
           :epaonnistui-fn reitintallennus/merkinta-epaonnistui
           :laadunalitus (:laadunalitus? @s/havaintolomakedata)
           :kuva (:kuva @s/havaintolomakedata)})
    (.log js/console "Lomake tallennettu")
    (kartta/lisaa-kirjausikoni "!")
    (tyhjenna-lomake!)
    (sulje-lomake!)))


(defn peruuta-lomake! []
  (.log js/console "Peru lomake!")
  (tyhjenna-lomake!)
  (sulje-lomake!))
