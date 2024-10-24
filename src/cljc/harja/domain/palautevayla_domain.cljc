(ns harja.domain.palautevayla-domain
  (:require
    [clojure.spec.alpha]
    [specql.rel :as rel]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    #?@(:clj  [[harja.kyselyt.specql-db :refer [define-tables]]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["palautevayla_aihe" ::aihe
   {"ulkoinen_id" ::aihe-id
    "nimi" ::nimi
    "jarjestys" ::jarjestys
    "kaytossa" ::kaytossa?
    "muokattu" ::muokkaustiedot/muokattu
    "luotu" ::muokkaustiedot/luotu
    ::tarkenteet (specql.rel/has-many
                   ::aihe-id
                   ::tarkenne
                   ::aihe-id)}]
  ["palautevayla_tarkenne" ::tarkenne
   {"ulkoinen_id" ::tarkenne-id
    "nimi" ::nimi
    "jarjestys" ::jarjestys
    "kaytossa" ::kaytossa?
    "aihe_id" ::aihe-id
    "muokattu" ::muokkaustiedot/muokattu
    "luotu" ::muokkaustiedot/luotu}])

(def domain->api
  {::aihe :aihe
   ::aihe-id :aihe-id
   ::nimi :nimi
   ::jarjestys :jarjestys
   ::kaytossa? :kaytossa?
   ::tarkenteet :tarkenteet
   ::tarkenne :tarkenne
   ::tarkenne-id :tarkenne-id})

(defn hae-aihe [aiheet-ja-tarkenteet aihe]
  (->> aiheet-ja-tarkenteet
    (filter #(= aihe (:aihe-id %)))
    first
    :nimi))

(defn hae-tarkenne [aiheet-ja-tarkenteet tarkenne]
  (->> aiheet-ja-tarkenteet
    (map :tarkenteet)
    flatten
    (filter #(= tarkenne (:tarkenne-id %)))
    first
    :nimi))
