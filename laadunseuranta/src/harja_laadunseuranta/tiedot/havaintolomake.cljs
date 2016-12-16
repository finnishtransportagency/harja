(ns harja-laadunseuranta.tiedot.havaintolomake
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [reagent.ratom :as ratom]
            [cljs-time.local :as l]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.ui.kartta :as kartta]
            [cljs-time.local :as lt]
            [cljs-time.coerce :as tc]
            [harja-laadunseuranta.tiedot.ilmoitukset :as ilmoitukset]))

(defn sulje-lomake! []
  (reset! s/havaintolomake-auki? false))

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
             :tr-osoite (or @s/tr-osoite
                            {:tie nil
                             :aosa nil
                             :aet nil
                             :losa nil
                             :let nil})
             :aikaleima (l/local-now)
             :liittyy-havaintoon nil
             :laadunalitus? false
             :kuvaus ""}))
  s/havaintolomakedata)

(defn tallenna-lomake! []
  (when (reitintallennus/kirjaa-lomake!
          {:idxdb @s/idxdb
           :sijainti @s/sijainti
           :tarkastusajo-id @s/tarkastusajo-id
           :jatkuvat-havainnot @s/jatkuvat-havainnot
           :lomakedata @s/havaintolomakedata
           :epaonnistui-fn reitintallennus/merkinta-epaonnistui})
    (ilmoitukset/ilmoita "Lomake tallennettu!" s/ilmoitus)
    (kartta/lisaa-kirjausikoni "!")
    (tyhjenna-lomake!)
    (sulje-lomake!)))


(defn peruuta-lomake! []
  (.log js/console "Peru lomake!")
  (tyhjenna-lomake!)
  (sulje-lomake!))
