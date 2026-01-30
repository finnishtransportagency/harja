(ns harja.views.urakka.muutos-nakyma
  "MHU-urakoiden muutosten välilehti. 
  Hallinnoi ja näyttää tarjouksen pohjatietoihin ja tavoitehintaan tehtäviä muutoksia."
  (:require
    [tuck.core :as tuck]
    [harja.tiedot.urakka :as u]
    [harja.ui.yleiset :as yleiset]
    [harja.ui.komponentti :as komp]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.views.urakka.valinnat :as urakka-valinnat]
    [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]

    ;; Vanhat urakat 
    [harja.views.urakka.muutokset.vanhat-urakat.suunniteltujen-maarien-muutokset :as suunnitellut]
    [harja.views.urakka.muutokset.vanhat-urakat.tavoitehinnan-muutokset :as tavoitehinta]

    ;; Osiot / lomake
    [harja.views.urakka.muutokset.vaikutukset :as vaikutukset]
    [harja.views.urakka.muutokset.kirjatut-muutokset :as kirjatut-muutokset]
    [harja.views.urakka.muutokset.lasketut-muutokset :as lasketut-muutokset]
    [harja.views.urakka.muutokset.rahavarausten-muutokset :as rahavarausten-muutokset]
    [harja.views.urakka.muutokset.lomake.muutoslomake :as muutoslomake]))


(defn muutoslistaus [e! {:keys [nakyma-uusi? nakyma-vanha?] :as app}]
  [:div.muutoslistaus
   (when (:valittu-hoitokausi app)
     (cond
       nakyma-vanha?
       [:span.muutostiedot
        [tavoitehinta/tavoitehinnan-muutokset e! app]
        [suunnitellut/suunniteltujen-maarien-muutokset e! app]]

       nakyma-uusi?
       [:span.uudet-muutostiedot
        [kirjatut-muutokset/kirjatut-muutokset e! app]
        [lasketut-muutokset/lasketut-muutokset e! app]
        [rahavarausten-muutokset/rahavarausten-muutokset e! app]]))])


(defn muutosten-hallinta-sisalto [e! {:keys [haku-kaynnissa? nakyma-uusi? nakyma-vanha?] :as app}]
  [:div.valinnat-ja-listaus
   [:h1 "Muutosten hallinta"]
   [:div.otsikko-ja-hoitokausi

    [urakka-valinnat/paivittava-urakkavuosi-tuck
     @u/valittu-aikavali
     #(e! (t-yhteiset/->AlustaNakyma)) haku-kaynnissa? false]]

   (cond
     nakyma-uusi?
     [:<>
      [vaikutukset/muutosten-vaikutukset-uusi e! app]
      [muutoslistaus e! app]]

     nakyma-vanha?
     [:<>
      [yleiset/varoitus-vihje
       (str
         "Muutosten luokittelu on muuttunut hoitovuodesta 2025-26 alkaen. "
         "Tämä hoitovuosi noudattaa vanhaa kirjaustapaa ja luokittelua.") nil :alert]
      [vaikutukset/muutosten-vaikutukset-vanha e! app]
      [muutoslistaus e! app]]

     :else
     [yleiset/varoitus-vihje
      "Muutokset ovat käytössä hoitovuodesta 2023 alkaen." nil :alert])])


(defn muutokset-alempi-valilehti*
  [e! _app]
  (komp/luo
    (komp/lippu t-yhteiset/nakymassa?)
    (komp/sisaan #(e! (t-yhteiset/->AlustaNakyma)))
    (fn [e!
         {:keys [muokattava-muutos] :as app}]
      [:span.muutokset-sivu
       (if muokattava-muutos
         ;; Jos valittuna rivi, näytä lomake 
         [muutoslomake/muutoslomake e! app]
         ;; Muuten näytä sivun sisältö 
         [muutosten-hallinta-sisalto e! app])])))


(defn muutokset-paatason-valilehti [_ur]
  (fn [_ur] [tuck/tuck tila/muutokset muutokset-alempi-valilehti*]))
