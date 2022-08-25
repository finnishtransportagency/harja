(ns harja.views.hallinta.toteumatyokalu-nakyma
  "Työkalu toteumien lisäämiseksi testiurakoille."
  (:require [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :refer [<! >! chan close! timeout]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tiedot.hallinta.yhteiset :as yhteiset]
            [harja.tiedot.hallinta.toteumatyokalu-tiedot :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.ui.listings :refer [suodatettu-lista]]
            [harja.loki :refer [log]]
            [harja.ui.grid :as grid]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallintayksikot :as hal]
            [clojure.string :as str]
            [harja.ui.yleiset :as y])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn toteumalomake [e! {:keys [toteumatiedot] :as app}]
  (let [disable-tallenna? (if (or (nil? (:lahetysaika toteumatiedot))
                                (nil? (:valittu-urakka toteumatiedot))
                                (nil? (:valittu-materiaali toteumatiedot))
                                (nil? (:koordinaatit app)))
                            true
                            false)
        disable-trhaku? (if (or (nil? (:numero (:tierekisteriosoite toteumatiedot)))
                                (nil? (:alkuosa (:tierekisteriosoite toteumatiedot)))
                              (nil? (:alkuetaisyys (:tierekisteriosoite toteumatiedot)))
                              (nil? (:loppuosa (:tierekisteriosoite toteumatiedot)))
                              (nil? (:loppuetaisyys (:tierekisteriosoite toteumatiedot)))
                              )
                            true
                            false)]
    [:div.yhteydenpito
     [:h3 "Reittitoteuman simulointi valitulle urakalle"]
     [lomake/lomake
      {:ei-borderia? true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :footer-fn (fn [toteumatiedot]
                    [:div
                     [:p "Koska tämä viritelmä on kesken, niin tr-osoitteen koordinaatteja ei saada, ennenkuin ne haetaan serveriltä"]
                     [:p (str (:koordinaatit app))]
                     [napit/tallenna "Hae TR osoitteelle koordinaatit"
                      #(e! (tiedot/->HaeTROsoitteelleKoordinaatit toteumatiedot))
                      {:disabled disable-trhaku? :paksu? true}]
                     [napit/tallenna "Lähetä"
                      #(e! (tiedot/->Laheta toteumatiedot))
                      {:disabled disable-tallenna? :paksu? true}]])
       :muokkaa! #(e! (tiedot/->Muokkaa %))}
      [{:nimi :valittu-hallintayksikko
        :otsikko "Valitse hallintayksikko"
        :tyyppi :valinta
        :valinnat @hal/vaylamuodon-hallintayksikot
        :valinta-nayta :nimi
        :pakollinen? true}
       {:id #_ (hash tiedot/+mahdolliset-urakat+)
        (hash (:mahdolliset-urakat app))
        :nimi :valittu-urakka
        :otsikko "Valitse urakka"
        :tyyppi :valinta
        :valinnat (:mahdolliset-urakat app)                 ;tiedot/+mahdolliset-urakat+
        :valinta-nayta :nimi
        :pakollinen? true}
       {:nimi :valittu-jarjestelma
        :otsikko "Järjestelma"
        :tyyppi :string
        :pituus-max 40
        :pakollinen? true}
       {:nimi :suorittaja-nimi
        :otsikko "Suorittaja"
        :tyyppi :string
        :pituus-max 40
        :pakollinen? true}
       {:nimi :lahetysaika
        :otsikko "Lähetysaika"
        :tyyppi :string
        :pituus-max 40
        :pakollinen? true}
       {:nimi :ulkoinen-id
        :otsikko "Ulkoinen id"
        :tyyppi :numero
        :pituus-max 40
        :pakollinen? true}
       {:nimi :sopimusid
        :otsikko "Sopimusid"
        :tyyppi :numero
        :pituus-max 40
        :pakollinen? true
        :tarkkaile-ulkopuolisia-muutoksia? true}
       {:nimi :valittu-materiaali
        :otsikko "Valitse materiaali"
        :tyyppi :valinta
        :valinnat tiedot/+mahdolliset-materiaalit+
        :valinta-nayta :nimi
        :pakollinen? true}
       {:nimi :materiaalimaara
        :otsikko "Materiaalimäärä"
        :tyyppi :string
        :pakollinen? true}
       {:nimi :tierekisteriosoite
        :tyyppi :tierekisteriosoite
        :vayla-tyyli? true
        :lataa-piirrettaessa-koordinaatit? true}]
      toteumatiedot]]))

(defn simuloi-toteuma* []
  (komp/luo
    (komp/sisaan-ulos
      #(go (do
             (js/console.log "simuloi-toteuma* :: sisään" @tiedot/nakymassa?)
             (nav/vaihda-kartan-koko! :L)
             (kartta-tasot/taso-paalle! :tr-valitsin)
             (kartta-tasot/taso-paalle! :organisaatio)
             (reset! tiedot/nakymassa? true)))
      #(do
         (js/console.log "simuloi-toteuma* :: ulos" @tiedot/nakymassa?)
         (nav/vaihda-kartan-koko! :S)
         (kartta-tasot/taso-pois! :tr-valitsin)
         (kartta-tasot/taso-pois! :organisaatio)
         (reset! tiedot/nakymassa? false)))
    (fn [e! app]
      (let [valittu-hallintayksikko @nav/valittu-hallintayksikko
            ;_ (js/console.log "simuloi-toteuma* :: valittu-hallintayksikko: " (pr-str valittu-hallintayksikko))
            ]
        (when @tiedot/nakymassa?
          [:div
           [kartta/kartan-paikka]
           [debug/debug app]
           (toteumalomake e! app)])))))

(defn simuloi-toteuma []
  [tuck tiedot/data simuloi-toteuma*])
