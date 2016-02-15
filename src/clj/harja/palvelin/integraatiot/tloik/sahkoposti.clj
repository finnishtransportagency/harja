(ns harja.palvelin.integraatiot.tloik.sahkoposti
  "Ilmoitusten lähettäminen urakoitsijalle ja kuittausten vastaanottaminen"
  (:require [hiccup.core :refer [html]]
            [harja.domain.ilmoitusapurit :as apurit]
            [harja.pvm :as pvm]
            [clojure.string :as str]))


(def ^{:doc "Ilmoituksen otsikon regex pattern, josta urakka ja ilmoitusid tunnistetaan" :const true :private true}
  otsikko-pattern #".*\#\[(\d+)/(\d+)\].*")

(def ^{:doc "Kuittaustyypit, joita sähköpostilla voi ilmoittaa" :const true :private true}
  kuittaustyypit [["Vastaanotettu" :vastaanotettu]
                  ["Aloitettu" :aloitettu]
                  ["Lopetettu" :lopetettu]])

(def ^{:doc "Kuittaustyypin tunnistava regex pattern" :const true :private true}
  kuittaustyyppi-pattern #"\[(Vastaanotettu|Aloitettu|Lopetettu)\]")

(defn- otsikko [{:keys [ilmoitus-id urakka-id ilmoitustyyppi]}]
  (str "#[" urakka-id "/" ilmoitus-id "] " (apurit/ilmoitustyypin-nimi (keyword ilmoitustyyppi))))

(defn- html-nappi
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

(defn- viesti [vastausosoite otsikko ilmoitus]
  (html
   [:div
    [:table
     (for [[kentta arvo] [["Ilmoitettu" (:ilmoitettu ilmoitus)]
                          ["Otsikko" (:otsikko ilmoitus)]
                          ["Lyhyt selite" (:lyhytselite ilmoitus)]
                          ["Selitteet" (apurit/parsi-selitteet (mapv keyword (:selitteet ilmoitus)))]
                          ["Ilmoittaja" (apurit/nayta-henkilo (:ilmoittaja ilmoitus))]]]
       [:tr
        [:td [:b kentta]]
        [:td arvo]])]
    [:blockquote (:pitkaselite ilmoitus)]
    (for [teksti (map first kuittaustyypit)]
      [:div {:style "padding-top: 10px;"}
       (html-nappi vastausosoite teksti otsikko (str "[" teksti "]"))])]))

(defn otsikko-ja-viesti [vastausosoite ilmoitus]
  (let [otsikko (otsikko ilmoitus)
        viesti (viesti
                vastausosoite
                otsikko
                ilmoitus)]
    [otsikko viesti]))

(defn viestin-kuittaustyyppi [sisalto]
  (when-let [nimi (some->> sisalto
                           (re-find kuittaustyyppi-pattern)
                           second)]
    (second (first (filter #(= (first %) nimi) kuittaustyypit)))))

(defn viesti-ilman-kuittaustyyppia [sisalto]
  (str/replace sisalto kuittaustyyppi-pattern ""))

(defn lue-kuittausviesti
  "Lukee annetun kuittausviestin otsikosta ja sisällöstä kuittauksen tiedot mäpiksi"
  [otsikko sisalto]
  (let [[_ urakka-id ilmoitus-id] (re-matches otsikko-pattern otsikko)
        kuittaustyyppi (viestin-kuittaustyyppi sisalto)
        kommentti (str/trim (viesti-ilman-kuittaustyyppia sisalto))]
    (if (and urakka-id ilmoitus-id kuittaustyyppi)
      {:urakka-id (Long/parseLong urakka-id)
       :ilmoitus-id (Long/parseLong ilmoitus-id)
       :kuittaustyyppi kuittaustyyppi
       :kommentti (when-not (str/blank? kommentti)
                    kommentti)}
      {:virhe "Viestistä ei löytynyt kuittauksen tietoja"})))
