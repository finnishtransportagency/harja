(ns harja.palvelin.integraatiot.tloik.sahkoposti
  "Ilmoitusten lähettäminen urakoitsijalle ja kuittausten vastaanottaminen"
  (:require [hiccup.core :refer [html]]
            [harja.domain.ilmoitusapurit :as apurit]
            [harja.pvm :as pvm]))


(def ^{:doc "Ilmoituksen otsikon regex pattern, josta urakka ja ilmoitusid tunnistetaan"
       :const true :private true}
  otsikko-pattern #".*\#\[(\d+)/(\d+)\].*")


(defn- muodosta-otsikko [{:keys [ilmoitus-id urakka-id ilmoitustyyppi]}]
  (str "#[" urakka-id "/" ilmoitus-id "] " (apurit/ilmoitustyypin-nimi (keyword ilmoitustyyppi))))

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
     (for [[kentta arvo] [["Ilmoitettu" (pvm/pvm-aika (:ilmoitettu ilmoitus))]
                          ["Otsikko" (:otsikko ilmoitus)]
                          ["Lyhyt selite" (:lyhytselite ilmoitus)]
                          ["Selitteet" (apurit/parsi-selitteet (mapv keyword (:selitteet ilmoitus)))]
                          ["Ilmoittaja" (apurit/nayta-henkilo (:ilmoittaja ilmoitus))]]]
       [:tr
        [:td [:b kentta]]
        [:td arvo]])]
    [:blockquote (:pitkaselite ilmoitus)]
    (for [n ["Vastaanotettu" "Aloitettu" "Lopetettu"]]
      [:div {:style "padding-top: 10px;"}
       (luo-html-nappi vastausosoite "Vastaanotettu" otsikko "Vastaanotettu")])]))

(defn otsikko-ja-viesti [vastausosoite ilmoitus]
  (let [otsikko (muodosta-otsikko ilmoitus)
        viesti (muodosta-viesti
                vastausosoite
                otsikko
                ilmoitus)]
    [otsikko viesti]))


