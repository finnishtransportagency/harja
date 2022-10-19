(ns harja.palvelin.integraatiot.tloik.sahkoposti
  "Ilmoitusten lähettäminen urakoitsijalle ja kuittausten vastaanottaminen"
  (:require [hiccup.core :refer [html]]
            [harja.domain.tieliikenneilmoitukset :as apurit]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.tieliikenneilmoitukset :as ilmoitukset]
            [harja.domain.tieliikenneilmoitukset :as ilm]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.tyokalut.html :refer [sanitoi] :as html-tyokalut]
            [harja.geo :as geo]
            [harja.fmt :as fmt]
            [harja.domain.tierekisteri :as tierekisteri]))

(def ^{:doc "Ilmoituksen otsikon regex pattern, josta urakka ja ilmoitusid tunnistetaan" :const true :private true}
otsikko-pattern #".*\#\[(\d+)/(\d+)\].*")

(def ^{:doc "Kuittaustyypit, joita sähköpostilla voi ilmoittaa" :const true :private true}
kuittaustyypit [["Vastaanotettu" :vastaanotettu]
                ["Aloitettu" :aloitettu]
                ["Toimenpiteet aloitettu" :toimenpiteet-aloitettu]
                ["Lopetettu" :lopetettu]
                ["Lopetettu toimenpitein" :lopetettu]
                ["Muutettu" :muutettu]
                ["Vastattu" :vastattu]
                ["Väärä urakka" :vaara-urakka]])

(def ^{:doc "Kuittaustyypin tunnistava regex pattern" :const true :private true}
kuittaustyyppi-pattern #"\[(Vastaanotettu|Aloitettu|Toimenpiteet aloitettu|Lopetettu|Lopetettu toimenpitein|Muutettu|Vastattu|Väärä urakka)\]")

(def ^{:doc "Viesti, joka lähetetään vastaanottajalle kun saadaan sisään sähköposti, jota ei tunnisteta" :private true}
+virheellinen-toimenpide-viesti+
  {:otsikko "Virheellinen kuittausviesti"
   :sisalto "Jos lähetit toimenpidekuittauksen tai muun määrämuotoisen viestin, tarkista viestin muoto ja lähetä se uudelleen. HARJA ei osannut käsitellä lähettämäsi sähköpostiviestin sisältöä."})

(def ^{:doc "Viesti, joka lähetetään jos päivystäjätietoja tai ilmoitustietoja ei voida päätellä" :private true}
+ilmoitustoimenpiteen-tallennus-epaonnistui+
  {:otsikko "Kuittausta ei voitu käsitellä"
   :sisalto "Varmista, että vastaat samalla sähköpostiosoitteella, johon ilmoitustiedot toimitettiin."})

(def ^{:doc "Viesti, joka lähetetään jos päivystäjätietoja tai ilmoitustietoja ei voida päätellä" :private true}
+toimenpiteen-aloituksen-tallennus-epaonnistui+
  {:otsikko "Kuittausta ei voitu käsitellä"
   :sisalto "Varmista, että vastaat samalla sähköpostiosoitteella, johon ilmoitustiedot toimitettiin. Toimenpiteiden aloitusta ei tallennettu."})

(def ^{:doc "Viesti, joka lähetetään onnistuneen ilmoitustoimenpiteen tallennuksen jälkeen." :private true}
+onnistunut-viesti+
  {:otsikko nil                                             ;; tämä täydennetään ilmoituksen otsikolla
   :sisalto "Kuittaus käsiteltiin onnistuneesti. Kiitos!"})

(def ^{:doc "Template, jolla muodostetaan URL Google static map kuvalle" :private true :const true}
goole-static-map-url-template
  "http://maps.googleapis.com/maps/api/staticmap?zoom=15&markers=color:red|%s,%s&size=400x300&key=%s")

(def ^{:doc "Template, jolla muodostetaan URL jonka avulla käyttäjä itse voi avata sijainnin Google Mapsissä" :private true :const true}
  open-google-map-url-template
  "https://maps.google.com/?q=%s,%s")

(defn- otsikko
  "Luo sähköpostin otsikon. Otsikkorivin tulee olla täsmälleen tiettyä muotoa, koska
   sitä käytetään sisäisesti viestiketjujen yhdistämiseen."
  [{:keys [ilmoitus-id urakka-id ilmoitustyyppi] :as ilmoitus}]
  (str "#[" urakka-id "/" ilmoitus-id "] "
       (apurit/ilmoitustyypin-nimi (keyword ilmoitustyyppi))
       (when (ilm/virka-apupyynto? ilmoitus) " (VIRKA-APUPYYNTÖ)")))

(def +vastausohje+ (str "Läheta tämä viesti kuitataksesi. Älä muuta otsikkoa tai hakasuluissa "
                        "olevaa kuittaustyyppiä. Voit kirjoittaa myös kommentin viestin alkuun."))

(defn- html-mailto-nappi
  "Luo HTML-fragmentin mailto: napin sähköpostia varten. Tämä täytyy tyylitellä inline, koska ei voida
resursseja liitää sähköpostiin mukaan luotettavasti."
  [vastausosoite napin-teksti subject body]
  (html-tyokalut/nappilinkki napin-teksti
                             (str "mailto:" vastausosoite "?subject=" subject "&body=" body)))

(defn- viesti [vastausosoite otsikko ilmoitus]
  (html
    [:div
     [:table
      (html-tyokalut/tietoja
        [["Urakka" (:urakkanimi ilmoitus)]
         ["Tunniste" (:tunniste ilmoitus)]
         ["Ilmoitettu" (pvm/pvm-aika (konversio/java-date (:ilmoitettu ilmoitus)))]
         ["Lähetetty HARJAan" (pvm/pvm-aika (konversio/java-date (:ilmoitettu ilmoitus)))]
         ;;TODO: ["Tiedotettu urakkaan" (:valitetty-urakkaan ilmoitus)]
         ["Yhteydenottopyyntö" (fmt/totuus (:yhteydenottopyynto ilmoitus))]
         ["Otsikko" (:otsikko ilmoitus)]
         ["Tierekisteriosoite" (tierekisteri/tierekisteriosoite-tekstina (:sijainti ilmoitus) {:teksti-tie? false})]
         ["Paikan kuvaus" (:paikankuvaus ilmoitus)]
         ["Selitteet" (apurit/parsi-selitteet (mapv keyword (:selitteet ilmoitus)))]
         ["Ilmoittaja" (apurit/nayta-henkilon-yhteystiedot (:ilmoittaja ilmoitus))]
         ["Lähettäjä" (apurit/nayta-henkilon-yhteystiedot (:lahettaja ilmoitus))]])]
     [:blockquote (sanitoi (:lisatieto ilmoitus))]
     (when-let [sijainti (:sijainti ilmoitus)]
       (let [[lat lon] (geo/euref->wgs84 [(:x sijainti) (:y sijainti)])]
         [:a {:href (format open-google-map-url-template lat lon)
              :target "_blank"
              :rel "noopener noreferrer"} "Avaa sijainti kartalla"]))
     (for [teksti (map first kuittaustyypit)]
       [:div {:style "padding-top: 10px;"}
        (html-mailto-nappi vastausosoite teksti otsikko (str "[" teksti "] " +vastausohje+))])]))

(defn otsikko-ja-viesti [vastausosoite ilmoitus]
  (let [otsikko (otsikko ilmoitus)
        viesti (viesti vastausosoite otsikko ilmoitus)]
    [otsikko viesti]))

(defn viestin-kuittaustyyppi [sisalto]
  (when-let [nimi (some->> sisalto
                           (re-find kuittaustyyppi-pattern)
                           second)]
    (second (first (filter #(= (first %) nimi) kuittaustyypit)))))

(defn viesti-ilman-kuittaustyyppia-ja-ohjetta [sisalto]
  (-> sisalto
      (str/replace kuittaustyyppi-pattern "")
      (str/replace +vastausohje+ "")))

(defn lue-kuittausviesti
  "Lukee annetun kuittausviestin otsikosta ja sisällöstä kuittauksen tiedot mäpiksi"
  [otsikko sisalto]
      (println "SISÄLTÖ LUE KUITTAUSVIESTI " sisalto)
  (let [[_ urakka-id ilmoitus-id] (re-matches otsikko-pattern otsikko)
        kuittaustyyppi (viestin-kuittaustyyppi sisalto)
        kommentti (str/trim (viesti-ilman-kuittaustyyppia-ja-ohjetta sisalto))
        aiheutti-toimenpiteita (.contains sisalto "Lopetettu toimenpitein")
        virheviesti (str "Viestistä ei löytynyt kuittauksen tietoja."
                      (when-not urakka-id " Urakka-id puuttuu. \n" )
                      (when-not ilmoitus-id " Ilmoitus-id puuttuu.\n" )
                      (when-not kuittaustyyppi " Kuittaustyyppi puuttuu.\n" ))]
    (if (and urakka-id ilmoitus-id kuittaustyyppi)
      {:urakka-id (Long/parseLong urakka-id)
       :ilmoitus-id (Long/parseLong ilmoitus-id)
       :kuittaustyyppi kuittaustyyppi
       :kommentti (when-not (str/blank? kommentti)
                    kommentti)
       :aiheutti-toimenpiteita aiheutti-toimenpiteita}
      {:virhe virheviesti})))

(def ^{:doc "Vastaanotetun kuittauksen mäppäys kuittaustyyppi tietokantaenumiksi" :private true}
kuittaustyyppi->enum {:vastaanotettu "vastaanotto"
                      :aloitettu "aloitus"
                      :lopetettu "lopetus"
                      :muutettu "muutos"
                      :vastattu "vastaus"
                      :vaara-urakka "vaara-urakka"})

(defn- tallenna-toimenpiteiden-aloitus [jms-lahettaja db lahettaja {:keys [urakka-id
                                                                           ilmoitus-id]}]
  (let [{ilmoitus :id
         ilmoitustyyppi :ilmoitustyyppi} (first (ilmoitukset/hae-ilmoitus-ilmoitus-idlla db ilmoitus-id))
        paivystaja (first (yhteyshenkilot/hae-urakan-paivystaja-sahkopostilla db urakka-id lahettaja))]
    (if (and paivystaja ilmoitus)
      (do
        (ilmoitukset/tallenna-ilmoitusten-toimenpiteiden-aloitukset! db [ilmoitus])
        (assoc +onnistunut-viesti+
          :otsikko (otsikko {:ilmoitus-id ilmoitus-id
                             :urakka-id urakka-id
                             :ilmoitustyyppi ilmoitustyyppi})))
      +toimenpiteen-aloituksen-tallennus-epaonnistui+)))

(defn- tallenna-ilmoitustoimenpide [jms-lahettaja db lahettaja {:keys [urakka-id
                                                                       ilmoitus-id
                                                                       kuittaustyyppi
                                                                       kommentti
                                                                       aiheutti-toimenpiteita]}]
  (let [paivystaja (first (yhteyshenkilot/hae-urakan-paivystaja-sahkopostilla db urakka-id lahettaja))
        {ilmoitus :id
         urakka :urakka
         ilmoitustyyppi :ilmoitustyyppi :as ilmoituksen-tiedot} (first (ilmoitukset/hae-ilmoitus-ilmoitus-idlla db ilmoitus-id))
        _ (when-not (and paivystaja ilmoitus)
            (log/error "Ilmoitustoimenpidettä ei voitu tallentaa! Päivystäjän tai ilmoituksen tiedot ovat väärin. Lähettäjä " (pr-str lahettaja) "urakka-id" (pr-str urakka-id) )
            (log/error "Yritettiin lähettää päivystäjälle:" (pr-str paivystaja))
            (log/error "Ilmoituksen tiedot:" (pr-str ilmoituksen-tiedot)))]
    (if-not (and paivystaja ilmoitus)
      +ilmoitustoimenpiteen-tallennus-epaonnistui+
      (let [tallenna (fn [kuittaustyyppi vapaateksti]
                       (ilmoitustoimenpiteet/tallenna-ilmoitustoimenpide
                         db
                         ilmoitus
                         ilmoitus-id
                         vapaateksti
                         kuittaustyyppi
                         paivystaja
                         "sisaan"
                         "sahkoposti"))]
        (when (= kuittaustyyppi :lopetettu)
          (ilmoitukset/ilmoitus-aiheutti-toimenpiteita! db aiheutti-toimenpiteita ilmoitus))
        (when (and (= kuittaustyyppi :aloitettu) (not (ilmoitukset/ilmoitukselle-olemassa-vastaanottokuittaus? db ilmoitus-id)))
          (let [aloitus-kuittaus-id (tallenna "vastaanotto" "Vastaanotettu")]
            (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja db aloitus-kuittaus-id)))

        (let [ilmoitustoimenpide-id (tallenna (kuittaustyyppi->enum kuittaustyyppi) kommentti)]
          (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja db ilmoitustoimenpide-id))
        (assoc +onnistunut-viesti+
          :otsikko (otsikko {:ilmoitus-id ilmoitus-id
                             :urakka-id urakka
                             :ilmoitustyyppi ilmoitustyyppi}))))))

(defn- lokita-sahkopostikuittauksen-virhe [kuittaus {:keys [sisalto] :as viesti}]
  "Annetaan errori kolmessa tilanteessa:
   Urakka-id puuttuu
   Ilmoitus-id puuttuu
   Urakka-id on ja ilmoitus-id on, mutta sisältö on täysin tyhjä.

  Varoitus annetaan, jos urakka-id löytyy ja ilmoitus-id löytyy ja sisältö löytyy, mutta se ei sisällä kuittaustyyppiä."
  (cond
    (or
      (str/includes? (:virhe kuittaus) "Urakka-id puuttuu")
      (str/includes? (:virhe kuittaus) "Ilmoitus-id puuttuu"))
    (log/error (format "VIRHE! Vastaanotettiin T-LOIK kuittaus sähköpostilla. Viesti: %s. Virheviesti: %s " viesti kuittaus))

    (or (nil? sisalto) (empty? sisalto))
    (log/error (format "VIRHE! Vastaanotettiin T-LOIK kuittaus sähköpostilla. Viesti: %s. Virheviesti: %s " viesti kuittaus))

    ;; Muuten riittää pelkkä varoitus, että ei sotketa logia turhilla erroreilla
    :else
    (log/warn (format "Varoitus: Vastaanotettiin T-LOIK kuittaus sähköpostilla. Viesti: %s. Virheviesti: %s " viesti kuittaus))))

(defn vastaanota-sahkopostikuittaus
  "Käsittelee sisään tulevan sähköpostikuittauksen ja palauttaa takaisin viestin, joka lähetetään
kuittauksen lähettäjälle."
  [jms-lahettaja db {:keys [lahettaja otsikko sisalto] :as viesti}]
  (log/debug (format "Vastaanotettiin T-LOIK kuittaus sähköpostilla. Viesti: %s." viesti))
  (let [v (lue-kuittausviesti otsikko sisalto)]
    (if (:ilmoitus-id v)
      (if (= (:kuittaustyyppi v) :toimenpiteet-aloitettu)
        (tallenna-toimenpiteiden-aloitus jms-lahettaja db lahettaja v)
        (tallenna-ilmoitustoimenpide jms-lahettaja db lahettaja v))
      (do
        ;; Logitetaan virhe, jos urakka-id tai ilmoitus-id puuttuu
        (lokita-sahkopostikuittauksen-virhe v viesti)

        ;; Palautetaan kutsujalle virheviesti ja tarkennus virheestä
        (assoc +virheellinen-toimenpide-viesti+
          :sisalto (str (:sisalto +virheellinen-toimenpide-viesti+) " Virhe: " (:virhe v)))))))
