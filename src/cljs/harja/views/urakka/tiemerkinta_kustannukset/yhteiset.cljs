(ns harja.views.urakka.tiemerkinta-kustannukset.yhteiset
  "Tiemerkintöjen kustannukset yhteiset funktiot/komponentit"
  (:require [harja.transit :as transit]
            
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.views.urakka.valinnat :as urakka-valinnat]))

(defonce yhteenveto-tyypit {:korjaus "Tiemerkintöjen korjaus"
                            :muut-kustannukset "Muut kustannukset"})

(defonce laji-valinnat {:kaikki "Kaikki"
                        :yllapidon_sakko "Sakko"
                        :yllapidon_bonus "Bonus"})

(defonce tyyppi-valinnat {:lisatyo "Lisätyö"
                          :muu "Muu kustannus"
                          :muutostyo "Muutostyö"
                          :arvonmuutos "Arvonmuutos"
                          :indeksi "Indeksitarkistus"
                          :sopimusalueen-muutos "Sopimusalueen muutos"})

(defonce lomake-validointi-virhe-viesti "Tallennus epäonnistui. Pakollisia tietoja puuttuu.")


(defn raporttiviennit 
  "Raportteja voi Harjassa tehdä kolmella eri tapaa 
   Tässä yksi, passataan frontista suoraa raporttimoottorille parametrit
   Tämä on kyseisiin näkymiin ratkaisuna toimiva.
   Wrapperin voisi heittää johonkin yleiseen kirjastoon, on käytössä muuallakin Harjassa sama koodi"
  [{:keys [raportti] :as _valinnat}]
  [:div.raporttiviennit
   ;;
   ;; Excel raportti
   ^{:key "raporttixls"}
   [:form {:target "_blank" :method "POST"
           :action (komm/excel-url :raportointi)}

    [:input {:type "hidden" :name "parametrit"
             :value (transit/clj->transit raportti)}]

    [:button {:type "submit"
              :class #{"nappi-toissijainen"}}

     [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Tallenna Excel"]]]
   
   ;;
   ;; Pdf raportti
   ^{:key "raporttipdf"}
   [:form {:target "_blank" :method "POST"
           :action (komm/pdf-url :raportointi)}

    [:input {:type "hidden" :name "parametrit"
             :value (transit/clj->transit raportti)}]

    [:button {:type "submit"
              :class #{"nappi-toissijainen"}}

     [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Tallenna PDF"]]]])


(defn paivittava-urakkavuosi-suodatin
  "Urakkavuosi valinta, passataan näkymän tuck hakufunktio"
  [{:keys [aikavali]} haku-fn haku-kaynnissa?]
  (urakka-valinnat/paivittava-urakkavuosi-tuck aikavali haku-fn haku-kaynnissa?))


(defn nakyma-body
  "Muut kustannukset, Sakot ja bonukset 
   Välilehdet samalla leiskalle, joten yhteinen komponentti"
  [otsikko lisaa-uusi-fn aikavali
   valinnat muokataan muokkauspaneeli grid laji-suodatin yhteenveto?]
  ;; Body 
  [:div.tiemerkinta-kustannusten-kirjaus

   ;; Otsikko / header, raporttiviennit
   [:div.header
    [:h1.header-yhteiset otsikko]
    (raporttiviennit valinnat)]

   ;; Suodattimet
   [:div.suodattimet

    ;; Urakkavuosi 
    aikavali

    ;; Laji
    [:div.laji (when laji-suodatin "Laji")
     [:div.kentta (when laji-suodatin laji-suodatin)]]

    ;; Lisää uusi 
    (when-not yhteenveto?
      [:div
       [napit/uusi "Lisää uusi" lisaa-uusi-fn {:disabled false}]])]

   ;; Muokkauspaneeli
   (when muokataan muokkauspaneeli)

   ;; Grid
   [:div.muut-kustannukset-listaus grid]])
