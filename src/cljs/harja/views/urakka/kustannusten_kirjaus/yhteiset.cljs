(ns harja.views.urakka.kustannusten-kirjaus.yhteiset
  "Tiemerkintöjen kustannusten kirjaus apufunktiot"
  (:require
   [harja.ui.napit :as napit]
   [harja.transit :as transit]
   [harja.ui.ikonit :as ikonit]
   [harja.asiakas.kommunikaatio :as komm]))


(defn nollaa-tuck-tila
  "Nollaa Tuck-tilan osittain säilyttäen olemassa olevat syvemmän tason arvot.
   Korvaa arvot, jotka on määritelty `nollatut-valinnat`
   Käytetään kun suodattimia päivitetään, urakkaa vaihdetaan, yms, jotta tilaan ei jää mitään roikkumaan."
  [app nollatut-valinnat]
  (merge-with (fn [app valinta]
                (if (and
                      (map? app) (map? valinta))
                  (merge app valinta)
                  valinta))
    app
    nollatut-valinnat))


(defn raporttiviennit [{:keys [raportti] :as valinnat}]
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


(defn nakyma-body
  "Muut kustannukset, Sakot ja bonukset 
   Välilehdet samalla leiskalle, joten yhteinen komponentti"
  [otsikko lisaa-uusi-fn aikavali
   valinnat muokataan muokkauspaneeli grid laji-suodatin]
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
    [:div
     [napit/uusi "Lisää uusi" lisaa-uusi-fn {:disabled false}]]]

   ;; Muokkauspaneeli
   (when muokataan muokkauspaneeli)

   ;; Grid
   [:div.muut-kustannukset-listaus grid]])
