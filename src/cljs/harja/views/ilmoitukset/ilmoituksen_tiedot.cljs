(ns harja.views.ilmoitukset.ilmoituksen-tiedot
  (:require [harja.ui.yleiset :as yleiset]
            [harja.pvm :as pvm]
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
            [harja.domain.palautejarjestelma-domain :as palautejarjestelma]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.tiedot.ilmoitukset.viestit :as v]
            [harja.loki :refer [log]]
            [reagent.core :refer [atom] :as r]))

(defn selitelista [{:keys [selitteet] :as ilmoitus}]
  (let [virka-apu? (ilmoitukset/virka-apupyynto? ilmoitus)]
    [:div.selitelista.inline-block
     (when virka-apu?
       [:div.selite-virkaapu
        [ikonit/livicon-warning-sign] "Virka-apupyyntö"])
     (parsi-selitteet (filter #(not= % :virkaApupyynto) selitteet))]))

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
   "Aiheutti toimenpiteitä " (if (:aiheutti-toimenpiteita ilmoitus) "Kyllä" "Ei")])

(defn aiheen-sisaltavat-yleiset-tiedot [ilmoitus aiheet-ja-tarkenteet]
  (println "jere testaa::" palautejarjestelma/hae-aihe aiheet-ja-tarkenteet (:aihe ilmoitus))
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
   "Aihe " (palautejarjestelma/hae-aihe aiheet-ja-tarkenteet (:aihe ilmoitus))
   "Tarkenne " (palautejarjestelma/hae-tarkenne aiheet-ja-tarkenteet (:tarkenne ilmoitus))
   "Otsikko " (:otsikko ilmoitus)
   "Kuvaus " (when (:lisatieto ilmoitus) (:lisatieto ilmoitus))
   "Aiheutti toimenpiteitä " (if (:aiheutti-toimenpiteita ilmoitus) "Kyllä" "Ei")
   (when (:toimenpiteet-aloitettu ilmoitus) "Toimenpiteet aloitettu ")
   (when (:toimenpiteet-aloitettu ilmoitus) (pvm/pvm-aika-sek (:toimenpiteet-aloitettu ilmoitus)))])

(defn ilmoitus [_e! _ilmoitus _aiheet-ja-tarkenteet]
  (let [nayta-valitykset? (atom false)]
    (fn [e! ilmoitus aiheet-ja-tarkenteet]
      [:div
       [bs/panel {}
        (ilmoitustyypin-nimi (:ilmoitustyyppi ilmoitus))
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
              "Toimenpiteet aloitettu"]))]]
       [:div.kuittaukset
        [:h3 "Kuittaukset"]
        [:div
         ;; Tilannekuvanäkymässä ei voi tehdä kuittauksia, mutta tätä komponenttia käytetään
         ;; näyttämään ilmoituksen tarkempia tietoja. Tällöin e! on nil
         (when e!
           (if-let [uusi-kuittaus (:uusi-kuittaus ilmoitus)]
             [kuittaukset/uusi-kuittaus e! uusi-kuittaus]
             (when (oikeudet/voi-kirjoittaa? oikeudet/ilmoitukset-ilmoitukset
                                             (:id @nav/valittu-urakka))

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
            (for [kuittaus (cond->> (sort-by :kuitattu pvm/jalkeen? (:kuittaukset ilmoitus))
                                    (not @nayta-valitykset?) (remove ilmoitukset/valitysviesti?))]
              (kuittaukset/kuittauksen-tiedot kuittaus))])]]])))