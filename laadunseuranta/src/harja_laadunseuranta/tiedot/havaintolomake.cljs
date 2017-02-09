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

(defn tyhjenna-lomake!
  "Tyhjentää lomakkeen sovelluksen tilaan ja palauttaan lomakedata-atomin"
  []
  (reset! s/havaintolomakedata
          {:kayttajanimi nil
           :tr-osoite {:tie nil
                       :aosa nil
                       :aet nil
                       :losa nil
                       :let nil}
           :aikaleima (l/local-now)
           :laadunalitus? false
           :liittyy-havaintoon nil
           :liittyy-varmasti-tiettyyn-havaintoon? false
           :kuvaus ""
           :kuva nil
           :esikatselukuva nil})
  (reset! s/lomake-koskettu? false)
  s/havaintolomakedata)


(defn alusta-uusi-lomake!
  "Alustaa uuden lomakkeen sovelluksen tilaan ja palauttaan lomakedata-atomin"
  []
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
             :laadunalitus? false
             :kuvaus ""}))
  (reset! s/lomake-koskettu? false)
  s/havaintolomakedata)

(defn tallenna-lomake! []
  (when (reitintallennus/kirjaa-lomake!
          {:idxdb @s/idxdb
           :sijainti @s/sijainti
           :tarkastusajo-id @s/tarkastusajo-id
           :lomakedata @s/havaintolomakedata
           :epaonnistui-fn reitintallennus/merkinta-epaonnistui})
    (ilmoitukset/ilmoita "Lomake tallennettu!" s/ilmoitus {:tyyppi :onnistui})
    (kartta/lisaa-kirjausikoni "!")
    (tyhjenna-lomake!)
    (sulje-lomake!)))


(defn peruuta-lomake! []
  (.log js/console "Peru lomake!")
  (tyhjenna-lomake!)
  (sulje-lomake!))
