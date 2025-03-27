(ns harja.views.urakka.kustannusten-kirjaus.yhteiset
  "Tiemerkintöjen kustannusten kirjaus apufunktiot"
  (:require [tuck.core :refer [tuck]]
            [harja.asiakas.kommunikaatio :as komm]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.urakka :as tila]
            [reagent.core :as r]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :as kentat]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.transit :as transit]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr-domain]))


(defn raporttiviennit [valinnat]
  [:div.raporttiviennit
   ;;
   ;; Excel
   ;;
   ^{:key "raporttixls"}
   [:form {:style {:margin-left "auto"}
           :target "_blank" :method "POST"
           ; :action TODO
           }

    [:input {:type "hidden" :name "parametrit"
             :value (transit/clj->transit {:tr (:tr valinnat)
                                           :aikavali (:aikavali valinnat)
                                           :urakka-id @nav/valittu-urakka-id})}]

    [:button {:type "submit"
              :class #{"nappi-toissijainen"}}

     [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Tallenna Excel"]]]
   ;;
   ;; Pdf 
   ;;
   ^{:key "raporttipdf"}
   [:form {:style {:margin-left "auto"}
           :target "_blank" :method "POST"
           ; :action TODO
           }

    [:input {:type "hidden" :name "parametrit"
             :value (transit/clj->transit {:tr (:tr valinnat)
                                           :aikavali (:aikavali valinnat)
                                           :urakka-id @nav/valittu-urakka-id})}]

    [:button {:type "submit"
              :class #{"nappi-toissijainen"}}

     [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Tallenna PDF"]]]])


(defn nakyma-body
  "Muut kustannukset, Sakot ja bonukset 
  Välilehdet lähes täysin samannäköisiä, tehty molemmille yhteinen komponentti"
  [otsikko e!
   lisaa-uusi-fn
   rivit valinnat muokataan valittu-rivi
   haku-kaynnissa? kustannukset tyypit muokkauspaneeli grid laji-suodatin]

  (let [alkuaika (:alkuaika valittu-rivi)
        ;; TODO 
        voi-kirjoittaa? true
        voi-tallentaa? true]

    ;; Body 
    [:div.tiemerkinta-muut-kustannukset

     ;; Otsikko / header, raporttiviennit
     [:div.header
      [:h1.header-yhteiset otsikko]
      (raporttiviennit valinnat)]

     ;; Suodattimet
     [:div.suodattimet

      ;; Urakkavuosi 
      [:div
       [urakka-valinnat/urakan-hoitokausi @nav/valittu-urakka]]

      ;; Laji
      [:div.laji (when laji-suodatin "Laji")
       [:div.kentta (when laji-suodatin laji-suodatin)]]

      ;; Lisää uusi 
      [:div
       [napit/uusi "Lisää uusi" lisaa-uusi-fn {:disabled false}]]]

     ;; Muokkauspaneeli
     (when muokataan muokkauspaneeli)

     ;; Grid
     [:div.muut-kustannukset-listaus grid]]))
