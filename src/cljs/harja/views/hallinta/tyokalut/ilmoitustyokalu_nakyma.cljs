(ns harja.views.hallinta.tyokalut.ilmoitustyokalu-nakyma
  "Työkalu ilmoitusten lisäämiseksi ja debuggaamisen helpottamiseksi."
  (:require [tuck.core :refer [tuck]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.komponentti :as komp]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.tiedot.hallinta.tyokalut.ilmoitustyokalu-tiedot :as tiedot])

  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn ilmoituslomake [e! {:keys [ilmoitus] :as app}]
  [:div.yhteydenpito
   [:h3 "Ilmoituksen lähetys valitulle urakalle"]
   [:p "Hae Hallinta / Seuranta / Integraatiotilanne / Integraatiolokit -näkymästä - järjestelmä: 'tloik' integraatio: 'ilmoituksen-kirjaus'.
   Lisää hakusanaksi 'Viestin sisältö' kohtaan 'xml' ja ota sisään tullut XML-ilmoitus, kopioi sen tähän lomakkeeseen (ilman xml-tageja) ja lähetä."]

   [lomake/lomake
    {:ei-borderia? true
     :tarkkaile-ulkopuolisia-muutoksia? true
     :footer-fn (fn [toteumatiedot]
                  [:div
                   [napit/tallenna "Lähetä XML"
                    #(e! (tiedot/->Laheta toteumatiedot))
                    {:paksu? true}]])
     :muokkaa! #(e! (tiedot/->Muokkaa %))}
    [{:nimi :xml
      :otsikko "XML ilmoitus (jätä xml tagi pois siitä ekalta riviltä.)"
      :tyyppi :text
      :koko [50 20] :pituus-max 500000
      :vayla-tyyli? true}]

    ilmoitus]])

(defn simuloi-ilmoitus* []
  (komp/luo
    (komp/sisaan-ulos
      #(go (reset! tiedot/nakymassa? true))
      #(reset! tiedot/nakymassa? false))
    (fn [e! app]
      (if (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
        (when @tiedot/nakymassa?
          [:div
           (ilmoituslomake e! app)
           [debug/debug app]])
        "Puutteelliset käyttöoikeudet"))))

(defn simuloi-ilmoitus []
  [tuck tiedot/data simuloi-ilmoitus*])
