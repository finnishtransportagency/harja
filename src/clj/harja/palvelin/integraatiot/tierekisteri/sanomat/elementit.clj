(ns harja.palvelin.integraatiot.tierekisteri.sanomat.elementit
  (:require [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [hiccup.core :refer [html]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn muodosta-tietue [tietue]
  (let [tie (get-in tietue [:sijainti :tie])]
    [:tietue
     [:tunniste (:tunniste tietue)]
     [:alkupvm (:alkupvm tietue)]
     (when (:loppupvm tietue) [:loppupvm (:loppupvm tietue)])
     (when (:karttapvm tietue) [:karttapvm (:karttapvm tietue)])
     (when (:piiri tietue) [:piiri (:piiri tietue)])
     (when (:kuntoluokka tietue) [:kuntoluokka (:kuntoluokka tietue)])
     (when (:urakka tietue) [:urakka (:urakka tietue)])
     [:sijainti
      [:tie
       [:numero (:numero tie)]
       [:aet (:aet tie)]
       [:aosa (:aosa tie)]
       (when (:let tie) [:let (:let tie)])
       (when (:losa tie) [:losa (:losa tie)])
       (when (:ajr tie) [:ajr (:ajr tie)])
       (when (:puoli tie) [:puoli (:puoli tie)])]]
     [:tietolaji
      [:tietolajitunniste (get-in tietue [:tietolaji :tietolajitunniste])]
      [:arvot (get-in tietue [:tietolaji :arvot])]]]))


