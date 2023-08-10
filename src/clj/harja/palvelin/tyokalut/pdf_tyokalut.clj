(ns harja.palvelin.tyokalut.pdf-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.pdf :as pdf-raportointi]
            [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]))

(defmethod pdf-raportointi/muodosta-pdf :tyomaa-laskutusyhteenveto-yhteensa [[_ kyseessa-kk-vali? hoitokausi laskutettu laskutetaan laskutettu-str laskutetaan-str]]
  ;; Muodostaa työmaakokouksen laskutusyhteenvedolle "Laskutus yhteensä" -yhteenvedon 
  ;; Näihin tulee Hoitokauden & Valitun kuukauden otsikot joiden alle arvot annettujen parametrien perusteella
  [:fo:block {:margin-top "10px"}
   (pdf-raportointi/arvotaulukko-valittu-aika
    kyseessa-kk-vali?
    (str "Laskutus yhteensä " hoitokausi)
    (str laskutettu-str)
    (str laskutetaan-str)
    (str (fmt/euro laskutettu))
    (str (fmt/euro laskutetaan)))])

(defn liikenneyhteenveto-arvo-str [arvot tyyppi avain]
  (str (avain (get arvot tyyppi))))

(defmethod pdf-raportointi/muodosta-pdf :liikenneyhteenveto [[_ yhteenveto]]
  [:fo:table {:font-size pdf-raportointi/otsikon-fonttikoko}
   [:fo:table-column {:column-width "8%"}]
   [:fo:table-column {:column-width "18%"}]
   [:fo:table-column {:column-width "18%"}]
   [:fo:table-column {:column-width "18%"}]
   [:fo:table-column {:column-width "18%"}]
   [:fo:table-column {:column-width "18%"}]

   (let [saraketyyli-yla {:margin-left "8mm" :margin-top "30px" :font-weight "bold"}
         sivusarakkeet-yla {:margin-left "14mm" :margin-top "30px" :font-weight "bold"}
         saraketyyli-ala {:margin-left "8mm" :margin-top "6px" :font-weight "bold"}
         sivusarakkeet-ala {:margin-left "14mm" :margin-top "6px" :font-weight "bold"}]

     [:fo:table-body
      [:fo:table-row
       [:fo:table-cell [:fo:block {:margin-top "30px"} "Toimenpiteet"]]

       [:fo:table-cell [:fo:block sivusarakkeet-yla "Sulutukset ylös: " (liikenneyhteenveto-arvo-str yhteenveto :toimenpiteet :sulutukset-ylos)]]
       [:fo:table-cell [:fo:block saraketyyli-yla "Sulutukset alas: " (liikenneyhteenveto-arvo-str yhteenveto :toimenpiteet :sulutukset-alas)]]
       [:fo:table-cell [:fo:block saraketyyli-yla "Sillan avaukset: " (liikenneyhteenveto-arvo-str yhteenveto :toimenpiteet :sillan-avaukset)]]
       [:fo:table-cell [:fo:block saraketyyli-yla "Tyhjennykset: " (liikenneyhteenveto-arvo-str yhteenveto :toimenpiteet :tyhjennykset)]]
       [:fo:table-cell [:fo:block sivusarakkeet-yla "Yhteensä: " (liikenneyhteenveto-arvo-str yhteenveto :toimenpiteet :yhteensa)]]]

      [:fo:table-row
       [:fo:table-cell [:fo:block {:margin-top "6px"} "Palvelumuoto"]]

       [:fo:table-cell [:fo:block sivusarakkeet-ala "Paikallispalvelu: " (liikenneyhteenveto-arvo-str yhteenveto :palvelumuoto :paikallispalvelu)]]
       [:fo:table-cell [:fo:block saraketyyli-ala "Kaukopalvelu: " (liikenneyhteenveto-arvo-str yhteenveto :palvelumuoto :kaukopalvelu)]]
       [:fo:table-cell [:fo:block saraketyyli-ala "Itsepalvelu: " (liikenneyhteenveto-arvo-str yhteenveto :palvelumuoto :itsepalvelu)]]
       [:fo:table-cell [:fo:block saraketyyli-ala "Muu: " (liikenneyhteenveto-arvo-str yhteenveto :palvelumuoto :muu)]]
       [:fo:table-cell [:fo:block sivusarakkeet-ala "Yhteensä: " (liikenneyhteenveto-arvo-str yhteenveto :palvelumuoto :yhteensa)]]]])])

