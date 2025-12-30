(ns harja.views.urakka.tiemerkinta-kustannukset.yhteiset
  "Tiemerkintöjen kustannukset yhteiset funktiot/komponentit"
  (:require [harja.ui.napit :as napit]
            [harja.ui.valinnat :as valinnat]
            [harja.views.urakka.valinnat :as urakka-valinnat]))

(defonce yhteenveto-tyypit {:korjaus "Tiemerkintöjen korjaus"
                            :paikkausten-merkinnat "Paikkauskohteiden tiemerkintäkustannukset"
                            :paallysteiden-merkinnat "Päällystyskohteiden tiemerkintäkustannukset"
                            :sakko "Sakot"
                            :bonus "Bonukset"
                            :arvonmuutokset "Arvonmuutokset"
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


(defn paivittava-urakkavuosi-suodatin
  "Urakkavuosi valinta, passataan näkymän tuck hakufunktio"
  [{:keys [aikavali]} haku-fn haku-kaynnissa? anna-valita-kaikki?]
  (urakka-valinnat/paivittava-urakkavuosi-tuck aikavali haku-fn haku-kaynnissa? anna-valita-kaikki?))


(defn nakyma-body
  "Muut kustannukset, Sakot ja bonukset 
   Välilehdet samalla leiskalle, joten yhteinen komponentti"
  [otsikko lisaa-uusi-fn aikavali
   {:keys [raportti] :as _valinnat}
   muokataan muokkauspaneeli grid haku-kaynnissa? laji-suodatin yhteenveto?]
  ;; Body 
  [:div.tiemerkinta-kustannusten-kirjaus

   ;; Otsikko / header, raporttiviennit
   [:div.header
    [:h1 otsikko]
    (valinnat/raporttiviennit raportti haku-kaynnissa?)]

   ;; Suodattimet
   [:div.suodattimet

    ;; Urakkavuosi 
    aikavali

    ;; Laji
    [:div.laji (when laji-suodatin "Laji")
     [:div.kentta (when laji-suodatin laji-suodatin)]]

    ;; Lisää uusi 
    (when-not yhteenveto?
      [:div.lisaa-uusi
       [napit/uusi "Lisää uusi" lisaa-uusi-fn {:disabled false}]])]

   ;; Muokkauspaneeli
   (when muokataan muokkauspaneeli)

   ;; Grid
   [:div.muut-kustannukset-listaus grid]])
