(ns harja.tyokalut.html
  "Apureita yksinkertaisten HTML-elementtien tekemiseen (esim. sähköpostilähetystä varten)"
  (:require [clojure.xml :refer [parse]]
            [clojure.java.io :as io]
            [clojure.zip :refer [xml-zip]]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html h]]
            [clj-time.format :as f]
            [clj-time.coerce :as tc]
            [clojure.data.zip.xml :as z]
            [clojure.string :as str]))

(defn sanitoi
  "Sanitoi tekstin käyttämällä Hiccupin escape-html funktiota.
   escape-html sanitoi hipsumerkin (') tuloksella &apos;
   Tämä ei tiettävästi toimi HTML4:ssä eikä esim. Macin Outlookissa (ainakaan tätä kirjoittaessa),
   joten &apos; korvataan toimivalla entiteetillä: &#39;"
  [teksti]
  (let [sanitoitu (h teksti)
        korjattu (str/replace sanitoitu "&apos;" "&#39;")]
    korjattu))

(defn tietoja
  "Luo yksinkertaisen kenttä-arvo pareja sisältävän listauksen.
   Arvo voi olla tekstiä tai HTML-elementti, jos sanitointi otetaan pois päältä.

   Optiot:
   sanitoi?        Sanitoi kentän ja arvon, jottei taulukossa voi esittää HTML-koodia. Oletuksena true.
                   Arvojen tulee olla tekstiä tai tekstiksi muunnettavia.
                   Jos kentän tai arvojen halutaan olevan komponentteja tai muusta syystä ei haluta
                   sanitoida, voidaan sanitoinniksi asettaa false,
                   jolloin arvojen sanitointi on kutsujapään vastuulla.
   arvon metatieto
        - sanitoi? Määrittää, otetaanko yksittäinen arvon sisältö sanitoituna mukaan. Sanitoinnin tarpeen tunnistaminen
                   kutsujapään vastuulla."
  ([kentta-arvo-parit] (tietoja {} kentta-arvo-parit))
  ([{:keys [sanitoi?] :as optiot} kentta-arvo-parit]
   (let [sanitoi-taulukko? (or sanitoi? true)]
     [:table
      (for [[kentta arvo] kentta-arvo-parit]
        (let [arvo-meta (meta arvo)
              ;; Defaulttina sanitoidaan kaikki arvot, ellei arvon metatiedoista löydy sanitoi?-asetusta.
              ;; Näin voidaan määritellä otetaanko mukaan esimerkiksi HTML-elementtejä yksittäiseen kenttä-arvo-pariin,
              ;; mutta sanitoidaan muut taulukon kenttä-arvo-parit.
              sanitoi-arvo? (cond
                               (nil? (:sanitoi? arvo-meta)) sanitoi-taulukko?
                               (boolean? (:sanitoi? arvo-meta)) (:sanitoi? arvo-meta)
                               :else sanitoi-taulukko?)]
          [:tr
           [:td [:b (if sanitoi-taulukko? (sanitoi kentta) kentta)]]
           [:td (if sanitoi-arvo? (sanitoi arvo) arvo)]]))])))

(defn nappilinkki
  "Luo yksinkertaisen nappi-linkin.
   Napin teksti sanitoidaan."
  [napin-teksti linkki]
  [:table {:width "100%" :border "0" :cellspacing "0" :cellpadding "0"}
   [:tr
    [:td
     [:table {:border "0" :cellspacing "0" :cellpadding "0"}
      [:tr
       [:td {:bgcolor "#EB7035" :style "padding: 12px; border-radius: 3px;" :align "center"}
        [:a {:href linkki
             :style "font-size: 16px; font-family: Helvetica, Arial, sans-serif; font-weight: normal; color: #ffffff; text-decoration: none; display: inline-block;"}
         (sanitoi napin-teksti)]]]]]]])