(defmethod pdf-raportointi/muodosta-pdf :gridit-vastakkain [[_
                                                             {:keys [otsikko-vasen optiot-vasen otsikot-vasen rivit-vasen]}
                                                             {:keys [otsikko-oikea optiot-oikea otsikot-oikea rivit-oikea]}]]
  ;; Tekee 2 PDF taulukkoa vierekkän mikäli molempien taulukkojen data olemassa
  (if otsikko-oikea
    [:fo:table {:font-size "9pt" :margin-bottom "12px"}
     [:fo:table-column {:column-width "52%"}]
     [:fo:table-column {:column-width "48%"}]

     [:fo:table-body
      [:fo:table-row
       [:fo:table-cell
        ;; Tehdään pieni väli keskelle, jonka takia 52% & 48%
        ;; Näyttää hieman siistimmältä, ehkä
        [:fo:block {:margin-right "5px"}
         (pdf-raportointi/taulukko
           otsikko-vasen
           false
           otsikot-vasen
           rivit-vasen
           optiot-vasen)]]

       [:fo:table-cell
        [:fo:block
         (pdf-raportointi/taulukko
           otsikko-oikea
           false
           otsikot-oikea
           rivit-oikea
           optiot-oikea)]]]]]

    ;; Pelkästään vasemman taulukon data olemassa, eli tehdään vain 1 taulukko
    (pdf-raportointi/taulukko
      otsikko-vasen
      false
      otsikot-vasen
      rivit-vasen
      optiot-vasen)))

(defmethod pdf-raportointi/muodosta-pdf :tyomaapaivakirja-header [[_ tyomaapaivakirja]]
  ;; Urakka nimi & työmaapäiväkirja title on vakiona jo PDF raportissa, joten nämä voi skippaa
  ;; Näytetään vaan saapunut / päivitetty / versio / kommentit / tila
  [:fo:table {:font-size pdf-raportointi/otsikon-fonttikoko}
   [:fo:table-column {:column-width "25%"}]
   [:fo:table-column {:column-width "25%"}]
   [:fo:table-column {:column-width "14%"}]
   [:fo:table-column {:column-width "2%"}]
   [:fo:table-column {:column-width "14%"}]

   (let [tyyli-default {:margin-bottom "20px" :margin-top "5px" :color "#000000" :font-size "8pt"} ;; #858585 harmaa sävy
         tila-tyyli tyyli-default
         saapunut-tyyli (merge tyyli-default {:font-size "7pt"})
         versio-tyyli (merge tyyli-default {:margin-left "5mm"})
         paivitetty-tyyli (merge tyyli-default {:margin-left "5mm" :font-size "7pt"})
         pallura-tyyli (merge tyyli-default {:margin-top "2.5px" :font-size "12pt" :font-weight "bold"})
         pallura-tyyli (merge pallura-tyyli (if (= "myohassa" (:tila tyomaapaivakirja))
                                              {:color "#FFC300"}
                                              {:color "#27B427"}))]
     [:fo:table-body
      [:fo:table-row
       [:fo:table-cell [:fo:block saapunut-tyyli (str "Saapunut: " (pvm/pvm-aika-klo (:luotu tyomaapaivakirja)))]]

       [:fo:table-cell [:fo:block paivitetty-tyyli (str "Päivitetty " (or 
                                                                        "-"
                                                                        (pvm/pvm-aika-klo (:muokattu tyomaapaivakirja))))]]
       [:fo:table-cell [:fo:block versio-tyyli (str "Versio " (:versio tyomaapaivakirja))]]
       [:fo:table-cell [:fo:block pallura-tyyli "• "]]
       [:fo:table-cell [:fo:block tila-tyyli (if (= "myohassa" (:tila tyomaapaivakirja))
                                               "Myöhässä"
                                               "Ok")]]]])])

(defmethod pdf-raportointi/muodosta-pdf :tyomaapaivakirjan-kommentit [[_ kommentit]]
  [:fo:block {:margin-top "30px"} "Kommentit"
   (let [taulukon-otsikot (rivi
                            {:otsikko "Aika" :leveys 0.2 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :tyyppi :varillinen-teksti}
                            {:otsikko "Nimi" :leveys 0.2 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti}
                            {:otsikko "Kommentti" :leveys 0.75 :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :tyyppi :varillinen-teksti})
         taulukon-optiot {:tyhja "Ei Tietoja."
                          :piilota-border? false
                          :viimeinen-rivi-yhteenveto? false}
         kommentit-rivit (for [{:keys [etunimi sukunimi luotu kommentti]} kommentit]
                           (into [] [[:varillinen-teksti {:arvo (str (pvm/pvm-aika luotu))}]
                                     [:varillinen-teksti {:arvo (str etunimi " " sukunimi)}]
                                     [:varillinen-teksti {:arvo kommentti}]]))
         kommentit-rivit (vec kommentit-rivit)]

     (pdf-raportointi/taulukko
       ""
       false
       taulukon-otsikot
       kommentit-rivit
       taulukon-optiot))])

(defn- olomuoto? [olom arvo]
  (boolean (= olom arvo)))

