(ns harja.palvelin.integraatiot.tloik.sahkoposti
  "Ilmoitusten lähettäminen urakoitsijalle ja kuittausten vastaanottaminen"
  (:require [hiccup.core :refer [html]]
            [harja.domain.ilmoitusapurit :as apurit]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.ilmoitukset :as ilmoitukset]
            [taoensso.timbre :as log]))


(def ^{:doc "Ilmoituksen otsikon regex pattern, josta urakka ja ilmoitusid tunnistetaan" :const true :private true}
  otsikko-pattern #".*\#\[(\d+)/(\d+)\].*")

(def ^{:doc "Kuittaustyypit, joita sähköpostilla voi ilmoittaa" :const true :private true}
  kuittaustyypit [["Vastaanotettu" :vastaanotettu]
                  ["Aloitettu" :aloitettu]
                  ["Lopetettu" :lopetettu]])

(def ^{:doc "Kuittaustyypin tunnistava regex pattern" :const true :private true}
  kuittaustyyppi-pattern #"\[(Vastaanotettu|Aloitettu|Lopetettu)\]")

(def ^{:doc "Viesti, joka lähetetään vastaanottajalle kun saadaan sisään sähköposti, jota ei tunnisteta" :private true}
  +virheellinen-toimenpide-viesti+
  {:otsikko "Virheellinen toimenpideviesti"
   :sisalto "Lähettämäsi viestistä ei voitu päätellä toimenpidetietoja."})

(def ^{:doc "Viesti, joka lähetetään jos päivystäjätietoja tai ilmoitustietoja ei voida päätellä" :private true}
  +ilmoitustoimenpiteen-tallennus-epaonnistui+
  {:otsikko "Ilmoitustoimenpidettä ei voitu tallentaa"
   :sisalto "Varmista, että vastaat samalla sähköpostiosoitteella, johon ilmoitustiedot toimitettiin."})

(def ^{:doc "Viesti, joka lähetetään onnistuneen ilmoitustoimenpiteen tallennuksen jälkeen." :private true}
  +onnistunut-viesti+
  {:otsikko nil ;; tämä täydennetään ilmoituksen otsikolla
   :sisalto "Ilmoitustoimenpide tallennettu onnistuneeti."})

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

(def ^{:doc "Vastaanotetun kuittauksen mäppäys kuittaustyyppi tietokantaenumiksi" :private true}
  kuittaustyyppi->enum {:vastaanotettu "vastaanotto"
                        :aloitettu "aloitus"
                        :lopetettu "lopetus"})

(defn- tallenna-ilmoitustoimenpide [jms-lahettaja db lahettaja {:keys [urakka-id ilmoitus-id kuittaustyyppi kommentti]}]
  (let [paivystaja (first (yhteyshenkilot/hae-urakan-paivystaja-sahkopostilla db urakka-id lahettaja))
        ilmoitus (first (ilmoitukset/hae-ilmoitus-ilmoitus-idlla db ilmoitus-id))]
    (if-not paivystaja
      +ilmoitustoimenpiteen-tallennus-epaonnistui+
      (let [ilmoitustoimenpide-id (ilmoitustoimenpiteet/tallenna-ilmoitustoimenpide
                                   db ilmoitus kommentti
                                   (kuittaustyyppi->enum kuittaustyyppi) paivystaja)]
        (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja db ilmoitustoimenpide-id)
        (assoc +onnistunut-viesti+
               :otsikko (otsikko {:ilmoitus-id (:ilmoitusid ilmoitus)
                                  :urakka-id (:urakka ilmoitus)
                                  :ilmoitustyyppi (:ilmoitustyyppi ilmoitus)}))))))

(defn- virheellinen-viesti-vastaus [viesti]
  {:otsikko "Kuittauksen käsittely epäonnistui"
   :sisalto (str "Lähettämäsi kuittausviestin otsikosta ei voitu päätellä kuittaustietoja. Otsikko oli:\n"
                 (:otsikko viesti))})

(defn vastaanota-sahkopostikuittaus
  "Käsittelee sisään tulevan sähköpostikuittauksen ja palauttaa takaisin viestin, joka lähetetään 
kuittauksen lähettäjälle."
  [jms-lahettaja db {:keys [lahettaja otsikko sisalto]}]
  (log/debug (format "Vastaanotettiin T-LOIK kuittaus sähköpostilla. Viesti: %s." viesti))
  (let [v (lue-kuittausviesti otsikko sisalto)]
    (if (:ilmoitus-id v)
      (tallenna-ilmoitustoimenpide jms-lahettaja db lahettaja v)
      +virheellinen-toimenpide-viesti+)))
