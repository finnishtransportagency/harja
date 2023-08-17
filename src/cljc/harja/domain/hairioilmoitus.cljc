(ns harja.domain.hairioilmoitus
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.set :as set]
            [specql.rel :as rel]
            [harja.domain.muokkaustiedot :as m]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            ])
            [harja.pvm :as pvm])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["hairioilmoitus_tyyppi" ::hairioilmoitus-tyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["hairioilmoitus" ::hairioilmoitus
   {"voimassa" ::voimassa?}])

(def tyyppi-fmt
  {:hairio "Häiriöilmoitus"
   :tiedote "Tiedote"})

(def sarakkeet #{::id ::viesti ::pvm ::voimassa? ::tyyppi ::alkuaika ::loppuaika})

(defn tuleva-hairio [hairiot]
   (->> hairiot
     (filter #(and
                (::voimassa? %)
                (pvm/ennen? (pvm/nyt) (::alkuaika %))))
     (sort-by ::pvm)
     first))

(defn tulevat-hairiot [hairiot]
  (->> hairiot
    (filter #(and
               (::voimassa? %)
               (::alkuaika %)
               (pvm/ennen? (pvm/nyt) (::alkuaika %))))
    (sort-by ::alkuaika)))

(defn vanhat-hairiot [hairiot]
  (->> hairiot
    (filter #(or
               (not (::voimassa? %))
               (pvm/jalkeen? (pvm/nyt) (::loppuaika %))))
    (sort-by ::loppupvm)))

(defn aikavalit-leikkaavat-sivuaminen-sallittu? [ensimmainen-alku ensimmainen-loppu toinen-alku toinen-loppu]
  (boolean (or
             (and
               (not (nil? toinen-alku))
               (not (nil? toinen-loppu))
               (not (nil? ensimmainen-alku))
               (not (nil? ensimmainen-loppu))
               (pvm/jalkeen? toinen-loppu ensimmainen-alku)
               (pvm/ennen? toinen-alku ensimmainen-loppu))
             (and
               (nil? toinen-alku)
               (not (nil? toinen-loppu))
               (not (nil? ensimmainen-alku))
               (not (nil? ensimmainen-loppu))
               (pvm/jalkeen? toinen-loppu ensimmainen-alku))
             (and
               (nil? toinen-loppu)
               (not (nil? toinen-alku))
               (not (nil? ensimmainen-alku))
               (not (nil? ensimmainen-loppu))
               (pvm/ennen? toinen-alku ensimmainen-loppu))
             (and
               (nil? ensimmainen-alku)
               (not (nil? toinen-alku))
               (not (nil? toinen-loppu))
               (not (nil? ensimmainen-loppu))
               (pvm/jalkeen? ensimmainen-loppu toinen-alku))
             (and
               (nil? ensimmainen-loppu)
               (not (nil? toinen-alku))
               (not (nil? toinen-loppu))
               (not (nil? ensimmainen-alku))
               (pvm/ennen? ensimmainen-alku toinen-loppu))
             (and
               (nil? ensimmainen-alku)
               (nil? toinen-alku))
             (and
               (nil? ensimmainen-loppu)
               (nil? toinen-loppu))
             (and
               (nil? ensimmainen-alku)
               (nil? toinen-loppu)
               (pvm/jalkeen? ensimmainen-loppu toinen-alku))
             (and
               (nil? ensimmainen-loppu)
               (nil? toinen-alku)
               (pvm/jalkeen? ensimmainen-alku toinen-loppu)))))
(defn onko-paallekkainen [uusialku uusiloppu vanhat]
  (some #(aikavalit-leikkaavat-sivuaminen-sallittu? (::alkuaika %) (::loppuaika %) uusialku uusiloppu) vanhat))

(defn voimassaoleva-hairio
  ([hairiot]
   (voimassaoleva-hairio hairiot (pvm/nyt)))
  ([hairiot aika]
   (->> hairiot
     (filter #(and
                (::voimassa? %)
                (cond
                  (and (::alkuaika %) (::loppuaika %))
                  (pvm/valissa? aika (::alkuaika %) (::loppuaika %) false)

                  (::alkuaika %)
                  (pvm/sama-tai-jalkeen? aika (::alkuaika %) false)

                  (::loppuaika %)
                  (pvm/sama-tai-ennen? aika (::loppuaika %) false)

                  :else true)))
     (sort-by ::pvm)
     first)))
