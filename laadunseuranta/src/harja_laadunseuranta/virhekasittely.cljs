(ns harja-laadunseuranta.virhekasittely
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.sovellus :as sovellus]))

(defn ilmoita-virhe [teksti]
  (swap! sovellus/virheet #(conj % teksti)))

(defn- tyhjenna-virheet []
  (reset! sovellus/virheet []))

(defn virhekomponentti []
  (when (not (empty? @sovellus/virheet))
    [:div.virheboksi {:on-click tyhjenna-virheet}
     (for [virhe @sovellus/virheet]
       ^{:key (hash virhe)}
       [:div.virheilmoitus virhe])]))