(defn generoi-saa-ikoni-pdf [olom]
  ;; Koska meillä ei ole ikoneita PDFään, palautetaan vaan lyhyt string joka kuvaa sää olomuotoa
  ;; Olomuodoksi annetaan NWS / WMO koodi pöydästä SYNOP koodi
  ;; https://issues.solita.fi/browse/VHAR-7868
  (cond
    ;; Selkeää
    (olomuoto? olom 0)
    "Selkeä"

    (or
      ;; Sumu
      (olomuoto? olom 10)
      ;; Sumua tai savua tai pölyä ilmassa, näkyvyys alle 1 km
      (olomuoto? olom 5)
      ;; Sumua tai savua tai pölyä ilmassa, näkyvyys yhtä suuri tai suurempi kuin 1 km
      (olomuoto? olom 4))
    "Sumu/Savu/Pöly"

    ;; Koodilukuja 20-25 käytetään, jos sadetta tai sumua on havaittu edellisen tunnin aikana, mutta ei havainnointihetkellä
    ;; _____________________________________________________________________________________________________________________

    (or
      ;; Sumu
      (olomuoto? olom 20)
      ;; Sumu
      (olomuoto? olom 30)
      ;; Sumua tai jääsumua, paikoin
      (olomuoto? olom 31)
      ;; Sumu tai jääsumu on ohentunut viimeisen tunnin aikana
      (olomuoto? olom 32)
      ;; Sumua tai jääsumua, ei merkittävää muutosta viimeisen tunnin aikana
      (olomuoto? olom 33)

      ;; Sumua tai jääsumua, on alkanut tai tihentynyt viimeisen tunnin aikana
      (olomuoto? olom 34))
    "Sumu"

    ;; Vesi tihkua
    (or
      (olomuoto? olom 21)
      (olomuoto? olom 40)
      ;; Tihkusadetta (ei jäätyvää) tai lunta
      (olomuoto? olom 22)
      ;; Tihkusadetta 
      (olomuoto? olom 23))
    "Tihkusade"

    ;; Lumisade
    (olomuoto? olom 24)
    "Lumisade"

    (or
      ;; Sadetta, lievää tai kohtalaista
      (olomuoto? olom 41)
      ;; Sadetta, rankkaa
      (olomuoto? olom 42)
      ;; Tihkusade
      (olomuoto? olom 50)
      ;; Tihkusadetta, ei jäätävää, vähäistä
      (olomuoto? olom 51)
      ;; Tihkusadetta, ei jäätävää, kohtalaista
      (olomuoto? olom 52)
      ;; Tihkusadetta, ei jäätävää, raskas
      (olomuoto? olom 53)

      ;; Sadetta 
      (olomuoto? olom 60)
      ;; Sadetta, vähäistä
      (olomuoto? olom 61)
      ;; Sadetta, rankkaa
      (olomuoto? olom 63)

      ;; Sadekuuroja tai ajoittaisia sateita
      (olomuoto? olom 80)
      ;; Sadekuuroja, vähäistä
      (olomuoto? olom 81)
      ;; Sadekuuroja, kohtalaista
      (olomuoto? olom 82)
      ;; Sadekuuroja, raskas
      (olomuoto? olom 83))
    "Sade"

    (or
      ;; Tihkusadetta, jäätävää, vähäistä
      (olomuoto? olom 54)
      ;; Tihkusadetta, jäätävää, kohtalaista
      (olomuoto? olom 55)
      ;; Tihkusadetta, jäätävää, raskas
      (olomuoto? olom 56)

      ;; Sadetta, jäätävää, vähäistä
      (olomuoto? olom 64)
      ;; Sadetta, jäätävää, kohtalaista
      (olomuoto? olom 65)
      ;; Sadetta, jäätävää, raskas
      (olomuoto? olom 66)

      ;; Jäätävää sadetta tai jäätävää tihkusadetta
      (olomuoto? olom 25)

      ;; Sadetta (tai tihkusadetta) ja lunta, kevyttä
      (olomuoto? olom 67)
      ; Sadetta (tai tihkusadetta) ja lunta, kohtalaista tai raskasta
      (olomuoto? olom 68))
    "Jäätävä sade"

    ;; Lumisade
    (or
      ;; Lumisade
      (olomuoto? olom 70)
      ;; Lumisade, vähäinen
      (olomuoto? olom 71)
      ;; Lumisade, kohtalainen
      (olomuoto? olom 72)
      ;; Lumisade, raskas
      (olomuoto? olom 73)

      ;; Lumisade, vähäinen
      (olomuoto? olom 85)
      ;; Lumisade, kohtalainen
      (olomuoto? olom 86)
      ;; Lumisade, raskas
      (olomuoto? olom 87))
    "Lumisade"

    (or
      ;; Rakeita, vähäinen
      (olomuoto? olom 74)
      ;; Rakeita, kohtalainen
      (olomuoto? olom 75)
      ;; Rakeita, raskas
      (olomuoto? olom 76))
    "Rakeita"

    ;; Erittäin rajuja sadekuuroja (> 32mm/h)
    (olomuoto? olom 84)
    "Raju sade (!)"

    ;; Fallback
    :else "Ei tietoja"))

(defmethod pdf-raportointi/muodosta-pdf :saa-ikoni [[_ {:keys [olomuoto _ _]}]]
  ;; Generoidaan sään olomuoto tekstinä pdfään
  (generoi-saa-ikoni-pdf olomuoto))
