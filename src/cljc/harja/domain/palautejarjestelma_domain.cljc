(ns harja.domain.palautejarjestelma-domain
  (:require
    [clojure.spec.alpha]
    [specql.rel :as rel]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["palautejarjestelma_aihe" ::aihe
   {"ulkoinen_id" ::aihe-id
    "nimi" ::nimi
    "jarjestys" ::jarjestys
    "kaytossa" ::kaytossa?
    ::tarkenteet (specql.rel/has-many
                    ::aihe-id
                    ::tarkenne
                    ::aihe-id)}]
  ["palautejarjestelma_tarkenne" ::tarkenne
   {"ulkoinen_id" ::tarkenne-id
    "nimi" ::nimi
    "jarjestys" ::jarjestys
    "kaytossa" ::kaytossa?
    "aihe_id" ::aihe-id}])

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