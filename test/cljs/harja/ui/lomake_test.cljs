(ns harja.ui.lomake-test
  (:require [harja.ui.lomake :as lomake]
            [reagent.core :as r]
            [clojure.spec :as s]
            [cljs.test :as t :include-macros true]))

(s/def ::luoja string?)
(s/def ::vuosi integer?)
(s/def ::nimi string?)

(def paradigmat #{:functional :object-oriented :procedural :declarative})
(s/def ::paradigma paradigmat)

(s/def ::kieli (s/keys :req [::luoja ::vuosi ::nimi]
                       :opt [::paradigma]))

(def skeema [{:nimi ::nimi :otsikko "Nimi" :tyyppi :string}
             {:nimi ::luoja :otsikko "Luoja" :tyyppi :string}
             {:nimi ::vuosi :otsikko "Vuosi" :tyyppi :positiivinen-numero}
             {:nimi ::paradigma :otsikkoa "Paradigma"
              :tyyppi :valinta :valinnat (vec paradigmat)}])

(defn testilomake []
  (r/with-let [data (r/atom {})]
    [lomake/lomake
     {:muokkaa! #(reset! data %)
      :spec ::kieli}
     skeema
     @data]))
