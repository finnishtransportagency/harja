(ns harja.views.urakka.kustannusten-kirjaus.yhteiset
  "Tiemerkintöjen kustannusten kirjaus apufunktiot"
  (:require
   [harja.ui.napit :as napit]
   [harja.transit :as transit]
   [harja.ui.ikonit :as ikonit]
   [harja.tiedot.navigaatio :as nav]
   [harja.asiakas.kommunikaatio :as komm]
   [harja.tiedot.urakka :as urakka-tiedot]
   [harja.views.urakka.valinnat :as urakka-valinnat]))

(defonce yhteenveto-tyypit {:muut-kustannukset "Muut kustannukset"})

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


(defn raporttiviennit [{:keys [raportti] :as _valinnat}]
  [:div.raporttiviennit
   ;;
   ;; Excel
   ^{:key "raporttixls"}
   [:form {:target "_blank" :method "POST"
           :action (komm/excel-url :raportointi)}

    [:input {:type "hidden" :name "parametrit"
             :value (transit/clj->transit raportti)}]

    [:button {:type "submit"
              :class #{"nappi-toissijainen"}}

     [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Tallenna Excel"]]]
   
   ;;
   ;; Pdf 
   ^{:key "raporttipdf"}
   [:form {:target "_blank" :method "POST"
           :action (komm/pdf-url :raportointi)}

    [:input {:type "hidden" :name "parametrit"
             :value (transit/clj->transit raportti)}]

    [:button {:type "submit"
              :class #{"nappi-toissijainen"}}

     [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Tallenna PDF"]]]])


(defn paivittava-urakkavuosi-suodatin
  "Urakkavuosi valinta, triggeröi haun"
  [{:keys [aikavali]} haku-fn]
  (let [fn-aikavali-muuttunut? (fn [aika]
                                 (let [alku (-> @urakka-tiedot/valittu-hoitokausi first)
                                       loppu (-> @urakka-tiedot/valittu-hoitokausi second)
                                       valinnat-alku (-> aika first)
                                       valinnat-loppu (-> aika second)]
                                   (boolean (or
                                              (not= alku valinnat-alku)
                                              (not= loppu valinnat-loppu)))))]

    [:div {:on-click #(when (fn-aikavali-muuttunut? aikavali) (haku-fn))}
     [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]]))


(defn nakyma-body
  "Muut kustannukset, Sakot ja bonukset 
   Välilehdet samalla leiskalle, joten yhteinen komponentti"
  [otsikko lisaa-uusi-fn aikavali
   valinnat muokataan muokkauspaneeli grid laji-suodatin yhteenveto?]
  ;; Body 
  [:div.tiemerkinta-muut-kustannukset

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
