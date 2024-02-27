(ns harja.views.urakka.yllapitokohteet.reikapaikkaukset
  "Reikäpaikkaukset päänäkymä"
  ;; TODO.. lisätty valmiiksi requireja, poista myöhemmin turhat 
  (:require [tuck.core :refer [tuck]]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-reikapaikkaukset :as tiedot]
            [harja.ui.debug :refer [debug]]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [harja.views.kartta :as kartta])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))


(defn reikapaikkaus-listaus [e! {:keys [valinnat rivit muokataan] :as app}]
  (let [tr-atomi (atom (:tr valinnat))
        sijainti-atomi (atom (:sijainti valinnat))]
    
    ;; Wrappaa reikapaikkausluokkaan niin ei yliajeta mitään 
    [:div.reikapaikkaukset

     ;; Muokkauspaneeli
     (when muokataan
       [:div.overlay-oikealla
        ;; Footer
        [:div
         [:hr]
         [:div.muokkaus-modal-napit
          ;; Tallenna
          [napit/tallenna "Tallenna muutokset" #(println "tallenna") {:disabled false :paksu? true}] ;; TODO 
          ;; Poista 
          [napit/poista "Poista" #(println "poista")] ;; TODO 
          ;; Sulje 
          [napit/yleinen-toissijainen "Sulje" #(e! (tiedot/->SuljeMuokkaus)) {:paksu? true}]]]])

     [:div.reikapaikkaus-listaus
      ;; Suodattimet
      [:div.row.filtterit
       ;; TR valinta
       [:div
        [:div.alasvedon-otsikko-vayla "Tieosoite"]
        [kentat/tee-kentta {:tyyppi :tierekisteriosoite
                            :alaotsikot? true
                            :sijainti sijainti-atomi
                            :vayla-tyyli? true} tr-atomi]]
       ;; Pvm valinta
       [:div
        [valinnat/aikavali
         tiedot/aikavali-atom
         {:otsikko "Päivämäärä"
          :for-teksti "filtteri-aikavali"
          :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta reikapaikkaus-pvm "}
          :ikoni-sisaan? true
          :vayla-tyyli? true
          :aikavalin-rajoitus [6 :kuukausi]}]]]

      [:div.reikapaikkaukset-kartta
       [kartta/kartan-paikka]]

      ;; Taulukon ylhäällä olevat tekstit
      [:div.taulukko-header.header-yhteiset
       [:h3 "1 800 riviä, 1000.0 EUR"]
       ;; Oikealla puolella olevat lataus / tuontinapit
       [:div.flex-oikealla
        [:div.lataus-nappi.klikattava {:on-click #(do  (println "Klikattu tuo tiedot"))}
         [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Tuo tiedot Excelistä"]]

        [:div.lataus-nappi.klikattava {:on-click #(do  (println "Klikattu lataa"))}
         [ikonit/ikoni-ja-teksti (ikonit/livicon-download) "Lataa Excel-pohja"]]]]

      ;; Grid
      [grid/grid {:tyhja "Valitulle aikavälille ei löytynyt mitään."
                  :tunniste :id ;; TODO korjaa tämä, ehkä 
                  :sivuta grid/vakiosivutus
                  :voi-kumota? false
                  :piilota-toiminnot? true
                  :jarjesta :pvm
                  :mahdollista-rivin-valinta? true
                  :rivi-klikattu #(e! (tiedot/->AvaaMuokkausModal))}

       [{:otsikko "Pvm"
         :tyyppi :komponentti
         :komponentti (fn [arvo _]  "test")
         :luokka "text-nowrap"
         :leveys 0.4}

        {:otsikko "Sijainti"
         :tyyppi :komponentti
         :komponentti (fn [arvo _] "test")
         :luokka "text-nowrap"
         :leveys 0.5}

        {:otsikko "Menetelmä"
         :tyyppi :komponentti
         :komponentti (fn [arvo _] "test")
         :luokka "text-nowrap"
         :leveys 1}

        {:otsikko "Määrä"
         :tyyppi :komponentti
         :komponentti (fn [arvo _] "test")
         :luokka "text-nowrap"
         :leveys 0.3}

        {:otsikko "Kustannus (EUR)"
         :tyyppi :komponentti
         :komponentti (fn [arvo _] "test")
         :luokka "text-nowrap"
         :leveys 0.3}]
       rivit]]]))


(defn reikapaikkaukset* [e! _app]
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-reikapaikkaukset)
    (komp/sisaan-ulos 
      ;; Sisään
      #(do
         ;; Aikaväli tarkkailu
         (add-watch tiedot/aikavali-atom :aikavali-haku
           (fn [_ _ vanha uusi]
             (when-not
               (and
                 (pvm/sama-pvm? (first vanha) (first uusi))
                 (pvm/sama-pvm? (second vanha) (second uusi)))
               (e! (tiedot/->PaivitaAikavali {:aikavali uusi})))))
         ;; Kartta
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)
         ;; Hae tiedot 
         (e! (tiedot/->HaeTiedot)))
      ;; Ulos
      #(do
         ;; do stuff 
         ;; (remove-watch tiedot/aikavali-atom :aikavali-haku)
         ))

    ;; Näytä listaus
    (fn [e! app]
      [:div
       [reikapaikkaus-listaus e! app]])))


(defn reikapaikkaukset []
  [tuck tiedot/tila reikapaikkaukset*])
