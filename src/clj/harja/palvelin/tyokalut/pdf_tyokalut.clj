(ns harja.palvelin.tyokalut.pdf-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita
  (:require [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.raportointi.pdf :as pdf-raportointi]))

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
       [:fo:table-cell [:fo:block saapunut-tyyli (str "Saapunut:" (pvm/pvm-aika-klo (:luotu tyomaapaivakirja)))]]

       [:fo:table-cell [:fo:block paivitetty-tyyli (str "Päivitetty " (pvm/pvm-aika-klo (:muokattu tyomaapaivakirja)))]]
       [:fo:table-cell [:fo:block versio-tyyli (str "Versio " (:versio tyomaapaivakirja))]]
       [:fo:table-cell [:fo:block pallura-tyyli "• "]]
       [:fo:table-cell [:fo:block tila-tyyli (if (= "myohassa" (:tila tyomaapaivakirja))
                                               "Myöhässä"
                                               "Ok")]]]])])

(defmethod pdf-raportointi/muodosta-pdf :tyomaapaivakirjan-kommentit [[_ kommentit]]
  ;; TODO ...
  ;;
  [:fo:block "Kommentit"])
