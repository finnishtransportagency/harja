(ns harja.palvelin.integraatiot.tloik.sahkoposti
  "Ilmoitusten lähettäminen urakoitsijalle ja kuittausten vastaanottaminen"
  (:require [hiccup.core :refer [html]]))


(def ^{:doc "Ilmoituksen otsikon regex pattern, josta urakka ja ilmoitusid tunnistetaan"
       :const true :private true}
  otsikko-pattern #".*\#\[(\d+)/(\d+)\].*")


(defn- muodosta-otsikko [urakka-id {id :id :as ilmoitus}]
  (str "#[" urakka-id "/" id " "
       "FIXME: tähän kuvaus ilmoituksesta"))

(defn- luo-html-nappi
  "Luo HTML-fragmentin mailto: napin sähköpostia varten. Tämä täytyy tyylitellä inline, koska ei voida
resursseja liitää sähköpostiin mukaan luotettavasti."
  [vastausosoite napin-teksti subject body]
  [:table {:width "100%" :border "0" :cellspacing "0" :cellpadding "0"}
   [:tr
    [:td
     [:table {:border "0" :cellspacing "0" :cellpadding "0"}
      [:tr
       [:td {:bgcolor "#EB7035" :style "padding: 12px; border-radius: 3px;" :align "center"}
        [:a {:href (str "mailto:" vastausosoite "?subject=" subject "&body=" body)
             :style "font-size: 16px; font-family: Helvetica, Arial, sans-serif; font-weight: normal; color: #ffffff; text-decoration: none; display: inline-block;"}
         napin-teksti]]]]]]])

(defn- muodosta-viesti [vastausosoite otsikko ilmoitus]
  (html
   [:div
    [:table
     (for [[kentta arvo] [["Aika" "2012-23-55 65:33"]
                          ["Tyyppi" "Toimenpidepyyntö"
                           "Kuvaus" "jotainhan siellä tapahtui, jospa viittisitte mennä katsomaan"]]]
       [:tr
        [:td kentta]
        [:td arvo]])]
    (for [n ["Vastaanotettu" "Aloitettu" "Lopetettu"]]
      [:div {:style "padding-top: 10px;"}
       (luo-html-nappi vastausosoite "Vastaanotettu" otsikko "Vastaanotettu")])]))



