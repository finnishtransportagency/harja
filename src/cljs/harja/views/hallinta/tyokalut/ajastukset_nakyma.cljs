(ns harja.views.hallinta.tyokalut.ajastukset-nakyma
  "Työkalu kaikkien yöllisten ja muiden ajastusten käynnistämiseen käsin."
  (:require [harja.ui.debug :as debug]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.napit :as napit]
            [harja.tiedot.hallinta.tyokalut.ajastukset-tiedot :as ajastukset-tiedot]))

(defn ajastukset-listattuna [e! app]
  [:div
   [:div.row
    [:div.col-md-6
     [:h3 "Suunniteltujen kustannusten siirto toteumiksi"]]]

   [:div.row
    [:div.col-md-6 "Harjassa on kolmenlaisia suunniteltuja (eli budjetoituja) kustannuksia, joista ei tehdä erillistä kulua. Reilusti suurin osa
   suunnitelluista summista (budjetoidut summat) tehdään kuluna harjaan. Näitä ovat mm. Hankinnat, Rahavaraukset.
   Johto- ja hallintokorvaukset, Erillishankinnat ja Hoidonjohtopalkkiot on sellaisia suunniteltuja kuluja (budjetoituja kuluja), joista ei koskaan tehdä kuluja.
   Ne lasketaan kuluksi automaattisesti. Ne löytyy siis maksueriltä, kustannusten seurannasta, välikatselmuksesta yms.
   Jotta ne voidaan löytää noissa näkymissä, ne täytyy siirtää kustannusarvoitu_tyo ja johto_ja_hallintokorvaus tauluista toteutuneet_kustannukset -tauluun.
   Tuo siirto tapahtuu automaattiesti joka kuukausi 10. päivä. Jos haluat siirtää nuo kustannukset heti, voit käynnistää tämän ajon."]]
   [:div.row {:style {:margin-top "1rem"}}
    [:div.col-md-6
     (if (:kustannusarvioidut-tyot-toteumiksi-kaynnissa app)
       [:div "Ajo käynnissä"]
       [:div [napit/tallenna "Siirrä suunnitellut kustannukset toteumiksi"
              #(e! (ajastukset-tiedot/->AjaKustannusarvioidutTyotToteumiksi))]])]]

   [:div.row
    [:div.col-md-6
     [:h3 "Maksuerien alustus hoidon urakoille"]]]
   [:div.row
    [:div.col-md-6
     "Hoidon urakoille luodaan maksuerät Sampo-integraation yhteydessä. Joskus integraatio ei ole käynnissä (esim. lokaalisti testatessa)
     tai integraatiossa on ollut ongelmia, jolloin maksueriä ei ole luotu. Tällöin voit käynnistää tämän ajon, joka luo maksuerät hoidon urakoille."]]
   [:div.row {:style {:margin-top "1rem"}}
    [:div.col-md-6
     (if (:maksuerat-kaynnissa app)
       [:div "Ajo käynnissä"]
       [:div [napit/tallenna "Muodosta maksuerät"
              #(e! (ajastukset-tiedot/->AjaKasitteleUrakatMaksueriksi))]])]]])

(defn nayta-ajastukset* [e! app]
  (if
    (oikeudet/voi-kirjoittaa? oikeudet/hallinta-toteumatyokalu)
    [:div
     [debug/debug app]
     [:h2 "Aja käsin erilaisia yöllisiä ja muita ajastuksia."]
     [ajastukset-listattuna e! app]]
    [:div "Ei oikeuksia"]))

(defn nayta-ajastukset []
  [tuck ajastukset-tiedot/tila nayta-ajastukset*])
