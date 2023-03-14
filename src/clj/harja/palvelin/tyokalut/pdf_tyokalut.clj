(ns harja.palvelin.tyokalut.pdf-tyokalut
  ;; Tänne voi laittaa mm yksittäisten raporttien funktioita

  (:require [harja.fmt :as fmt]
            [harja.palvelin.raportointi.pdf :as pdf-raportointi]))

(defmethod pdf-raportointi/muodosta-pdf :tyomaa-laskutusyhteenveto-yhteensa [[_ hoitokausi laskutettu laskutetaan laskutettu-str laskutetaan-str]]
  ;; Muodostaa työmaakokouksen laskutusyhteenvedolle "Laskutus yhteensä" -yhteenvedon 
  ;; Näihin tulee Hoitokauden & Valitun kuukauden otsikot joiden alle arvot annettujen parametrien perusteella

  (pdf-raportointi/arvotaulukko-valittu-aika
   (str "Laskutus yhteensä " hoitokausi)
   (str laskutettu-str)
   (str laskutetaan-str)
   (str (fmt/euro laskutettu))
   (str (fmt/euro laskutetaan))))

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
