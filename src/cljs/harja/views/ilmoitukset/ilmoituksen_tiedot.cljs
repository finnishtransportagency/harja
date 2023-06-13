(ns harja.views.ilmoitukset.ilmoituksen-tiedot
  (:require [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.ui.bootstrap :as bs]
            [clojure.string :refer [capitalize]]
            [harja.tiedot.ilmoitukset.tieliikenneilmoitukset :as tiedot]
            [harja.domain.tieliikenneilmoitukset
             :refer [+ilmoitustyypit+ ilmoitustyypin-nimi ilmoitustyypin-lyhenne-ja-nimi
                     +ilmoitustilat+ nayta-henkilo parsi-puhelinnumero
                     +ilmoitusten-selitteet+ parsi-selitteet kuittaustyypit
                     kuittaustyypin-selite]
             :as ilmoitukset]
            [harja.views.ilmoituskuittaukset :as kuittaukset]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.palautevayla-domain :as palautevayla]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [harja.loki :refer [log]]
            [reagent.core :refer [atom] :as r])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn selitelista [{:keys [selitteet] :as ilmoitus}]
  (let [virka-apu? (ilmoitukset/virka-apupyynto? ilmoitus)]
    [:div.selitelista.inline-block
     (when virka-apu?
       [:div.selite-virkaapu
        [ikonit/livicon-warning-sign] "Virka-apupyyntö"])
     (parsi-selitteet (filter #(not= % :virkaApupyynto) selitteet))]))

(defn- kuvalinkit [ilmoitus]
  (when-not (empty? (:kuvat ilmoitus))
    (for* [[n linkki] (map-indexed #(vector %1 %2) (:kuvat ilmoitus))]
      [:div
       [:a {:href linkki} (str "Kuvalinkki " (inc n))]
       [:br]])))

(defn- parsi-kuva [kuva]
  (mapv (fn [tiedot]
          (let [parsittu (str/replace tiedot "[" "")
                parsittu (str/replace parsittu "]" "")]
            parsittu)) (str/split kuva ",")))

(defn selitteen-sisaltavat-yleiset-tiedot [ilmoitus]
  [yleiset/tietoja {:piirra-viivat? true
                    :class "body-text"
                    :tietorivi-luokka "padding-8 css-grid css-grid-columns-12rem-9"}
   "Urakka " (:urakkanimi ilmoitus)
   "Id " (:ilmoitusid ilmoitus)
   "Tunniste " (:tunniste ilmoitus)
   "Ilmoitettu " (pvm/pvm-aika-sek (:ilmoitettu ilmoitus))
   "Tiedotettu HARJAan " (pvm/pvm-aika-sek (:valitetty ilmoitus))
   "Tiedotettu urakkaan " (pvm/pvm-aika-sek (:valitetty-urakkaan ilmoitus))
   "Yhteydenottopyyntö " (if (:yhteydenottopyynto ilmoitus) "Kyllä" "Ei")
   "Sijainti " (tr-domain/tierekisteriosoite-tekstina (:tr ilmoitus))
   "Otsikko " (:otsikko ilmoitus)
   "Paikan kuvaus " (:paikankuvaus ilmoitus)
   "Lisatieto " (when (:lisatieto ilmoitus) (:lisatieto ilmoitus))
   "Selitteet " [selitelista ilmoitus]
   "Toimenpiteet aloitettu " (when (:toimenpiteet-aloitettu ilmoitus) (pvm/pvm-aika-sek (:toimenpiteet-aloitettu ilmoitus)))
   "Kuvalinkit " (kuvalinkit ilmoitus)
   "Aiheutti toimenpiteitä " (if (:aiheutti-toimenpiteita ilmoitus) "Kyllä" "Ei")])

(defn aiheen-sisaltavat-yleiset-tiedot [ilmoitus aiheet-ja-tarkenteet]
  [yleiset/tietoja {:piirra-viivat? true
                    :class "body-text"
                    :tietorivi-luokka "padding-8 css-grid css-grid-columns-12rem-9"}
   "Urakka " (:urakkanimi ilmoitus)
   "Id " (:ilmoitusid ilmoitus)
   "Tunniste " (:tunniste ilmoitus)
   "Ilmoitettu " (pvm/pvm-aika-sek (:ilmoitettu ilmoitus))
   "Tiedotettu HARJAan " (pvm/pvm-aika-sek (:valitetty ilmoitus))
   "Tiedotettu urakkaan " (pvm/pvm-aika-sek (:valitetty-urakkaan ilmoitus))
   "Yhteydenottopyyntö " (if (:yhteydenottopyynto ilmoitus) "Kyllä" "Ei")
   "Sijainti " (tr-domain/tierekisteriosoite-tekstina (:tr ilmoitus))
   "Paikan kuvaus " (:paikankuvaus ilmoitus)
   "Aihe " (palautevayla/hae-aihe aiheet-ja-tarkenteet (:aihe ilmoitus))
   "Tarkenne " (palautevayla/hae-tarkenne aiheet-ja-tarkenteet (:tarkenne ilmoitus))
   "Otsikko " (:otsikko ilmoitus)
   "Kuvaus " (when (:lisatieto ilmoitus) (:lisatieto ilmoitus))
   "Kuvalinkit " (kuvalinkit ilmoitus)
   "Aiheutti toimenpiteitä " (if (:aiheutti-toimenpiteita ilmoitus) "Kyllä" "Ei")
   (when (:toimenpiteet-aloitettu ilmoitus) "Toimenpiteet aloitettu ")
   (when (:toimenpiteet-aloitettu ilmoitus) (pvm/pvm-aika-sek (:toimenpiteet-aloitettu ilmoitus)))])

(defn ilmoitus [_e! _ilmoitus _aiheet-ja-tarkenteet]
  (let [nayta-valitykset? (atom false)]
    (fn [e! ilmoitus aiheet-ja-tarkenteet]
      [:div
       ;; Ilmoitustietojen otsikko
       [:div.panel-heading
        [:h2.musta (ilmoitustyypin-nimi (:ilmoitustyyppi ilmoitus))]]

       [:div.ilmoitustiedot-flex
        [:span.ilmoituksen-tiedot
         ;; Ei anneta panelille tässä otsikkoa parametrina jotta flexit tulee samalle tasolle 
         ;; Alemmalla panelilla ei ole otsikkoa
         [bs/panel {}
          [:span
           (if-not (:aihe ilmoitus)
             [selitteen-sisaltavat-yleiset-tiedot ilmoitus]
             [aiheen-sisaltavat-yleiset-tiedot ilmoitus aiheet-ja-tarkenteet])
           [:br]
           [yleiset/tietoja {:piirra-viivat? true
                             :class "body-text"
                             :tietorivi-luokka "padding-8 css-grid css-grid-columns-12rem-9"}
            "Ilmoittaja " (let [henkilo (nayta-henkilo (:ilmoittaja ilmoitus))
                                tyyppi (capitalize (name (get-in ilmoitus [:ilmoittaja :tyyppi])))]
                            (if (and henkilo tyyppi)
                              (str henkilo ", " tyyppi)
                              (str (or henkilo tyyppi))))
            "Puhelinnumero " (parsi-puhelinnumero (:ilmoittaja ilmoitus))
            "Sähköposti " (get-in ilmoitus [:ilmoittaja :sahkoposti])]

           [:br]
           [yleiset/tietoja {:piirra-viivat? true
                             :class "body-text"
                             :tietorivi-luokka "padding-8 css-grid css-grid-columns-12rem-9"}
            "Lähettäjä " (nayta-henkilo (:lahettaja ilmoitus))
            "Puhelinnumero " (parsi-puhelinnumero (:lahettaja ilmoitus))
            "Sähköposti " (get-in ilmoitus [:lahettaja :sahkoposti])]

           [:br]
           (when (and
                   (:ilmoitusid ilmoitus)
                   (oikeudet/voi-kirjoittaa?
                     oikeudet/ilmoitukset-ilmoitukset
                     (:id @nav/valittu-urakka)))
             ;; todo: tämä kirjaus ei ole sallittu, jos lopetuskuittaus on jo tehty
             (if (:toimenpiteet-aloitettu ilmoitus)
               [:button.nappi-kielteinen
                {:on-click #(e! (v/->PeruutaToimenpiteidenAloitus (:id ilmoitus)))}
                "Peruuta toimenpiteiden aloitus"]
               [:button.nappi-ensisijainen
                {:on-click #(e! (v/->TallennaToimenpiteidenAloitus (:id ilmoitus)))}
                "Toimenpiteet aloitettu"]))]]]

        ;; Ilmoitukseen liitetyt kuvat panel
        [bs/panel {}
         [:div
          [:span
           (let [kuvaliite-teksti (when (empty? (:kuvat ilmoitus)) "Ei liitteitä")]
             [yleiset/tietoja {:piirra-viivat? true
                               :class "body-text"
                               :tietorivi-luokka "padding-8 css-grid css-grid-colums-12rem-9"}
              "Kuvaliitteet" ""
              "" kuvaliite-teksti])]

          (when-not (empty? (:kuvat ilmoitus))
            (map
              (fn [kuva]
                (let [kuva (parsi-kuva kuva) ;; Parsi kuvan tiedot [linkki, id]
                      validi? (= (count kuva) 2) ;; Tietojen pitäisi sisältää linkki ja id
                      id (if validi? (second kuva) nil)
                      linkki (if validi? (first kuva) nil)]
                  ^{:key id}
                  [:div
                   [:br]
                   [:a
                    {:href linkki}
                    [:img {:src linkki :style {:width "100%" :max-width "450px"}}]]])) (:kuvat ilmoitus)))]]]

       [:div.kuittaukset
        [:h3 "Kuittaukset"]
        [:div
         ;; Tilannekuvanäkymässä ei voi tehdä kuittauksia, mutta tätä komponenttia käytetään
         ;; näyttämään ilmoituksen tarkempia tietoja. Tällöin e! on nil
         (when e!
           (if-let [uusi-kuittaus (:uusi-kuittaus ilmoitus)]
             [kuittaukset/uusi-kuittaus e! uusi-kuittaus]
             (when (oikeudet/voi-kirjoittaa? oikeudet/ilmoitukset-ilmoitukset (:id @nav/valittu-urakka))

               (if (:ilmoitusid ilmoitus)
                 [:button.nappi-ensisijainen
                  {:class "uusi-kuittaus-nappi"
                   :on-click #(e! (v/->AvaaUusiKuittaus))}
                  (ikonit/livicon-plus) " Uusi kuittaus"]
                 [yleiset/vihje tiedot/vihje-liito]))))

         [:div.margin-vertical-16
          [harja.ui.kentat/tee-kentta {:tyyppi :checkbox
                                       :teksti "Näytä välitysviestit"
                                       :nayta-rivina? true} nayta-valitykset?]]

         (when-not (empty? (:kuittaukset ilmoitus))
           [:div
            (for [kuittaus (cond->>
                             (sort-by :kuitattu pvm/jalkeen? (:kuittaukset ilmoitus))
                             (not @nayta-valitykset?) (remove ilmoitukset/valitysviesti?))]
              (kuittaukset/kuittauksen-tiedot kuittaus))])]]])))
